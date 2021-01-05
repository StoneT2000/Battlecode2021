package givemeaname;

import battlecode.common.*;
import static givemeaname.Constants.*;

public class EnlightmentCenter {
    RobotController rc;

    EnlightmentCenter(RobotController rc) {
        this.rc = rc;
    }

    void run() throws GameActionException {
        // do smth with rc
        rc.bid(1);

        if (rc.canBuildRobot(RobotType.MUCKRAKER, Direction.NORTH, 1)) {
            rc.buildRobot(RobotType.MUCKRAKER, Direction.NORTH, 1);
        }
    }
}
