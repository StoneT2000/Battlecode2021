package lentilsoup;

import battlecode.common.*;
import static lentilsoup.Constants.*;

public class Politician extends Unit {
    static final int SACRIFICE = 0;
    static final int EXPLORE = 1;
    static final int DEFEND_SLANDERER = 10;
    static int role = DEFEND_SLANDERER;
    static MapLocation targetLoc = null;

    public static void setup() throws GameActionException {
        // find ec spawned from
        setHomeEC();
        // if null, we probablyy got a signal to do something instant like
        // sacrifice/empower
        if (homeEC == null) {
            role = SACRIFICE;
        }
    }

    public static void run() throws GameActionException {

        if (role == SACRIFICE) {
            if (rc.isReady()) {
                rc.empower(2);
            }
            return;
        }
        int homeECFlag = rc.getFlag(homeECID);
        switch (Comms.SIGNAL_TYPE_MASK & homeECFlag) {
            case Comms.POLI_SACRIFICE:
                role = SACRIFICE;
                break;
        }
        RobotInfo[] nearbyBots = rc.senseNearbyRobots();
        MapLocation locOfClosestFriendlyPoli = null;
        int distToClosestFriendlyPoli = 999999999;
        MapLocation locOfClosestEnemyMuck = null;
        int distToClosestEnemyMuck = 9999999;
        for (int i = nearbyBots.length; --i >= 0;) {
            RobotInfo bot = nearbyBots[i];
            if (bot.team == myTeam && bot.type == RobotType.POLITICIAN) {
                int dist = rc.getLocation().distanceSquaredTo(bot.location);
                if (dist < distToClosestFriendlyPoli) {
                    distToClosestFriendlyPoli = dist;
                    locOfClosestFriendlyPoli = bot.location;
                }
            } else if (bot.team == oppTeam && bot.type == RobotType.MUCKRAKER) {
                int dist = rc.getLocation().distanceSquaredTo(bot.location);
                if (dist < distToClosestEnemyMuck) {
                    distToClosestEnemyMuck = dist;
                    locOfClosestEnemyMuck = bot.location;
                }
            }
        }
        if (role == EXPLORE) {
            if (locOfClosestFriendlyPoli != null) {
                // head in scoutDir, direction of spawning, and find lattice points, rotate left
                // if hit edge and no spots found
                targetLoc = rc.getLocation().add(locOfClosestFriendlyPoli.directionTo(rc.getLocation()));

            }
        } else if (role == DEFEND_SLANDERER) {
            // to defend, stay near EC. FUTURE, move to cornern where we pack slanderers
            if (locOfClosestEnemyMuck != null) {
                targetLoc = rc.getLocation().add(rc.getLocation().directionTo(locOfClosestEnemyMuck));
                int distToClosestMuck = rc.getLocation().distanceSquaredTo(locOfClosestEnemyMuck);
                if (rc.canEmpower(distToClosestMuck)) {
                    rc.empower(distToClosestEnemyMuck);
                }
            } else {
                // move away from EC...
                if (rc.getLocation().distanceSquaredTo(homeEC) <= 2) {
                    targetLoc = rc.getLocation().add(rc.getLocation().directionTo(homeEC).opposite());
                }
            }
        }
        if (rc.isReady()) {
            Direction dir = getNextDirOnPath(targetLoc);
            if (dir != Direction.CENTER) {
                rc.move(dir);
            }
        }
    }
}
