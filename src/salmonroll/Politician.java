package salmonroll;

import battlecode.common.*;
import salmonroll.utils.LinkedList;
import salmonroll.utils.Node;

import static salmonroll.Constants.*;

public class Politician extends Unit {
    public static final int[][] BFS25 = { { 0, 0 }, { 1, 0 }, { 0, -1 }, { -1, 0 }, { 0, 1 }, { 2, 0 }, { 1, -1 },
            { 0, -2 }, { -1, -1 }, { -2, 0 }, { -1, 1 }, { 0, 2 }, { 1, 1 }, { 3, 0 }, { 2, -1 }, { 1, -2 }, { 0, -3 },
            { -1, -2 }, { -2, -1 }, { -3, 0 }, { -2, 1 }, { -1, 2 }, { 0, 3 }, { 1, 2 }, { 2, 1 }, { 4, 0 }, { 3, -1 },
            { 2, -2 }, { 1, -3 }, { 0, -4 }, { -1, -3 }, { -2, -2 }, { -3, -1 }, { -4, 0 }, { -3, 1 }, { -2, 2 },
            { -1, 3 }, { 0, 4 }, { 1, 3 }, { 2, 2 }, { 3, 1 }, { 5, 0 }, { 4, -1 }, { 3, -2 }, { 2, -3 }, { 1, -4 },
            { 0, -5 }, { -1, -4 }, { -2, -3 }, { -3, -2 }, { -4, -1 }, { -5, 0 }, { -4, 1 }, { -3, 2 }, { -2, 3 },
            { -1, 4 }, { 0, 5 }, { 1, 4 }, { 2, 3 }, { 3, 2 }, { 4, 1 }, { 4, -2 }, { 3, -3 }, { 2, -4 }, { -2, -4 },
            { -3, -3 }, { -4, -2 }, { -4, 2 }, { -3, 3 }, { -2, 4 }, { 2, 4 }, { 3, 3 }, { 4, 2 }, { 4, -3 }, { 3, -4 },
            { -3, -4 }, { -4, -3 }, { -4, 3 }, { -3, 4 }, { 3, 4 }, { 4, 3 } };
    static final int SACRIFICE = 0;
    static final int DEFEND_SLANDERER = 2;
    static final int ATTACK_EC = 3;
    static Direction exploreDir = Direction.NORTH;
    static int role = DEFEND_SLANDERER;
    static MapLocation targetLoc = null;
    /** used for attack ec role, where to attack the ec */
    static MapLocation attackLoc = null;
    static int LATTICE_SIZE = 3;
    static MapLocation closestCorner = null;

    static MapLocation targetedEnemyLoc = null;

    public static void setup() throws GameActionException {
        // find ec spawned from
        setHomeEC();
        if (homeEC == null) {
            // role = SACRIFICE;
            // this means we were captured probably
            exploreDir = randomDirection();
        } else {
            exploreDir = homeEC.directionTo(rc.getLocation());
        }
        // first turn set flag to indiciate unit type
    }

    public static void handleFlag(int flag) {
        switch (Comms.SIGNAL_TYPE_MASK & flag) {
            case Comms.MAP_OFFSET_X_AND_WIDTH:
                int[] vs = Comms.readMapOffsetSignalXWidth(flag);
                offsetx = vs[0];
                mapWidth = vs[1];
                break;
            case Comms.MAP_OFFSET_Y_AND_HEIGHT:
                int[] vs2 = Comms.readMapOffsetSignalYHeight(flag);
                offsety = vs2[0];
                mapHeight = vs2[1];
                break;
            case Comms.POLI_SACRIFICE:
                role = SACRIFICE;
                break;
            case Comms.FOUND_EC:
                processFoundECFlag(flag);
                break;
            case Comms.ATTACK_EC:
                if (turnCount < 6) {
                    role = ATTACK_EC;
                    attackLoc = Comms.readAttackECSignal(flag, rc);
                }
                break;

        }
    }

    public static void run() throws GameActionException {

        if (rc.canGetFlag(homeECID)) {
            int homeECFlag = rc.getFlag(homeECID);
            handleFlag(homeECFlag);
        } else {
            // clean out this
            multiPartMessagesByBotID.remove(homeECID);
        }
        RobotInfo[] nearbyBots = rc.senseNearbyRobots();
        MapLocation locOfClosestFriendlyPoli = null;
        int distToClosestFriendlyPoli = 999999999;
        MapLocation locOfClosestEnemyMuck = null;
        int distToClosestEnemyMuck = 9999999;

        // array[n] = number of friendlies within n r^2 distance
        int[] friendlyUnitsAtDistanceCount = new int[10];
        int[] oppUnitsAtDistanceCount = new int[10];
        int[] oppMuckUnitsAtDistanceCount = new int[10];
        int[] neutralUnitsAtDistanceCount = new int[10];
        // int[] oppSlandUnitsAtDistanceCount = new int[10];
        // int[] oppECUnitsAtDistanceCount = new int[10];
        // int[] oppPoliUnitsAtDistanceCount = new int[10];

        LinkedList<MapLocation> locsOfFriendSlands = new LinkedList<>();

        for (int i = nearbyBots.length; --i >= 0;) {
            RobotInfo bot = nearbyBots[i];
            int dist = rc.getLocation().distanceSquaredTo(bot.location);
            boolean withinDist = dist <= 9;
            if (bot.team == myTeam) {
                if (withinDist) {
                    friendlyUnitsAtDistanceCount[dist] += 1;
                }
                if (rc.canGetFlag(bot.ID) && rc.getFlag(bot.ID) == Comms.IMASLANDERERR) {
                    // System.out.println("Found sland");
                    locsOfFriendSlands.add(bot.location);
                } else if (bot.type == RobotType.POLITICIAN) {
                    if (dist < distToClosestFriendlyPoli) {
                        distToClosestFriendlyPoli = dist;
                        locOfClosestFriendlyPoli = bot.location;
                    }
                } else if (bot.type == RobotType.ENLIGHTENMENT_CENTER) {
                    if (homeEC == null) {
                        homeEC = bot.location;
                    }
                }
            } else if (bot.team == oppTeam) {
                if (withinDist) {
                    oppUnitsAtDistanceCount[dist] += 1;
                }
                if (bot.type == RobotType.MUCKRAKER) {
                    if (withinDist) {
                        oppMuckUnitsAtDistanceCount[dist] += 1;
                    }
                    if (dist < distToClosestEnemyMuck) {
                        distToClosestEnemyMuck = dist;
                        locOfClosestEnemyMuck = bot.location;
                    }
                }
            } else {
                // neutral team
                if (withinDist) {
                    neutralUnitsAtDistanceCount[dist] += 1;
                }
            }
        }
        if (haveMapDimensions()) {
            if (closestCorner == null) {
                MapLocation[] corners = new MapLocation[] { new MapLocation(offsetx, offsety),
                        new MapLocation(offsetx + mapWidth, offsety), new MapLocation(offsetx, offsety + mapHeight),
                        new MapLocation(offsetx + mapWidth, offsety + mapHeight) };
                int closestDist = 999999999;
                for (int i = -1; ++i < corners.length;) {
                    int dist = corners[i].distanceSquaredTo(rc.getLocation());
                    if (dist < closestDist) {
                        closestCorner = corners[i];
                        closestDist = dist;
                    }
                }
            }
        }

        // TODO: Buf sometimes homeEC is nul
        MapLocation protectLocation = homeEC;
        if (closestCorner != null) {
            protectLocation = closestCorner;
        }
        MapLocation currLoc = rc.getLocation();
        MapLocation bestLatticeLoc = null;
        int bestLatticeLocVal = Integer.MIN_VALUE;
        findbestLatticeLoc: {
            if (homeEC == null) {
                break findbestLatticeLoc;
            }
            if (currLoc.x % LATTICE_SIZE == 0 && currLoc.y % LATTICE_SIZE == 0 && currLoc.distanceSquaredTo(homeEC) > 2) {
                bestLatticeLoc = currLoc;
                bestLatticeLocVal = -bestLatticeLoc.distanceSquaredTo(protectLocation);
            }
            for (int i = 0; ++i < BFS25.length;) {
                int[] deltas = BFS25[i];
    
                MapLocation checkLoc = new MapLocation(currLoc.x + deltas[0], currLoc.y + deltas[1]);
                if (rc.onTheMap(checkLoc)) {
                    if (checkLoc.x % LATTICE_SIZE == 0 && checkLoc.y % LATTICE_SIZE == 0
                            && checkLoc.distanceSquaredTo(homeEC) > 2) {
                        RobotInfo bot = rc.senseRobotAtLocation(checkLoc);
                        if (bot == null || bot.ID == rc.getID()) {
                            int value = -checkLoc.distanceSquaredTo(protectLocation);
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
            if (rc.getLocation().distanceSquaredTo(homeEC) == 1) {
                // if buff is high, suicide?
                if (rc.canEmpower(1) && calculatePoliticianEmpowerConviction(myTeam, rc.getConviction(), 0)
                        / 4 > rc.getConviction() * 2) {
                    rc.empower(1);
                }
            } else if (rc.getLocation().distanceSquaredTo(homeEC) == 2) {
                // if buff is high, suicide?
                if (rc.canEmpower(2) && calculatePoliticianEmpowerConviction(myTeam, rc.getConviction(), 0)
                        / 6 > rc.getConviction() * 2) {
                    rc.empower(2);
                }
            }
        }

        if (role == DEFEND_SLANDERER) {
            if (locOfClosestEnemyMuck != null) {

                // find an optimal empower distance that destroys as many muckrakers as possible
                int mucksCountInRadius = 0;
                int friendlyUnitsInRadius = 0;
                int oppUnitsInRadius = 0;
                int neutralsInRadius = 0;
                int maxMucksDestroyed = 0;
                int optimalEmpowerRadius = -1;
                for (int i = 1; i <= 9; i++) {
                    mucksCountInRadius += oppMuckUnitsAtDistanceCount[i];
                    oppUnitsInRadius += oppUnitsAtDistanceCount[i];
                    neutralsInRadius += neutralUnitsAtDistanceCount[i];
                    friendlyUnitsInRadius += friendlyUnitsAtDistanceCount[i];
                    int n = (oppUnitsInRadius + friendlyUnitsInRadius + neutralsInRadius);
                    if (mucksCountInRadius > 0) {
                        int speechInfluencePerUnit = calculatePoliticianEmpowerConviction(myTeam, rc.getConviction(), 0)
                                / n;
                        if (speechInfluencePerUnit >= 2) {
                            if (mucksCountInRadius > maxMucksDestroyed) {
                                maxMucksDestroyed = mucksCountInRadius;
                                optimalEmpowerRadius = i;
                            }
                        }
                    }
                }

                Node<MapLocation> currNode = locsOfFriendSlands.dequeue();
                boolean slandererInDanger = false;
                // TODO: optimize to kill more mucks if not in danger and we see more than 2
                // relatively close
                while (currNode != null) {
                    if (currNode.val.distanceSquaredTo(locOfClosestEnemyMuck) <= MUCKRAKER_ACTION_RADIUS + 10) {
                        slandererInDanger = true;
                        break;
                    }
                    currNode = locsOfFriendSlands.dequeue();
                }

                // we keep following mucks if we cant optimally empower muckrakers or no
                // slanderers in danger and we can't do a 2 birds one stone.
                if (optimalEmpowerRadius == -1 || (!slandererInDanger && maxMucksDestroyed < 2)) {
                    // go towards closest muckraker in hope of more optimal empowering.
                    targetLoc = rc.getLocation().add(rc.getLocation().directionTo(locOfClosestEnemyMuck));
                } else {
                    if (rc.canEmpower(optimalEmpowerRadius)) {
                        rc.empower(optimalEmpowerRadius);
                    }
                }
            } else {
                // move away from EC...
                if (homeEC != null && rc.getLocation().distanceSquaredTo(homeEC) <= 2) {
                    targetLoc = rc.getLocation().add(rc.getLocation().directionTo(homeEC).opposite());
                } else {
                    if (bestLatticeLoc == null) {
                        targetLoc = rc.getLocation().add(exploreDir).add(exploreDir).add(exploreDir);
                        if (!rc.onTheMap(targetLoc)) {
                            exploreDir = exploreDir.rotateLeft().rotateLeft();
                            targetLoc = rc.getLocation().add(exploreDir).add(exploreDir).add(exploreDir);
                        }
                    } else {
                        targetLoc = bestLatticeLoc;
                    }
                }

            }
        } else if (role == ATTACK_EC) {
            targetLoc = attackLoc;
            int distToEC = rc.getLocation().distanceSquaredTo(attackLoc);
            if (distToEC <= 1) {
                // TOOD: shout neighbors to run if they arer therre
                rc.empower(1);
            } else if (distToEC <= POLI_ACTION_RADIUS) {
                // measure if worth
                int friendlyUnitsInRadius = 0;
                int oppUnitsInRadius = 0;
                int neutralsInRadius = 0;
                for (int i = 1; i <= 9; i++) {
                    oppUnitsInRadius += oppUnitsAtDistanceCount[i];
                    friendlyUnitsInRadius += friendlyUnitsAtDistanceCount[i];
                    neutralsInRadius += neutralUnitsAtDistanceCount[i];
                    int n = (oppUnitsInRadius + friendlyUnitsInRadius + neutralsInRadius);
                    if (distToEC <= i) {
                        int speechInfluencePerUnit = calculatePoliticianEmpowerConviction(myTeam, rc.getConviction(), 0)
                                / n;
                        if (speechInfluencePerUnit >= rc.getInfluence()) {
                            rc.empower(i);
                            break;
                        }
                    }
                }
            }
        }
        if (rc.isReady()) {
            Direction dir = getNextDirOnPath(targetLoc);
            if (dir != Direction.CENTER) {
                rc.move(dir);
            } else if (!rc.getLocation().equals(targetLoc)) {
                // wiggle out if perhaps stuck
                for (Direction wiggleDir : DIRECTIONS) {
                    // MapLocation loc = rc.getLocation().add(wiggleDir);
                    if (rc.canMove(wiggleDir)) {
                        rc.move(wiggleDir);
                    }
                }
            }
        }
    }
}
