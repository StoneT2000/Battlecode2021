package givemeaname;

import battlecode.common.*;
import static givemeaname.Constants.*;

public class Muckraker extends Unit {
    public static void run() throws GameActionException {
        Direction dir = randomDirection();
        if (rc.canMove(dir)) {
            rc.move(dir);
        }
    }
}
