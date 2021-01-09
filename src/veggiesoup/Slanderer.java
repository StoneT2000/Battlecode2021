package veggiesoup;

import battlecode.common.*;
import static veggiesoup.Constants.*;

public class Slanderer extends Unit {
    static MapLocation targetLoc = null;

    public static void setup() throws GameActionException {
        setHomeEC();
    }

    public static void run() throws GameActionException {
        if (rc.canGetFlag(homeECID)) {
            int homeECFlag = rc.getFlag(homeECID);
            switch (Comms.SIGNAL_TYPE_MASK & homeECFlag) {
                case Comms.MAP_OFFSET_X_AND_WIDTH:
                    int[] vs = Comms.readMapOffsetSignalXWidth(homeECFlag);
                    offsetx = vs[0];
                    mapWidth = vs[1];
                    break;
                case Comms.MAP_OFFSET_Y_AND_HEIGHT:
                    int[] vs2 = Comms.readMapOffsetSignalYHeight(homeECFlag);
                    offsety = vs2[0];
                    mapHeight = vs2[1];
                    break;
            }
        }

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
        if (rc.getLocation().distanceSquaredTo(homeEC) <= 2 ) {
            targetLoc = rc.getLocation().add(rc.getLocation().directionTo(homeEC).opposite());
        }

        if (locOfClosestEnemyMuckraker != null) {
            targetLoc = rc.getLocation().add(findDirAwayFromLocations(new MapLocation[]{locOfClosestEnemyMuckraker}));
        }
        
        if (rc.isReady()) {
            Direction dir = getNextDirOnPath(targetLoc);
            if (dir != Direction.CENTER) {
                rc.move(dir);
            }
        }
    }
}
