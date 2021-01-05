package givemeaname;

import battlecode.common.*;
import static givemeaname.Constants.*;

public class Slanderer extends Unit {
    Slanderer(RobotController rc) {
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
