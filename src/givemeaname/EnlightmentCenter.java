package givemeaname;

import battlecode.common.*;
import static givemeaname.Constants.*;

public class EnlightmentCenter extends RobotPlayer {
    public static void run() throws GameActionException {
        // do smth with rc
        rc.bid(1);
        System.out.println("EC At " + rc.getLocation() + " - Influence: " + rc.getInfluence());
        if (rc.canBuildRobot(RobotType.MUCKRAKER, Direction.NORTH, 1)) {
            rc.buildRobot(RobotType.MUCKRAKER, Direction.NORTH, 1);
            RobotInfo[] nearbyBots = rc.senseNearbyRobots();
        }
    }
}
