package lentilsoup;

import battlecode.common.*;
import lentilsoup.utils.HashTable;
import static lentilsoup.Constants.*;

public strictfp class RobotPlayer {
    static RobotController rc;
    static int turnCount;

    static Team myTeam;
    static Team oppTeam;
    static boolean setFlagThisTurn = false;

    static int offsetx = 0;
    static int offsety = 0;
    static int mapWidth = 0;
    static int mapHeight = 0;

    /** locations using short map hash  (encoding using map offsets) */
    static HashTable<Integer> enemyECLocs = new HashTable<>(12);
    /** locations using short map hash  (encoding using map offsets) */
    static HashTable<Integer> friendlyECLocs = new HashTable<>(12);
    /** locations using short map hash  (encoding using map offsets) */
    static HashTable<Integer> neutralECLocs = new HashTable<>(12);

    /**
     * run() is the method that is called when a robot is instantiated in the
     * Battlecode world. If this method returns, the robot dies!
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // This is the RobotController object. You use it to perform actions from this
        // robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;
        myTeam = rc.getTeam();
        oppTeam = rc.getTeam().opponent();
        turnCount = 0;
        switch (rc.getType()) {
            case ENLIGHTENMENT_CENTER:
                // new instance like this takes ~7 bytecode
                EnlightmentCenter.setup();
                break;
            case POLITICIAN:
                Politician.setup();
                break;
            case SLANDERER:
                Slanderer.setup();
                break;
            case MUCKRAKER:
                Muckraker.setup();
                break;
        }
        while (true) {
            turnCount += 1;
            // Try/catch blocks stop unhandled exceptions, which cause your robot to freeze
            try {
                // Here, we've separated the controls into a different method for each
                // RobotType.
                // You may rewrite this into your own control structure if you wish.
                switch (rc.getType()) {
                    case ENLIGHTENMENT_CENTER:
                        // new instance like this takes ~7 bytecode
                        EnlightmentCenter.run();
                        break;
                    case POLITICIAN:
                        Politician.run();
                        break;
                    case SLANDERER:
                        Slanderer.run();
                        break;
                    case MUCKRAKER:
                        Muckraker.run();
                        break;
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform
                // this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println(rc.getType() + " Exception");
                e.printStackTrace();
            }
        }
    }

    // TODO: this might be wrong calculation
    public static int calculatePoliticianEmpowerConviction(Team team, int conviction) {
        return (int) (conviction * rc.getEmpowerFactor(team, 0) - GameConstants.EMPOWER_TAX);
    }

    public static void setFlag(int flag) throws GameActionException {
        rc.setFlag(flag);
        setFlagThisTurn = true;
    }

    public static boolean haveMapDimensions() {
        return mapHeight >= 32 && mapWidth >= 32;
    }
    
    public static void processFoundECFlag(int flag) {
        int[] data = Comms.readFoundECSignal(flag);
        int teamval = data[0];
        MapLocation ECLoc = Comms.decodeMapLocation(data[1], offsetx, offsety);
        if (teamval == TEAM_ENEMY) {
            if (!enemyECLocs.contains(data[1])) {
                System.out.println("Found EC at " + ECLoc);
                enemyECLocs.add(data[1]);
                // remove this from other hashtables in case they converted to enemy now
                neutralECLocs.remove(data[1]);
                friendlyECLocs.remove(data[1]);
            }
        } else if (teamval == TEAM_NEUTRAL) {
            if (!neutralECLocs.contains(data[1])) {
                neutralECLocs.add(data[1]);
                friendlyECLocs.remove(data[1]);
                enemyECLocs.remove(data[1]);
            }
        } else {
            if (!friendlyECLocs.contains(data[1])) {
                System.out.println("Found friend EC at " + ECLoc);
                friendlyECLocs.add(data[1]);
                neutralECLocs.remove(data[1]);
                enemyECLocs.remove(data[1]);
            }
        }
    }
}
