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
}
