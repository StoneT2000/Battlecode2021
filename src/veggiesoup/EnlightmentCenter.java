package veggiesoup;

import battlecode.common.*;
import veggiesoup.utils.*;

import static veggiesoup.Constants.*;

public class EnlightmentCenter extends RobotPlayer {
    static int lastScoutBuildDirIndex = -1;
    static int lastRushBuildIndex = -1;
    static final int BUILD_SCOUTS = 0;
    static final int NORMAL = 1;
    static int role = NORMAL;

    // offsets are also "lowMapX/Y"
    static int offsetx = Integer.MAX_VALUE;
    static int offsety = Integer.MAX_VALUE;
    static int highMapX = Integer.MIN_VALUE;
    static int highMapY = Integer.MIN_VALUE;

    static HashTable<Integer> ecIDs = new HashTable<>(10);
    static HashTable<Integer> muckrakerIDs = new HashTable<>(50);
    static HashTable<Integer> slandererIDs = new HashTable<>(50);
    static HashTable<Integer> politicianIDs = new HashTable<>(20);
    static int lastBuildTurn = -1;

    static double minBidAmount = 2;
    static int lastTeamVotes = 0;
    static int lastTurnInfluence = GameConstants.INITIAL_ENLIGHTENMENT_CENTER_INFLUENCE;

    static class Stats {
        static int muckrakersBuilt = 0;
        static int politiciansBuilt = 0;
        static int slanderersBuilt = 0;
    }

    public static void setup() throws GameActionException {
        // boolean firstEC = rc.getTeamVotes() == 0;
        // if (firstEC) {
        // do scout building
        role = BUILD_SCOUTS;
        lastScoutBuildDirIndex = -1;
        // rc.setFlag(0);
        // } else {
        // rc.setFlag(rc.getID());
        // }
        ecIDs.add(rc.getID());
    }

    public static void run() throws GameActionException {
        setFlagThisTurn = false;
        // how much influence is spent on building
        int spentInfluence = 0;

        int influenceGainedLastTurn = rc.getInfluence() - lastTurnInfluence;
        // determine if we won or lost/tied bid?
        if (!wonInVotes()) {
            if (rc.getTeamVotes() > lastTeamVotes) {
                // won, lower the bid
                minBidAmount /= 1.25;
                minBidAmount = Math.max(minBidAmount, 1);
            } else {
                // lost, increase bid, and see what happens
                minBidAmount *= 1.5;
            }
            lastTeamVotes = rc.getTeamVotes();

            if (rc.getInfluence() >= minBidAmount && rc.getInfluence() >= 400) {
                rc.bid((int) minBidAmount);
            } else if (rc.getInfluence() >= 10) {
                if (influenceGainedLastTurn > 2) {
                    rc.bid(2);
                } else {
                    rc.bid(1);
                }
            }
        }

        System.out.println("TURN: " + turnCount + " | EC At " + rc.getLocation() + " - Influence: " + rc.getInfluence()
                + " - Conviction: " + rc.getConviction() + " - CD: " + rc.getCooldownTurns() + " - ROLE: " + role
                + " - Units Controlled EC: " + ecIDs.size + ", S: " + slandererIDs.size + ", P: " + politicianIDs.size
                + ", M: " + muckrakerIDs.size + " | Gained " + influenceGainedLastTurn + " influence");

        // global comms code independent of role

        // iterate over all known units, remove if they arent there anymore
        ecIDs.resetIterator();
        muckrakerIDs.resetIterator();
        slandererIDs.resetIterator();
        politicianIDs.resetIterator();

        Node<Integer> currIDNode = muckrakerIDs.next();
        LinkedList<Integer> idsToRemove = new LinkedList<>();
        while (currIDNode != null) {
            try {
                int flag = rc.getFlag(currIDNode.val);
                switch (Comms.SIGNAL_TYPE_MASK & flag) {
                    case Comms.CORNER_LOC_X:
                        int cx = Comms.readCornerLocSignalX(flag);
                        highMapX = Math.max(highMapX, cx);
                        offsetx = Math.min(offsetx, cx);
                        mapWidth = highMapX - offsetx + 1;
                        break;
                    case Comms.CORNER_LOC_Y:
                        int cy = Comms.readCornerLocSignalY(flag);
                        highMapY = Math.max(highMapY, cy);
                        offsety = Math.min(offsety, cy);
                        mapHeight = highMapY - offsety + 1;
                        break;
                    case Comms.FOUND_EC:
                        processFoundECFlag(flag);
                }
            } catch (GameActionException error) {
                // lost this muck
                idsToRemove.add(currIDNode.val);
            }
            currIDNode = muckrakerIDs.next();
        }

        while (true) {
            Node<Integer> node = idsToRemove.dequeue();
            if (node == null)
                break;
            else {
                muckrakerIDs.remove(node.val);
            }
        }

        currIDNode = slandererIDs.next();
        while (currIDNode != null) {
            try {
                int flag = rc.getFlag(currIDNode.val);
                if ((Comms.SIGNAL_TYPE_MASK & flag) == Comms.UNIT_DETAILS) {
                    // check if switched to politican, and remove/add appropriately
                    int[] data = Comms.readUnitDetails(flag);
                    if (data[0] == TYPE_POLITICIAN) {
                        idsToRemove.add(currIDNode.val);
                        politicianIDs.add(currIDNode.val);
                    }
                }
            } catch (GameActionException error) {
                idsToRemove.add(currIDNode.val);
            }
            currIDNode = slandererIDs.next();
        }
        while (true) {
            Node<Integer> node = idsToRemove.dequeue();
            if (node == null)
                break;
            else {
                slandererIDs.remove(node.val);
            }
        }

        currIDNode = politicianIDs.next();
        while (currIDNode != null) {
            try {
                int flag = rc.getFlag(currIDNode.val);
            } catch (GameActionException error) {
                idsToRemove.add(currIDNode.val);
            }
            currIDNode = politicianIDs.next();
        }
        while (true) {
            Node<Integer> node = idsToRemove.dequeue();
            if (node == null)
                break;
            else {
                politicianIDs.remove(node.val);
            }
        }

        // strategy to take nearby neutral HQ asap
        boolean buildEarlyPoliticianToTakeNeutralHQ = false;
        // if enemy HQ is near
        boolean nearbyEnemyHQ = false;
        boolean nearbyEnemyMuckraker = false;
        RobotInfo[] nearbyBots = rc.senseNearbyRobots();

        int enemyPoliticianConvictionNearby = -1;
        for (int i = nearbyBots.length; --i >= 0;) {
            RobotInfo bot = nearbyBots[i];
            if (bot.team == oppTeam && bot.type == RobotType.POLITICIAN) {
                int c = calculatePoliticianEmpowerConviction(oppTeam, bot.conviction, 0);
                enemyPoliticianConvictionNearby += c;
            } else if (bot.type == RobotType.ENLIGHTENMENT_CENTER) {
                if (bot.team == Team.NEUTRAL && bot.influence <= 130) {
                    buildEarlyPoliticianToTakeNeutralHQ = true;
                } else if (bot.team == oppTeam) {
                    nearbyEnemyHQ = true;
                }
            } else if (bot.team == oppTeam && bot.type == RobotType.MUCKRAKER) {
                nearbyEnemyMuckraker = true;
            }
        }

        switch (role) {
            case BUILD_SCOUTS:
                // TODO: handle edge cases if diagonals are blocked
                if (lastScoutBuildDirIndex < 3 && rc.isReady()) {
                    lastScoutBuildDirIndex = (lastScoutBuildDirIndex + 1);
                    Direction dir = DIAG_DIRECTIONS[lastScoutBuildDirIndex];
                    MapLocation buildLoc = rc.getLocation().add(dir);
                    if (rc.onTheMap(buildLoc)) {
                        RobotInfo bot = rc.senseRobotAtLocation(buildLoc);
                        if (bot == null) {
                            // flag of 0 is default no signal value flag of 1-4 represents build direction
                            setFlag(lastScoutBuildDirIndex + 1);
                            rc.buildRobot(RobotType.MUCKRAKER, dir, 1);
                            Stats.muckrakersBuilt += 1;
                            // add new id
                            RobotInfo newbot = rc.senseRobotAtLocation(buildLoc);
                            muckrakerIDs.add(newbot.ID);
                        }
                    }
                }
                if (lastScoutBuildDirIndex == 3) {
                    role = NORMAL;
                }
                break;
            case NORMAL:
                // TODO Calculate this
                int dilutingUnits = 2;
                if (enemyPoliticianConvictionNearby / dilutingUnits > rc.getConviction()) {
                    for (Direction dir : DIRECTIONS) {
                        MapLocation buildLoc = rc.getLocation().add(dir);
                        if (rc.canBuildRobot(RobotType.POLITICIAN, dir, rc.getConviction())) {
                            rc.buildRobot(RobotType.POLITICIAN, dir, rc.getConviction());
                            RobotInfo newbot = rc.senseRobotAtLocation(buildLoc);
                            politicianIDs.add(newbot.ID);
                            lastBuildTurn = turnCount;
                            spentInfluence += rc.getConviction();
                            break;
                        }
                    }
                }
                // generate infinite influence
                // else if (rc.getEmpowerFactor(myTeam, 0) > 1.5) {
                //     // TODO: bug, some of this is wasted due to nearby friendly units
                //     int minInfluence = (int) (GameConstants.EMPOWER_TAX / (rc.getEmpowerFactor(myTeam, 0) - 1.0));
                //     if (rc.getInfluence() >= minInfluence) {
                //         for (Direction dir : DIRECTIONS) {
                //             MapLocation buildLoc = rc.getLocation().add(dir);
                //             if (rc.canBuildRobot(RobotType.POLITICIAN, dir, rc.getConviction())) {
                //                 rc.buildRobot(RobotType.POLITICIAN, dir, rc.getConviction());
                //                 RobotInfo newbot = rc.senseRobotAtLocation(buildLoc);
                //                 politicianIDs.add(newbot.ID);
                //                 setFlag(Comms.getPoliSacrificeSignal());
                //                 lastBuildTurn = turnCount;
                //                 break;
                //             }
                //         }
                //     }
                // } 
                else {

                    // otherwise spam muckrakers wherever possible and ocassionally build slanderers
                    boolean buildSlanderer = false;
                    if (muckrakerIDs.size / (slandererIDs.size + 0.1) > 8 || turnCount <= 2) {
                        buildSlanderer = true;
                    }
                    if (nearbyEnemyMuckraker) {
                        buildSlanderer = false;
                    }
                    boolean buildPoli = false;

                    if (slandererIDs.size / (politicianIDs.size + 0.1) > 0.5) {
                        buildPoli = true;
                    }
                    if (nearbyEnemyMuckraker) {
                        buildPoli = true;
                    }
                    for (int i = 0; i < 8; i++) {
                        lastRushBuildIndex = (lastRushBuildIndex + 1) % DIRECTIONS.length;
                        Direction dir = DIRECTIONS[lastRushBuildIndex];
                        MapLocation buildLoc = rc.getLocation().add(dir);
                        if (buildPoli && rc.getInfluence() >= 20) {
                            int influenceWant = 20;
                            if (rc.canBuildRobot(RobotType.POLITICIAN, dir, influenceWant)) {
                                rc.buildRobot(RobotType.POLITICIAN, dir, influenceWant);
                                RobotInfo newbot = rc.senseRobotAtLocation(buildLoc);
                                politicianIDs.add(newbot.ID);
                                int sig = Comms.getBuiltUnitSignal(newbot.ID, newbot.type);
                                setFlag(sig);
                                lastBuildTurn = turnCount;
                                spentInfluence += influenceWant;
                                break;
                            }
                        }
                        if (buildSlanderer && rc.getInfluence() >= 148) {
                            int want = Math.min(rc.getInfluence(), 200);
                            if (rc.canBuildRobot(RobotType.SLANDERER, dir, want)) {
                                rc.buildRobot(RobotType.SLANDERER, dir, want);
                                RobotInfo newbot = rc.senseRobotAtLocation(buildLoc);
                                slandererIDs.add(newbot.ID);
                                int sig = Comms.getBuiltUnitSignal(newbot.ID, newbot.type);
                                setFlag(sig);
                                lastBuildTurn = turnCount;
                                spentInfluence += want;
                                break;
                            }
                        } else {
                            int influenceWant = 1;
                            if (rc.canBuildRobot(RobotType.MUCKRAKER, dir, influenceWant)) {
                                rc.buildRobot(RobotType.MUCKRAKER, dir, influenceWant);
                                RobotInfo newbot = rc.senseRobotAtLocation(buildLoc);
                                muckrakerIDs.add(newbot.ID);
                                int sig = Comms.getBuiltUnitSignal(newbot.ID, newbot.type);
                                setFlag(sig);
                                lastBuildTurn = turnCount;
                                spentInfluence += influenceWant;
                                break;
                            }
                        }
                    }
                }
                break;
        }

        // signal stuff
        /**
         * turncount modulus: 0, 1 = map details 2, 3, 4 = ec locations
         */

        int turnCountModulus = 2;
        if (enemyECLocs.size > 0) {
            turnCountModulus = 5;
        }
        System.out.println("turncountmod " + turnCountModulus);
        if (!setFlagThisTurn && turnCount % turnCountModulus > 1 && enemyECLocs.size > 0) {

            Node<Integer> ecLocHashNode = enemyECLocs.next();
            if (ecLocHashNode == null) {
                enemyECLocs.resetIterator();
                ecLocHashNode = enemyECLocs.next();
            }
            MapLocation ECLoc = Comms.decodeMapLocation(ecLocHashNode.val, offsetx, offsety);
            int signal = Comms.getFoundECSignal(ECLoc, TEAM_ENEMY, offsetx, offsety);
            setFlag(signal);
        }

        /**
         * If mapheight and width resolved o some valid value [32, 64], then send out
         * signals to everyone OPTIMIZATION: do this only when we build new units, or
         * maybe if nearby unit requests this information
         */
        if (mapHeight >= 32 && mapWidth >= 32) {
            System.out.println("Map Details: Offsets: (" + offsetx + ", " + offsety + ") - Width: " + mapWidth
                    + " - Height: " + mapHeight);
            // TODO: this might be too long of a wait
            if (!setFlagThisTurn && (turnCount - lastBuildTurn) >= 2) {
                if (turnCount % turnCountModulus == 0) {
                    int sig = Comms.getMapOffsetSignalXWidth(offsetx, mapWidth);
                    setFlag(sig);
                } else if (turnCount % turnCountModulus == 1) {
                    int sig = Comms.getMapOffsetSignalYHeight(offsety, mapHeight);
                    setFlag(sig);
                }
            }
        }


        lastTurnInfluence = rc.getInfluence() - spentInfluence;//stuff we do

    }
}
