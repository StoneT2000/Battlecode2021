package skepticalsushi;

import battlecode.common.*;
import skepticalsushi.Comms;
import skepticalsushi.utils.HashMap;
import skepticalsushi.utils.HashTable;
import skepticalsushi.utils.Node;

import static skepticalsushi.Constants.*;

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

    /** locations using long map hash (encoding w/o using map offsets) */
    static HashMap<Integer, ECDetails> enemyECLocs = new HashMap<>(12);
    /** locations using long map hash (encoding w/o using map offsets) */
    static HashMap<Integer, ECDetails> friendlyECLocs = new HashMap<>(12);
    /** locations using long map hash (encoding w/o using map offsets) */
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
                multiPartMessagesByBotID = new HashMap<>(10);
                break;
            case SLANDERER:
                Slanderer.setup();
                multiPartMessagesByBotID = new HashMap<>(10);
                break;
            case MUCKRAKER:
                Muckraker.setup();
                multiPartMessagesByBotID = new HashMap<>(10);
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
        return (int) ((n + GameConstants.EMPOWER_TAX) / rc.getEmpowerFactor(team, roundsInFuture));
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
        int hash = data[1];
        int influence = data[2];

        MapLocation ECLoc = Comms.decodeMapLocation(hash, rc);
        storeAndProcessECLocAndTeam(ECLoc, teamval, influence);

    }

    /**
     * stores EC Loc data and removes old data if necessary
     */
    private static void storeAndProcessECLocAndTeam(MapLocation loc, int teamval, int influence) {
        int hash = Comms.encodeMapLocation(loc);
        if (teamval == TEAM_ENEMY) {
            if (!enemyECLocs.contains(hash)) {
                enemyECLocs.put(hash, new ECDetails(loc, influence, TEAM_ENEMY));
                // remove this from other hashtables in case they converted to enemy now
                neutralECLocs.remove(hash);
                friendlyECLocs.remove(hash);
            }
        } else if (teamval == TEAM_NEUTRAL) {
            if (!neutralECLocs.contains(hash)) {
                neutralECLocs.put(hash, new ECDetails(loc, influence, TEAM_NEUTRAL));
                friendlyECLocs.remove(hash);
                enemyECLocs.remove(hash);
            }
        } else {
            if (!friendlyECLocs.contains(hash)) {
                friendlyECLocs.put(hash, new ECDetails(loc, influence, TEAM_FRIEND));
                neutralECLocs.remove(hash);
                enemyECLocs.remove(hash);
            }
        }
    }

    public static boolean wonInVotes() {
        if (rc.getTeamVotes() >= GameConstants.GAME_MAX_NUMBER_OF_ROUNDS / 2 + 1) {
            return true;
        }
        return false;
    }

    public static int slandererInfPerTurn(int startingInfluence) {
        return (int) Math.floor((1. / 50. + 0.03 * Math.exp(-0.001 * startingInfluence)) * startingInfluence);
    }

    public static int dirToInt(Direction dir) {
        switch (dir) {
            case NORTH:
                return 0;
            case NORTHEAST:
                return 1;
            case EAST:
                return 2;
            case SOUTHEAST:
                return 3;
            case SOUTH:
                return 4;
            case SOUTHWEST:
                return 5;
            case WEST:
                return 6;
            case NORTHWEST:
                return 7;
            default:
                return -1;
        }
    }
}
