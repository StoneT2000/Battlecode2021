package lentilsoup;

import battlecode.common.*;
import static lentilsoup.Constants.*;

public class Slanderer extends Unit {
    static MapLocation targetLoc = null;
    static MapLocation homeEC = null;
    static int homeECID = -1;

    static int offsetx = 0;
    static int offsety = 0;
    static int mapWidth = 0;
    static int mapHeight = 0;
    public static void setup() throws GameActionException {
        setHomeEC();
    }

    public static void run() throws GameActionException {
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
        targetLoc = rc.getLocation();
        if (rc.isReady()) {
            Direction dir = getNextDirOnPath(targetLoc);
            if (dir != Direction.CENTER) {
                rc.move(dir);
            }
        }
    }
}
