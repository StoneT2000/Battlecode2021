package soupdelastone;

import battlecode.common.*;
import soupdelastone.utils.*;

import static soupdelastone.Constants.*;

public class EnlightmentCenter extends RobotPlayer {
    static int lastScoutBuildDirIndex = -1;
    static int lastRushBuildIndex = -1;
    static final int BUILD_SCOUTS = 0;
    static final int NORMAL = 1;
    static int role = NORMAL;

    static int highMapX = Integer.MIN_VALUE;
    static int highMapY = Integer.MIN_VALUE;

    static HashTable<Integer> ecIDs = new HashTable<>(10);
    static HashTable<Integer> muckrakerIDs = new HashTable<>(120);
    static HashTable<Integer> slandererIDs = new HashTable<>(50);
    static HashTable<Integer> politicianIDs = new HashTable<>(40);
    static int lastBuildTurn = -1;

    static final int MIN_INF_NEEDED_TO_TAKE_NEUTRAL_EC = 550;

    static double minBidAmount = 2;
    static int lastTeamVotes = 0;
    static int lastTurnInfluence = GameConstants.INITIAL_ENLIGHTENMENT_CENTER_INFLUENCE;

    static int stageOfMultipartMessage = 0;

    static HashTable<Integer> attackingPolis = new HashTable<>(10);
    static int turnBuiltNeutralAttackingPoli = 0;

    static int multiPartMessageType = 0;
    static final int SKIP_FLAG = -1;
    static LinkedList<Integer> specialMessageQueue = new LinkedList<>();

    static class Stats {
        static int muckrakersBuilt = 0;
        static int politiciansBuilt = 0;
        static int slanderersBuilt = 0;
    }

    public static void setup() throws GameActionException {
        role = BUILD_SCOUTS;
        lastScoutBuildDirIndex = -1;
        ecIDs.add(rc.getID());
    }

    public static void run() throws GameActionException {

        setFlagThisTurn = false;
        // how much influence is spent on building
        int spentInfluence = 0;

        int influenceGainedLastTurn = rc.getInfluence() - lastTurnInfluence;
        // determine if we won or lost/tied bid?
        if (!wonInVotes() && turnCount >= 100) {
            if (rc.getTeamVotes() > lastTeamVotes) {
                // won, lower the bid
                minBidAmount /= 1.1;
                minBidAmount = Math.max(minBidAmount, 1);
            } else {
                double factor = 0.25;
                if (turnCount >= 300) {
                    factor = 0.5;
                } else if (turnCount >= 500) {
                    factor = 0.75;
                }
                // lost, increase bid, and see what happens
                minBidAmount *= 2;
                minBidAmount = Math.max(minBidAmount, 1);
                if (rc.getInfluence() < 1000) {
                    minBidAmount = Math.min(minBidAmount, Math.ceil(influenceGainedLastTurn * factor));
                }
                
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
                + ", M: " + muckrakerIDs.size + " | Gained " + influenceGainedLastTurn + " influence | min bid "
                + minBidAmount);

        // global comms code independent of role

        // iterate over all known units, remove if they arent there anymore
        handleUnitChecks();

        // strategy to take nearby neutral HQ asap
        boolean buildEarlyPoliticianToTakeNeutralHQ = false;
        // if enemy HQ is near
        boolean nearbyEnemyHQ = false;
        boolean nearbyEnemyMuckraker = false;

        RobotInfo[] nearbyBots = rc.senseNearbyRobots();

        for (int i = nearbyBots.length; --i >= 0;) {
            RobotInfo bot = nearbyBots[i];
            if (bot.team == oppTeam && bot.type == RobotType.POLITICIAN) {
                // int c = calculatePoliticianEmpowerConviction(oppTeam, bot.conviction, 0);
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
        if (turnCount == 1 && nearbyEnemyMuckraker == false) {
            tryToBuildAnywhere(RobotType.SLANDERER, rc.getInfluence());
        }

        // strategy for taking ecs
        boolean stockInfluence = false;
        /** closest neutral ec loc */
        MapLocation neutralECLocToTake = null;
        int closestDist = 99999999;
        neutralECLocs.resetIterator();
        System.out.println("There aer " + neutralECLocs.size + " neutal ECs");
        HashMapNodeVal<Integer, ECDetails> neutralHashNode = neutralECLocs.next();
        while (neutralHashNode != null) {
            MapLocation loc = neutralHashNode.val.location;
            int dist = loc.distanceSquaredTo(rc.getLocation());
            if (dist < closestDist) {
                closestDist = dist;
                neutralECLocToTake = loc;
            }
            neutralHashNode = neutralECLocs.next();
        }
        if (neutralECLocToTake != null) {
            if (rc.getInfluence() >= MIN_INF_NEEDED_TO_TAKE_NEUTRAL_EC) {

            } else {
                stockInfluence = true;
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
                            setFlag(Comms.GO_SCOUT);
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
                    rc.setFlag(0);
                }
                break;
            case NORMAL:
                // generate infinite influence
                if (calculatePoliticianEmpowerConviction(myTeam, rc.getConviction(), 10) / 6 > rc.getConviction() * 2
                        && rc.getInfluence() < Integer.MAX_VALUE / 2) {
                    // TODO: bug, some of this is wasted due to nearby friendly units
                    for (Direction dir : DIRECTIONS) {
                        MapLocation buildLoc = rc.getLocation().add(dir);
                        if (rc.canBuildRobot(RobotType.POLITICIAN, dir, rc.getConviction())) {
                            rc.buildRobot(RobotType.POLITICIAN, dir, rc.getConviction());
                            RobotInfo newbot = rc.senseRobotAtLocation(buildLoc);
                            politicianIDs.add(newbot.ID);
                            lastBuildTurn = turnCount;
                            break;
                        }
                    }
                } else if (neutralECLocToTake != null && rc.getInfluence() >= MIN_INF_NEEDED_TO_TAKE_NEUTRAL_EC) {
                    // TODO: dont do this if enemy is near and can capture easily
                    RobotInfo newbot = tryToBuildAnywhere(RobotType.POLITICIAN, MIN_INF_NEEDED_TO_TAKE_NEUTRAL_EC,
                            rc.getLocation().directionTo(neutralECLocToTake));
                    if (newbot != null) {
                        attackingPolis.add(newbot.ID);
                        turnBuiltNeutralAttackingPoli = turnCount;
                        int sig1 = Comms.getAttackECXSignal(neutralECLocToTake.x);
                        int sig2 = Comms.getAttackECYSignal(neutralECLocToTake.y);
                        specialMessageQueue.add(SKIP_FLAG);
                        specialMessageQueue.add(sig1);
                        specialMessageQueue.add(sig2);
                    }
                }
                // proceed with generic building
                else {

                    // otherwise spam muckrakers wherever possible and ocassionally build slanderers
                    boolean buildSlanderer = false;
                    if (muckrakerIDs.size / (slandererIDs.size + 0.1) > 2 || turnCount <= 2) {
                        buildSlanderer = true;
                    }
                    if (nearbyEnemyMuckraker) {
                        buildSlanderer = false;
                    }
                    boolean buildPoli = false;

                    if (slandererIDs.size / (politicianIDs.size + 0.1) > 1) {
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
                                lastBuildTurn = turnCount;
                                spentInfluence += influenceWant;
                                break;
                            }
                        }
                    }
                }
                break;
        }

        // code to send messages to polis attacking neutal ECs
        attackingPolis.resetIterator();
        Node<Integer> attackPoliNode = attackingPolis.next();
        LinkedList<Integer> idsToRemove = new LinkedList<>();
        while (attackPoliNode != null) {
            if (!rc.canGetFlag(attackPoliNode.val)) {
                idsToRemove.add(attackPoliNode.val);
            }
            attackPoliNode = attackingPolis.next();
        }

        while (idsToRemove.size > 0) {
            Node<Integer> currIdToRemoveNode = idsToRemove.dequeue();
            attackingPolis.remove(currIdToRemoveNode.val);
        }

        // System.out.println(stageOfMultipartMessage + " - " + neutralECLocToTake + " -
        // " + neutralAttackingPolis.size);

        if (specialMessageQueue.size > 0) {
            int flag = specialMessageQueue.dequeue().val;
            if (flag != SKIP_FLAG) {
                setFlag(flag);
            }
            ;
            // if flag is -1, thenits a skip message meaning we can signal smth else

        }

        sendConstSignals();

        lastTurnInfluence = rc.getInfluence() - spentInfluence;// stuff we do

        if (!setFlagThisTurn) {
            rc.setFlag(0);
        }
    }

    private static boolean canStartNewMessageChain() {
        return stageOfMultipartMessage == 0;
    }

    private static void sendConstSignals() throws GameActionException {
        // signal rotation length
        int turnCountModulus = 2;
        if (enemyECLocs.size > 0) {
            turnCountModulus = 5;
        }
        System.out.println("turncountmod " + turnCountModulus);

        // send locations of enemy ECs
        if (!setFlagThisTurn && turnCount % turnCountModulus > 1 && enemyECLocs.size > 0) {

            if (haveMapDimensions()) {
                HashMapNodeVal<Integer, ECDetails> ecLocHashNode = enemyECLocs.next();
                if (ecLocHashNode == null) {
                    enemyECLocs.resetIterator();
                    ecLocHashNode = enemyECLocs.next();
                }
                MapLocation ECLoc = ecLocHashNode.val.location;
                System.out.println("Sending " + ECLoc);
                int signal = Comms.getFoundECSignal(ECLoc, TEAM_ENEMY, offsetx, offsety);
                setFlag(signal);
            }
        }

        // send map dimensions
        if (mapHeight >= 32 && mapWidth >= 32) {
            System.out.println("Map Details: Offsets: (" + offsetx + ", " + offsety + ") - Width: " + mapWidth
                    + " - Height: " + mapHeight);
            if (!setFlagThisTurn) {
                if (turnCount % turnCountModulus == 0) {
                    int sig = Comms.getMapOffsetSignalXWidth(offsetx, mapWidth);
                    setFlag(sig);
                } else if (turnCount % turnCountModulus == 1) {
                    int sig = Comms.getMapOffsetSignalYHeight(offsety, mapHeight);
                    setFlag(sig);
                }
            }
        }
    }

    private static void tryToBuildAnywhere(RobotType type, int influence) throws GameActionException {
        for (Direction dir : DIRECTIONS) {
            MapLocation buildLoc = rc.getLocation().add(dir);
            if (rc.canBuildRobot(type, dir, influence)) {
                rc.buildRobot(type, dir, influence);
                RobotInfo newbot = rc.senseRobotAtLocation(buildLoc);
                if (type == RobotType.POLITICIAN) {
                    politicianIDs.add(newbot.ID);
                } else if (type == RobotType.MUCKRAKER) {
                    muckrakerIDs.add(newbot.ID);
                } else if (type == RobotType.SLANDERER) {
                    politicianIDs.add(newbot.ID);
                }
                // setFlag(Comms.getPoliSacrificeSignal());
                lastBuildTurn = turnCount;
                break;
            }
        }
    }

    private static RobotInfo tryToBuildAnywhere(RobotType type, int influence, Direction preferredDir)
            throws GameActionException {
        // TODO: optimize, can hardcode this based on preferred dir
        Direction[] dirs = new Direction[] { preferredDir, preferredDir.rotateLeft(), preferredDir.rotateRight(),
                preferredDir.rotateLeft().rotateLeft(), preferredDir.rotateRight().rotateRight(),
                preferredDir.rotateLeft().rotateLeft().rotateLeft(),
                preferredDir.rotateRight().rotateRight().rotateRight(), preferredDir.opposite() };
        for (Direction dir : dirs) {
            MapLocation buildLoc = rc.getLocation().add(dir);
            if (rc.canBuildRobot(type, dir, influence)) {
                rc.buildRobot(type, dir, influence);
                RobotInfo newbot = rc.senseRobotAtLocation(buildLoc);
                if (type == RobotType.POLITICIAN) {
                    politicianIDs.add(newbot.ID);
                } else if (type == RobotType.MUCKRAKER) {
                    muckrakerIDs.add(newbot.ID);
                } else if (type == RobotType.SLANDERER) {
                    politicianIDs.add(newbot.ID);
                }
                // setFlag(Comms.getPoliSacrificeSignal());
                lastBuildTurn = turnCount;
                return newbot;
            }
        }
        return null;
    }

    private static void handleUnitChecks() throws GameActionException {
        ecIDs.resetIterator();
        muckrakerIDs.resetIterator();
        slandererIDs.resetIterator();
        politicianIDs.resetIterator();

        Node<Integer> currIDNode = muckrakerIDs.next();
        LinkedList<Integer> idsToRemove = new LinkedList<>();
        while (currIDNode != null) {
            if (Clock.getBytecodesLeft() < 8000) {
                break;
            }
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
                        break;
                    case Comms.FOUND_EC_LONG_HASH_RANGE:
                        processFoundECLongHashFlag(currIDNode.val, flag);
                        break;

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
                // clean out
                multiPartMessagesByBotID.remove(node.val);
            }
        }

        currIDNode = slandererIDs.next();
        while (currIDNode != null) {
            if (Clock.getBytecodesLeft() < 6000) {
                break;
            }
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
                multiPartMessagesByBotID.remove(node.val);
            }
        }

        currIDNode = politicianIDs.next();
        while (currIDNode != null) {
            if (Clock.getBytecodesLeft() < 4000) {
                break;
            }
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
                multiPartMessagesByBotID.remove(node.val);
            }
        }
    }
}
