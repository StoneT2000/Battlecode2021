package muckrakerrush;

import battlecode.common.*;
import static muckrakerrush.Constants.*;

public class Politician extends Unit {
    static final int SACRIFICE = 0;
    static final int NORMAL = 10;
    static int role = NORMAL;
    public static void setup() throws GameActionException {
        // find ec spawned from
        setHomeEC();
        // if null, we probablyy got a signal to do something instant like sacrifice/empower
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
        
    }
}
