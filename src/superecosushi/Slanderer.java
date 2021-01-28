package superecosushi;

import battlecode.common.*;
import static superecosushi.Constants.*;

public class Slanderer extends Unit {
    static MapLocation targetLoc = null;
    static final int LATTICE_SIZE = 2;
    public static final int[][] BFS20 = { { 0, 0 }, { 1, 0 }, { 0, -1 }, { -1, 0 }, { 0, 1 }, { 2, 0 }, { 1, -1 },
            { 0, -2 }, { -1, -1 }, { -2, 0 }, { -1, 1 }, { 0, 2 }, { 1, 1 }, { 3, 0 }, { 2, -1 }, { 1, -2 }, { 0, -3 },
            { -1, -2 }, { -2, -1 }, { -3, 0 }, { -2, 1 }, { -1, 2 }, { 0, 3 }, { 1, 2 }, { 2, 1 }, { 4, 0 }, { 3, -1 },
            { 2, -2 }, { 1, -3 }, { 0, -4 }, { -1, -3 }, { -2, -2 }, { -3, -1 }, { -4, 0 }, { -3, 1 }, { -2, 2 },
            { -1, 3 }, { 0, 4 }, { 1, 3 }, { 2, 2 }, { 3, 1 }, { 4, -1 }, { 3, -2 }, { 2, -3 }, { 1, -4 }, { -1, -4 },
            { -2, -3 }, { -3, -2 }, { -4, -1 }, { -4, 1 }, { -3, 2 }, { -2, 3 }, { -1, 4 }, { 1, 4 }, { 2, 3 },
            { 3, 2 }, { 4, 1 }, { 4, -2 }, { 3, -3 }, { 2, -4 }, { -2, -4 }, { -3, -3 }, { -4, -2 }, { -4, 2 },
            { -3, 3 }, { -2, 4 }, { 2, 4 }, { 3, 3 }, { 4, 2 } };

    // location used to rally slanderrers into a lattice
    static MapLocation originPoint = null;

    public static void setup() throws GameActionException {
        setHomeEC();
    }

    public static void run() throws GameActionException {
        setFlagThisTurn = false;
        rc.setFlag(Comms.IMASLANDERERR);
        if (rc.canGetFlag(homeECID)) {
            int homeECFlag = rc.getFlag(homeECID);
            switch (Comms.SIGNAL_TYPE_MASK & homeECFlag) {
                case Comms.MAP_OFFSET_X_AND_WIDTH:
                    int[] vs = Comms.readMapOffsetSignalXWidth(homeECFlag);
                    offsetx = vs[0];
                    mapWidth = vs[1];
                    break;
                case Comms.MAP_OFFSET_Y_AND_HEIGHT:
                    int[] vs2 = Comms.readMapOffsetSignalYHeight(homeECFlag);
                    offsety = vs2[0];
                    mapHeight = vs2[1];
                    break;
            }
        }

        RobotInfo[] nearbyBots = rc.senseNearbyRobots();

        MapLocation locOfClosestEnemyMuckraker = null;
        int distToClosestEnemyMuckraker = 999999999;
        MapLocation locOfClosestFriendEC = null;
        int distToClosestFriendEC = 99999999;
        for (int i = nearbyBots.length; --i >= 0;) {
            RobotInfo bot = nearbyBots[i];
            if (bot.team == myTeam) {
                switch (bot.type) {
                    case ENLIGHTENMENT_CENTER:
                        int dist = rc.getLocation().distanceSquaredTo(bot.location);
                        if (dist < distToClosestFriendEC) {
                            distToClosestFriendEC = dist;
                            locOfClosestFriendEC = bot.location;
                        }
                        break;
                    case POLITICIAN: {
                        int flag = rc.getFlag(bot.ID);
                        switch (Comms.SIGNAL_TYPE_MASK & flag) {
                            case Comms.TARGETED_MUCK: {
                                int[] data = Comms.readTargetedMuckSignal(flag);
                                int dx = data[1];
                                int dy = data[1];
                                MapLocation muckloc = new MapLocation(bot.location.x + dx, bot.location.y + dy);
                                int distToSpottedMuck = rc.getLocation().distanceSquaredTo(muckloc);
                                if (distToSpottedMuck < distToClosestEnemyMuckraker) {
                                    distToClosestEnemyMuckraker = distToSpottedMuck;
                                    locOfClosestEnemyMuckraker = muckloc;
                                }
                                break;
                            }
                            case Comms.SPOTTED_MUCK: {
                                int[] data = Comms.readSpottedMuckSignal(flag, rc);
                                MapLocation muckloc = Comms.decodeMapLocation(data[0], rc);
                                int distToSpottedMuck = rc.getLocation().distanceSquaredTo(muckloc);
                                if (distToSpottedMuck < distToClosestEnemyMuckraker) {
                                    distToClosestEnemyMuckraker = distToSpottedMuck;
                                    locOfClosestEnemyMuckraker = muckloc;
                                }
                                break;
                            }
                            case Comms.SLAND_SPOTTED_MUCK: {

                                // this is a slanderer as well
                                MapLocation muckloc = Comms.readSlandererSpottedMuckSignal(flag, rc);
                                int distToSpottedMuck = rc.getLocation().distanceSquaredTo(muckloc);
                                int theirDist = bot.location.distanceSquaredTo(muckloc);
                                // strengthen the signal if on lower passability

                                double passability = rc.sensePassability(rc.getLocation());
                                int movedelay = (int) (1 / passability);
                                // we want to give ourselves more space depending on movedelay
                                // don't bother running away if muck is more than 8 + movedelay squares away
                                if (theirDist >= distToSpottedMuck || distToSpottedMuck > Math.pow(6 + movedelay, 2)) {
                                    // ignore messages from slanderers behind me
                                    break;
                                }
                                if (distToSpottedMuck < distToClosestEnemyMuckraker) {
                                    distToClosestEnemyMuckraker = distToSpottedMuck;
                                    locOfClosestEnemyMuckraker = muckloc;
                                }
                                break;
                            }
                            // TODO: add signal for just seeing a muck but not targeting
                            // TODO: propagate that signal backwards through the network of units
                        }
                        break;
                    }
                    case MUCKRAKER: {
                        int flag = rc.getFlag(bot.ID);
                        switch (Comms.SIGNAL_TYPE_MASK & flag) {
                            case Comms.SPOTTED_MUCK:
                                int[] data = Comms.readSpottedMuckSignal(flag, rc);
                                MapLocation muckloc = Comms.decodeMapLocation(data[0], rc);
                                int distToSpottedMuck = rc.getLocation().distanceSquaredTo(muckloc);
                                if (distToSpottedMuck < distToClosestEnemyMuckraker) {
                                    distToClosestEnemyMuckraker = distToSpottedMuck;
                                    locOfClosestEnemyMuckraker = muckloc;
                                }
                                break;
                        }
                        break;
                    }
                    default:
                        break;
                }
            } else if (bot.team == oppTeam) {
                switch (bot.type) {
                    case MUCKRAKER:
                        int dist = rc.getLocation().distanceSquaredTo(bot.location);
                        if (dist < distToClosestEnemyMuckraker) {
                            distToClosestEnemyMuckraker = dist;
                            locOfClosestEnemyMuckraker = bot.location;
                        }
                        break;
                    default:
                        break;
                }
            }
        }

        System.out.println("Closest enemy: " + locOfClosestEnemyMuckraker);
        targetLoc = rc.getLocation();
        if (rc.getLocation().distanceSquaredTo(homeEC) <= 4) {
            targetLoc = rc.getLocation().add(rc.getLocation().directionTo(homeEC).opposite());
        }

        // search in sensor range for close stuff
        MapLocation currLoc = rc.getLocation();
        MapLocation bestLatticeLoc = null;

        if (originPoint == null) {
            originPoint = homeEC;
        }

        int bestLatticeLocVal = Integer.MIN_VALUE;
        if (locOnLattice(currLoc) && currLoc.distanceSquaredTo(homeEC) > 4) {
            bestLatticeLoc = currLoc;
            bestLatticeLocVal = -bestLatticeLoc.distanceSquaredTo(originPoint);
        }
        for (int i = 0; ++i < BFS20.length;) {
            int[] deltas = BFS20[i];
            MapLocation checkLoc = new MapLocation(currLoc.x + deltas[0], currLoc.y + deltas[1]);
            if (locOnLattice(checkLoc)) {
                if (rc.onTheMap(checkLoc)) {
                    if (checkLoc.distanceSquaredTo(homeEC) > 4) {
                        RobotInfo bot = rc.senseRobotAtLocation(checkLoc);
                        if (bot == null || bot.ID == rc.getID()) {
                            int value = -checkLoc.distanceSquaredTo(originPoint);
                            if (value > bestLatticeLocVal) {
                                bestLatticeLocVal = value;
                                bestLatticeLoc = checkLoc;
                            }
                        }
                    }
                }
            }
        }
        if (homeEC != null) {
            if (bestLatticeLoc == null) {
                // find closest corner.
                Direction awayDir = homeEC.directionTo(rc.getLocation());
                targetLoc = rc.getLocation().add(awayDir).add(awayDir).add(awayDir);
            } else {
                targetLoc = bestLatticeLoc;
            }
        }

        if (locOfClosestEnemyMuckraker != null) {
            Direction greedyDir = Direction.CENTER;
            int bestDist = rc.getLocation().distanceSquaredTo(locOfClosestEnemyMuckraker);
            if (rc.isReady()) {
                for (Direction dir : DIRECTIONS) {
                    if (rc.canMove(dir)) {
                        MapLocation newloc = rc.getLocation().add(dir);
                        if (rc.onTheMap(newloc) && rc.senseRobotAtLocation(newloc) == null) {
                            int distToHome = newloc.distanceSquaredTo(homeEC);
                            if (distToHome > 3) {
                                int thisDist = newloc.distanceSquaredTo(locOfClosestEnemyMuckraker);
                                if (thisDist > bestDist) {
                                    greedyDir = dir;
                                    bestDist = thisDist;
                                }
                            }
                        }
                    }
                }
            } else {
                // cant move
            }

            if (greedyDir != Direction.CENTER) {
                rc.move(greedyDir);
            }

            // report loc! propagate message!
            int signal = Comms.getSlandererSpottedMuckSignal(locOfClosestEnemyMuckraker);
            setFlag(signal);

        } else {
            if (rc.isReady()) {
                Direction dir = getNextDirOnPath(targetLoc);
                if (dir != Direction.CENTER && rc.canMove(dir)) {
                    rc.move(dir);
                }
            }
        }
    }

    private static boolean locOnLattice(MapLocation loc) {
        return loc.x % LATTICE_SIZE != loc.y % LATTICE_SIZE;
    }
}
