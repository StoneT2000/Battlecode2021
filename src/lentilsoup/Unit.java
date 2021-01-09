package lentilsoup;

import battlecode.common.*;
import static lentilsoup.Constants.*;

public abstract class Unit extends RobotPlayer {

    static MapLocation homeEC = null;
    static int homeECID = -1;

    static int offsetx = 0;
    static int offsety = 0;
    static int mapWidth = 0;
    static int mapHeight = 0;

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
                    System.out.println(data[0] + " - " + data[1]);
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
}
