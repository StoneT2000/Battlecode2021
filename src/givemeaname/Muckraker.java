package givemeaname;

import battlecode.common.*;
import static givemeaname.Constants.*;

public class Muckraker extends Unit {
    Muckraker(RobotController rc) {
        super(rc);
    }

    @Override
    void run() throws GameActionException {
        Direction dir = randomDirection();
        if (rc.canMove(dir)) {
            rc.move(dir);
        }
    }
}
