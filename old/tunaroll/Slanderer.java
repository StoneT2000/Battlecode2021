package tunaroll;

import battlecode.common.*;
import static tunaroll.Constants.*;

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
        setFlag(Comms.IMASLANDERERR);
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
            if (bot.team == oppTeam && bot.type == RobotType.MUCKRAKER) {
                int dist = rc.getLocation().distanceSquaredTo(bot.location);
                if (dist < distToClosestEnemyMuckraker) {
                    distToClosestEnemyMuckraker = dist;
                    locOfClosestEnemyMuckraker = bot.location;
                }
            } else if (bot.team == myTeam && bot.type == RobotType.ENLIGHTENMENT_CENTER) {
                int dist = rc.getLocation().distanceSquaredTo(bot.location);
                if (dist < distToClosestFriendEC) {
                    distToClosestFriendEC = dist;
                    locOfClosestFriendEC = bot.location;
                }
            }
        }

        

        targetLoc = rc.getLocation();
        if (rc.getLocation().distanceSquaredTo(homeEC) <= 2 ) {
            targetLoc = rc.getLocation().add(rc.getLocation().directionTo(homeEC).opposite());
        }
        
        // search in sensor range for close stuff
        MapLocation currLoc = rc.getLocation();
        MapLocation bestLatticeLoc = null;
        
        if (originPoint == null) {
            originPoint = homeEC;
        }

        int bestLatticeLocVal = Integer.MIN_VALUE;
        if (locOnLattice(currLoc) && currLoc.distanceSquaredTo(homeEC) > 2) {
            bestLatticeLoc = currLoc;
            bestLatticeLocVal = - bestLatticeLoc.distanceSquaredTo(originPoint);
        }
        for (int i = 0; ++i < BFS20.length;) {
            int[] deltas = BFS20[i];

            MapLocation checkLoc = new MapLocation(currLoc.x + deltas[0], currLoc.y + deltas[1]);
            if (rc.onTheMap(checkLoc)) {
                if (locOnLattice(checkLoc) && checkLoc.distanceSquaredTo(homeEC) > 2) {
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
            Direction awayDir = findDirAwayFromLocations(new MapLocation[]{locOfClosestEnemyMuckraker});
            targetLoc = rc.getLocation().add(awayDir);
            originPoint = originPoint.add(awayDir);
        }
        
        if (rc.isReady()) {
            Direction dir = getNextDirOnPath(targetLoc);
            if (dir != Direction.CENTER) {
                rc.move(dir);
            };
        }
    }

    private static boolean locOnLattice(MapLocation loc) {
        return loc.x % LATTICE_SIZE != loc.y % LATTICE_SIZE;
    }
}
