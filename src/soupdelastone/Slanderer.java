package soupdelastone;

import battlecode.common.*;
import static soupdelastone.Constants.*;

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
    static MapLocation closestCorner = null;
    public static void setup() throws GameActionException {
        setHomeEC();
    }

    public static void run() throws GameActionException {
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
        for (int i = nearbyBots.length; --i >= 0;) {
            RobotInfo bot = nearbyBots[i];
            if (bot.team == oppTeam && bot.type == RobotType.MUCKRAKER) {
                int dist = rc.getLocation().distanceSquaredTo(bot.location);
                if (dist < distToClosestEnemyMuckraker) {
                    distToClosestEnemyMuckraker = dist;
                    locOfClosestEnemyMuckraker = bot.location;
                }
            }
        }

        

        targetLoc = rc.getLocation();
        if (rc.getLocation().distanceSquaredTo(homeEC) <= 2 ) {
            targetLoc = rc.getLocation().add(rc.getLocation().directionTo(homeEC).opposite());
        }

        // lattice in bottom corner
        if (haveMapDimensions()) {
            if (closestCorner == null) {
                MapLocation[] corners = new MapLocation[]{
                    new MapLocation(offsetx, offsety),
                    new MapLocation(offsetx + mapWidth, offsety),
                    new MapLocation(offsetx, offsety + mapHeight),
                    new MapLocation(offsetx + mapWidth, offsety + mapHeight)
                };
                int closestDist = 999999999;
                for (int i = -1; ++i < corners.length;) {
                    int dist = corners[i].distanceSquaredTo(rc.getLocation());
                    if (dist < closestDist) {
                        closestCorner = corners[i];
                        closestDist = dist;
                    }
                }
            }

            // search in sensor range for close stuff
            MapLocation currLoc = rc.getLocation();
            MapLocation bestLatticeLoc = null;
            int bestLatticeLocVal = Integer.MIN_VALUE;
            if (locOnLattice(currLoc)) {
                bestLatticeLoc = currLoc;
                bestLatticeLocVal = - bestLatticeLoc.distanceSquaredTo(closestCorner);
            }
            for (int i = 0; ++i < BFS20.length;) {
                int[] deltas = BFS20[i];

                MapLocation checkLoc = new MapLocation(currLoc.x + deltas[0], currLoc.y + deltas[1]);
                if (rc.onTheMap(checkLoc)) {
                    if (locOnLattice(checkLoc)) {
                        RobotInfo bot = rc.senseRobotAtLocation(checkLoc);
                        if (bot == null || bot.ID == rc.getID()) {
                            int value = -checkLoc.distanceSquaredTo(closestCorner);
                            if (value > bestLatticeLocVal) {
                                bestLatticeLocVal = value;
                                bestLatticeLoc = checkLoc;
                            }
                        }
                    }
                }
            }
            if (bestLatticeLoc == null) {
                // find closest corner.
                targetLoc = closestCorner;
            } else {
                targetLoc = bestLatticeLoc;
            }
        }

        if (locOfClosestEnemyMuckraker != null) {
            targetLoc = rc.getLocation().add(findDirAwayFromLocations(new MapLocation[]{locOfClosestEnemyMuckraker}));
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
