package muckrakerrush;

import battlecode.common.*;
import static muckrakerrush.Constants.*;

public abstract class Unit extends RobotPlayer {

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
}
