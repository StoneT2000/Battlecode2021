package slandertest;

import battlecode.common.*;
import static slandertest.Constants.*;

public class Politician extends Unit {
    public static void setup() throws GameActionException {

    }

    public static void run() throws GameActionException {
        Direction dir = randomDirection();
        if (rc.canMove(dir)) {
            rc.move(dir);
        }
    }
}
