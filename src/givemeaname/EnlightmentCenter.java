package givemeaname;

import battlecode.common.*;
import static givemeaname.Constants.*;

public class EnlightmentCenter extends RobotPlayer {
    static int lastScoutBuildDirIndex = -1;
    static final int BUILD_SCOUTS = 0;
    static final int NORMAL = 1;
    static int role = NORMAL;

    static class Stats {
        static int muckrakersBuilt = 0;
        static int politiciansBuilt = 0;
        static int slanderersBuilt = 0;
    }

    public static void setup() throws GameActionException {
        boolean firstEC = rc.getTeamVotes() == 0;
        if (firstEC) {
            // do scout building
            role = BUILD_SCOUTS;
            lastScoutBuildDirIndex = -1;
            // rc.setFlag(0);
        }
    }

    public static void run() throws GameActionException {
        // do smth with rc
        rc.bid(1);
        System.out.println("TURN: " + turnCount + " | EC At " + rc.getLocation() + " - Influence: " + rc.getInfluence()
                + " - CD: " + rc.getCooldownTurns() + " - ROLE: " + role + " - Built S: " + Stats.slanderersBuilt
                + ", P: " + Stats.politiciansBuilt + ", M: " + Stats.muckrakersBuilt);
        switch (role) {
            case BUILD_SCOUTS:
                if (lastScoutBuildDirIndex < 3 && rc.isReady()) {
                    lastScoutBuildDirIndex = (lastScoutBuildDirIndex + 1);
                    Direction dir = DIAG_DIRECTIONS[lastScoutBuildDirIndex];
                    MapLocation buildLoc = rc.getLocation().add(dir);
                    if (rc.onTheMap(buildLoc)) {
                        RobotInfo bot = rc.senseRobotAtLocation(buildLoc);
                        if (bot == null) {
                            // flag of 0 is default no signal value flag of 1-4 represents build direction
                            rc.setFlag(lastScoutBuildDirIndex + 1);
                            rc.buildRobot(RobotType.MUCKRAKER, dir, 1);
                            Stats.muckrakersBuilt += 1;
                        }
                    }
                }
                break;
            case NORMAL:
                break;
        }
        // if (rc.canBuildRobot(RobotType.MUCKRAKER, Direction.NORTH, 1)) {
        // rc.buildRobot(RobotType.MUCKRAKER, Direction.NORTH, 1);
        // RobotInfo[] nearbyBots = rc.senseNearbyRobots();

        // }
    }
}
