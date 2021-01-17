package slandertest;

import battlecode.common.*;
import static slandertest.Constants.*;

public class Slanderer extends Unit {
    static MapLocation targetLoc = null;
    public static void setup() throws GameActionException {
    }

    public static void run() throws GameActionException {
        // Direction dir = randomDirection();
        // if (rc.canMove(dir)) {
        //     rc.move(dir);
        // }
        RobotInfo[] nearbyBots = rc.senseNearbyRobots();

        MapLocation locOfClosestEnemyMuckraker = null;
        int distToClosestEnemyMuckraker = 999999999;
        for (int i = nearbyBots.length; --i >= 0;) {
            RobotInfo bot = nearbyBots[i];
            if (bot.team == oppTeam && bot.type == RobotType.MUCKRAKER) {
                int dist = rc.getLocation().distanceSquaredTo(bot.location);
                if (dist < distToClosestEnemyMuckraker) {
                    distToClosestEnemyMuckraker = dist;
                    locOfClosestEnemyMuckraker = bot.location;
                }
            }
        }
        targetLoc = rc.getLocation();

        if (locOfClosestEnemyMuckraker != null) {
            targetLoc = rc.getLocation().add(locOfClosestEnemyMuckraker.directionTo(rc.getLocation()));
        }
        Direction dir = getNextDirOnPath(targetLoc);
        if (dir != Direction.CENTER) {
            rc.move(dir);
        }

    }
}
