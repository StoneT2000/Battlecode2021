package veggiesoup;

import battlecode.common.*;
import veggiesoup.utils.HashTable;

import static veggiesoup.Constants.*;

public abstract class Unit extends RobotPlayer {

    static MapLocation homeEC = null;
    static int homeECID = -1;

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
        Direction greedyDir = Direction.CENTER;
        // System.out.println("======= " + turnCount + " =======");
        double bestValue = tileMoveCost(rc.getLocation()) + rc.getLocation().distanceSquaredTo(targetLoc);
        int origDist = rc.getLocation().distanceSquaredTo(targetLoc);
        for (Direction dir : DIRECTIONS) {
            MapLocation newloc = rc.getLocation().add(dir);
            if (rc.onTheMap(newloc) && rc.senseRobotAtLocation(newloc) == null) {

                int thisDist = newloc.distanceSquaredTo(targetLoc);
                double val = tileMoveCost(newloc) + thisDist;
                if (thisDist > origDist) {
                    val += 200000;
                }
                // System.out.println("Target: " + targetLoc + " - from " + rc.getLocation() + " check: " +  newloc + " - cost: " + val);
                if (val < bestValue) {
                    bestValue = val;
                    greedyDir = dir;
                }
            }
        }
        // System.out.println("Best " + greedyDir + " - " + bestValue);
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
            RobotInfo bot = rc.senseRobotAtLocation(checkLoc);
            if (bot != null && bot.type == RobotType.ENLIGHTENMENT_CENTER && bot.team == myTeam) {
                int flag = rc.getFlag(bot.ID);
                int parsed = (flag & Comms.SIGNAL_TYPE_MASK);
                System.out.println(flag);
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
        // System.out.println("HOMEEC: " + homeEC + " - " + homeECID);
    }

    // TODO: optimize this using the array of locs sorted by distance or smth to self
    static Direction findDirAwayFromLocations(MapLocation[] locs) {
        return locs[0].directionTo(rc.getLocation());
    }
}
