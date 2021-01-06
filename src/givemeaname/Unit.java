package givemeaname;

import battlecode.common.*;
import static givemeaname.Constants.*;

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
    static Direction getNextDirOnPath(MapLocation targetLoc) {
        // greedy method
        Direction greedyDir = rc.getLocation().directionTo(targetLoc);
        if (rc.canMove(greedyDir)) {
            return greedyDir;
        }
        return Direction.CENTER;
    }
}
