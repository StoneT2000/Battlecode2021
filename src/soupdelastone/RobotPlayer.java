package soupdelastone;

import battlecode.common.*;
import soupdelastone.Comms;
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

    /** locations using long map hash  (encoding w/o using map offsets) */
    static HashMap<Integer, ECDetails> enemyECLocs = new HashMap<>(12);
    /** locations using long map hash  (encoding w/o using map offsets) */
    static HashMap<Integer, ECDetails> friendlyECLocs = new HashMap<>(12);
    /** locations using long map hash  (encoding w/o using map offsets) */
    static HashMap<Integer, ECDetails> neutralECLocs = new HashMap<>(12);

    /** maps robot id to message parts they sent if message is multi-part */

    // maybe only used by EC atm
    static HashMap<Integer, int[]> multiPartMessagesByBotID;

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
                multiPartMessagesByBotID = new HashMap<>(50);
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
    
    public static void processFoundECLongHashFlag(int botID, int flag) {
        switch (flag & Comms.SIGNAL_TYPE_5BIT_MASK) {
            case Comms.FOUND_EC_X:
                // int[] data = Comms.readFoundECXSignal(flag);
                // int x = data[0];
                multiPartMessagesByBotID.put(botID, new int[]{flag});
                break;
            case Comms.FOUND_EC_Y:
                int[] data = multiPartMessagesByBotID.get(botID);
                multiPartMessagesByBotID.remove(botID);
                int xflag = data[0];
                int[] datax = Comms.readFoundECXSignal(xflag);
                int[] datay = Comms.readFoundECYSignal(flag);
                
                MapLocation ecloc = new MapLocation(datax[0], datay[0]);
                int teamval = datax[1];
                storeAndProcessECLocAndTeam(ecloc, teamval);
                break;
        }

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
        storeAndProcessECLocAndTeam(ECLoc, teamval);
        
    }
    /**
     * stores EC Loc data and removes old data if necessary
     */
    private static void storeAndProcessECLocAndTeam(MapLocation loc, int teamval) {
        int longHashKey = Comms.encodeMapLocationWithoutOffsets(loc);
        if (teamval == TEAM_ENEMY) {
            if (!enemyECLocs.contains(longHashKey)) {
                enemyECLocs.put(longHashKey, new ECDetails(loc, -1));
                // remove this from other hashtables in case they converted to enemy now
                neutralECLocs.remove(longHashKey);
                friendlyECLocs.remove(longHashKey);
            }
        } else if (teamval == TEAM_NEUTRAL) {
            if (!neutralECLocs.contains(longHashKey)) {
                neutralECLocs.put(longHashKey, new ECDetails(loc, -1));
                friendlyECLocs.remove(longHashKey);
                enemyECLocs.remove(longHashKey);
            }
        } else {
            if (!friendlyECLocs.contains(longHashKey)) {
                // System.out.println("Found friend EC at " + ECLoc);
                friendlyECLocs.put(longHashKey, new ECDetails(loc, -1));
                neutralECLocs.remove(longHashKey);
                enemyECLocs.remove(longHashKey);
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
