package fishysushi;

import battlecode.common.*;
import fishysushi.utils.HashTable;
import fishysushi.utils.LinkedList;

import static fishysushi.Constants.*;

public abstract class Unit extends RobotPlayer {

    static MapLocation homeEC = null;
    static int homeECID = -1;
    static Direction lastDir = null;

    /**
     * Hash table used to check if we're already planning to send this location or
     * not
     */
    static HashTable<Integer> foundECLocHashes = new HashTable<>(12);
    /** queue of EC Details to send */
    static LinkedList<ECDetails> ECDetailsToSend = new LinkedList<>();

    static final int SKIP_FLAG = -1;
    static LinkedList<Integer> specialMessageQueue = new LinkedList<>();

    /**
     * define helper methods for units in general e.g. pathing, comms etc.
     */

    /**
     * Returns a random Direction.
     *
     * @return a random Direction
     */
    static Direction randomDirection() {

        return DIRECTIONS[(int) (Math.random() * DIRECTIONS.length)];
    }


    static void computePath(MapLocation targetLoc) {
        // TODO fill in and use
    }
    /**
     * 
     * @param targetLoc
     * @return greedy dir, Direction.CENTER if staying put is best or cant move?
     */
    static Direction getNextDirOnPath(MapLocation targetLoc) throws GameActionException {
        // greedy method
        // Direction greedyDir = rc.getLocation().directionTo(targetLoc);
        if (rc.getLocation().distanceSquaredTo(targetLoc) <= 2) {
            return getGreedyDir(targetLoc);
        }
        Direction greedyDir = Direction.CENTER;
        double bestValue = tileMoveCost(rc.getLocation()) + rc.getLocation().distanceSquaredTo(targetLoc);
        int origDist = rc.getLocation().distanceSquaredTo(targetLoc);
        for (Direction dir : DIRECTIONS) {
            MapLocation newloc = rc.getLocation().add(dir);
            // if (lastDir != null && dir == lastDir) {
            //     continue;
            // }
            if (rc.onTheMap(newloc) && rc.senseRobotAtLocation(newloc) == null) {

                int thisDist = newloc.distanceSquaredTo(targetLoc);
                
                double val = tileMoveCost(newloc) + thisDist;
                
                if (thisDist > origDist) {
                    val += 200000;
                }
                if (thisDist == 0) {
                    val = 0;
                }
                if (val < bestValue) {
                    bestValue = val;
                    greedyDir = dir;
                }
            }
        }
        if (greedyDir == Direction.CENTER) {
            return Direction.CENTER;
        }
        if (rc.canMove(greedyDir)) {
            lastDir = greedyDir;
            return greedyDir;
        }
        return Direction.CENTER;
    }

    static Direction getGreedyDir(MapLocation targetLoc) throws GameActionException {
        Direction greedyDir = Direction.CENTER;
        int closestToTarget = rc.getLocation().distanceSquaredTo(targetLoc);
        for (Direction dir : DIRECTIONS) {
            MapLocation newloc = rc.getLocation().add(dir);
            if (rc.onTheMap(newloc) && rc.senseRobotAtLocation(newloc) == null) {

                int dist = newloc.distanceSquaredTo(targetLoc);
                if (dist < closestToTarget) {
                    closestToTarget = dist;
                    greedyDir = dir;
                }
            }
        }
        if (greedyDir == Direction.CENTER) {
            return Direction.CENTER;
        }
        if (rc.canMove(greedyDir)) {
            return greedyDir;
        }
        return Direction.CENTER;
    }

    private static int manhattanDist(MapLocation loc1, MapLocation loc2) {
        return Math.abs(loc1.x - loc2.x) + Math.abs(loc1.y - loc2.y);
    }
    // /**
    //  * 
    //  * @param dir1
    //  * @param dir2
    //  * @return true if dir1 is generally opposite of dir 2
    //  */
    // private static boolean isGenerallyOpposite(Direction dir1, Direction dir2) {

    // }

    private static double tileMoveCost(MapLocation loc) throws GameActionException {
        // double cost = 0f;
        double pass = rc.sensePassability(loc);
        return 1 / pass;
    }

    /**
     * sets homeEC and homeECID fields
     * 
     * if unit is a politician and it sees the poli_sacrifice signal and was just born, do so
     */
    static void setHomeEC() throws GameActionException {
        // find ec spawned from
        MapLocation selfLoc = rc.getLocation();
        for (int i = DIRECTIONS.length; --i >= 0;) {
            MapLocation checkLoc = selfLoc.add(DIRECTIONS[i]);
            if (rc.onTheMap(checkLoc)) {
                RobotInfo bot = rc.senseRobotAtLocation(checkLoc);
                if (bot != null && bot.type == RobotType.ENLIGHTENMENT_CENTER && bot.team == myTeam) {
                    int flag = rc.getFlag(bot.ID);
                    int parsed = (flag & Comms.SIGNAL_TYPE_MASK);
                    if (parsed == Comms.BUILT_UNIT) {
                        int[] data = Comms.readBuiltUnitSignal(flag);
                        int id = data[0];
                        if (id == rc.getID()) {
                            homeEC = bot.location;
                            homeECID = bot.ID;
                            break;
                        }
                    }
                    if (homeEC == null) {
                        homeEC = bot.location;
                        homeECID = bot.ID;
                    }
                }
            }
        }
        // System.out.println("HOMEEC: " + homeEC + " - " + homeECID);
    }

    // TODO: optimize this using the array of locs sorted by distance or smth to self
    static Direction findDirAwayFromLocations(MapLocation[] locs) {
        return locs[0].directionTo(rc.getLocation());
    }

    public static void handleFoundEC(RobotInfo bot) {
        int hash = Comms.encodeMapLocation(bot.location);
        if (!foundECLocHashes.contains(hash)) {
            foundECLocHashes.add(hash);
            int teamInd = TEAM_ENEMY;
            if (bot.team == oppTeam) {
                teamInd = TEAM_ENEMY;
            } else if (bot.team == myTeam) {
                teamInd = TEAM_FRIEND;
            } else {
                teamInd = TEAM_NEUTRAL;
            }
            ECDetailsToSend.add(new ECDetails(bot.location, bot.conviction, teamInd));
        }
    }
}