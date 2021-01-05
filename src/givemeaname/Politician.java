package givemeaname;

import battlecode.common.*;
import static givemeaname.Constants.*;

public class Politician extends Unit {
    public static void run() throws GameActionException {
        Direction dir = randomDirection();
        if (rc.canMove(dir)) {
            rc.move(dir);
        }
    }
}
