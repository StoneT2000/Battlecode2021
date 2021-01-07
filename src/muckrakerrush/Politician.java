package muckrakerrush;

import battlecode.common.*;
import static muckrakerrush.Constants.*;

public class Politician extends Unit {
    static final int SACRIFICE = 0;
    static final int NORMAL = 10;
    static int role = NORMAL;
    static MapLocation homeEC = null;
    static int homeECID = -1;
    public static void setup() throws GameActionException {
        // find ec spawned from
        MapLocation selfLoc = rc.getLocation();
        for (int i = DIRECTIONS.length; --i >= 0;) {
            MapLocation checkLoc = selfLoc.add(DIRECTIONS[i]);
            RobotInfo bot = rc.senseRobotAtLocation(checkLoc);
            if (bot != null && bot.type == RobotType.ENLIGHTENMENT_CENTER && bot.team == myTeam) {
                homeEC = bot.location;
                homeECID = bot.ID;
                break;
            }
        }
    }

    public static void run() throws GameActionException {
        int homeECFlag = rc.getFlag(homeECID);
        switch (Comms.SIGNAL_TYPE_MASK & homeECFlag) {
            case Comms.POLI_SACRIFICE:
                rc.empower(2);
                break;
        }
        
    }
}
