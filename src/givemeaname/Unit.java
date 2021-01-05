package givemeaname;

import battlecode.common.*;
import static givemeaname.Constants.*;

public abstract class Unit {
    RobotController rc;

    Unit(RobotController rc) {
        this.rc = rc;
    }

    /** must implement. This is called to run the mobile unit */
    abstract void run() throws GameActionException;

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
}
