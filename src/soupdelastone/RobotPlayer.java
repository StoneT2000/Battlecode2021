package soupdelastone;

import battlecode.common.*;
import soupdelastone.utils.HashMap;
import soupdelastone.utils.HashTable;
import static soupdelastone.Constants.*;

public strictfp class RobotPlayer {
    static RobotController rc;
    static int turnCount;

    static Team myTeam;
    static Team oppTeam;
    static boolean setFlagThisTurn = false;

    static int offsetx = Integer.MAX_VALUE;
    static int offsety = Integer.MAX_VALUE;
    static int mapWidth = 0;
    static int mapHeight = 0;

    /** locations using short map hash  (encoding using map offsets) */
    static HashMap<Integer, ECDetails> enemyECLocs = new HashMap<>(12);
    /** locations using short map hash  (encoding using map offsets) */
    static HashMap<Integer, ECDetails> friendlyECLocs = new HashMap<>(12);
    /** locations using short map hash  (encoding using map offsets) */
    static HashMap<Integer, ECDetails> neutralECLocs = new HashMap<>(12);

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
        RobotType spawnType = rc.getType();
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
                        if (spawnType != rc.getType()) {
                            setFlag(Comms.getUnitDetailsSignal(TYPE_POLITICIAN));
                            spawnType = rc.getType();
                            System.out.println("converted to poli from slander");
                            rc.setFlag(0);
                        }
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
    public static int calculatePoliticianEmpowerConviction(Team team, int conviction, int roundsInFuture) {
        return (int) (conviction * rc.getEmpowerFactor(team, roundsInFuture) - GameConstants.EMPOWER_TAX);
    }

    public static int minimumConvictionNeededToSpreadNConviction(Team team, int n, int roundsInFuture) {
        return (int)((n + GameConstants.EMPOWER_TAX) / rc.getEmpowerFactor(team, roundsInFuture));
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
        if (!haveMapDimensions()) {
            // TODO: store signal for later processing so we are a bit more efficient...
            return;
        }
        int shorthHashKey = data[1];
        
        MapLocation ECLoc = Comms.decodeMapLocation(shorthHashKey, offsetx, offsety);
        if (teamval == TEAM_ENEMY) {
            if (!enemyECLocs.contains(shorthHashKey)) {
                enemyECLocs.put(shorthHashKey, new ECDetails(ECLoc, -1));
                // remove this from other hashtables in case they converted to enemy now
                neutralECLocs.remove(shorthHashKey);
                friendlyECLocs.remove(shorthHashKey);
            }
        } else if (teamval == TEAM_NEUTRAL) {
            if (!neutralECLocs.contains(shorthHashKey)) {
                neutralECLocs.put(shorthHashKey, new ECDetails(ECLoc, -1));
                friendlyECLocs.remove(shorthHashKey);
                enemyECLocs.remove(shorthHashKey);
            }
        } else {
            if (!friendlyECLocs.contains(shorthHashKey)) {
                // System.out.println("Found friend EC at " + ECLoc);
                friendlyECLocs.put(shorthHashKey, new ECDetails(ECLoc, -1));
                neutralECLocs.remove(data[1]);
                enemyECLocs.remove(shorthHashKey);
            }
        }
    }

    public static boolean wonInVotes() {
        if (rc.getTeamVotes() >= GameConstants.GAME_MAX_NUMBER_OF_ROUNDS / 2 + 1) {
            return true;
        }
        return false;
    }
}
