package maxecosushi;

import battlecode.common.*;
import maxecosushi.utils.*;

import static maxecosushi.Constants.*;

public class EnlightmentCenter extends RobotPlayer {
    static int lastScoutBuildDirIndex = 0;
    static int lastRushBuildIndex = -1;
    static final int BUILD_SCOUTS = 0;
    static final int NORMAL = 1;
    static int role = NORMAL;

    static boolean sawFirstEnemyMuck = false;
    static boolean gotBuffed = false;

    static int highMapX = Integer.MIN_VALUE;
    static int highMapY = Integer.MIN_VALUE;

    static final int[] optimalSlandBuildVals = new int[] { 0, 21, 41, 63, 85, 107, 130, 154, 178, 203, 228, 255, 282,
            310, 339, 368, 399, 431, 463, 497, 532, 568, 605, 643, 683, 724, 766, 810, 855, 902, 949 };

    static HashTable<Integer> ecIDs = new HashTable<>(10);
    static HashTable<Integer> muckrakerIDs = new HashTable<>(120);
    static HashTable<Integer> slandererIDs = new HashTable<>(50);
    static HashTable<Integer> politicianIDs = new HashTable<>(40);
    static int lastBuildTurn = -1;

    static final int MIN_INF_NEEDED_TO_TAKE_NEUTRAL_EC = 550;

    static double minBidAmount = 2;
    static int lastTeamVotes = 0;
    static int lastTurnInfluence = GameConstants.INITIAL_ENLIGHTENMENT_CENTER_INFLUENCE;

    static HashTable<Integer> attackingPolis = new HashTable<>(20);
    static HashTable<Integer> attackingMucks = new HashTable<>(20);
    static int turnBuiltNeutralAttackingPoli = 0;

    static int multiPartMessageType = 0;
    static final int SKIP_FLAG = -1;
    static LinkedList<Integer> specialMessageQueue = new LinkedList<>();
    static int[] dirsToEnemySlands = new int[] { -1, -1, -1, -1, -1, -1, -1, -1 };
    static int[] dirsToEnemyPolis = new int[] { -1, -1, -1, -1, -1, -1, -1, -1 };

    static HashTable<Integer> locHashesOfCurrentlyAttackedNeutralECs = new HashTable<>(10);
    static int lastTurnSawEnemyMuck = -10;

    static int lastTurnBuiltMediumMuck = -10;

    static int buildPoliForBuffMuckId = -1;

    static int numberofattackingpolisneedingnewattacktarget = 0;

    static int timeToFirstEnemyMuck = -1;

    static class Stats {
        static int muckrakersBuilt = 0;
        static int politiciansBuilt = 0;
        static int slanderersBuilt = 0;
    }

    // 0 means like no slandereers are dying (few mucks attacking)
    static double threatFactor = 0;

    static int lastTurnBuiltBuffScoutMuck = -1;

    static LinkedList<Integer> bigEnemyMuckSizes;
    static LinkedList<MapLocation> bigEnemyMuckLocs;

    static HashMap<Integer, Integer> slanderersBuildTurn = new HashMap<>(100);

    static boolean firstEC = false;
    static int buildCount = 0;
    static LinkedList<Integer> buildScoutedDirs = new LinkedList<>();

    static int totalMoneyMakingSlanderersAlive = 0;

    public static void setup() throws GameActionException {
        // role = BUILD_SCOUTS;
        lastScoutBuildDirIndex = 0;
        ecIDs.add(rc.getID());

        if (rc.getRoundNum() < 2) {
            firstEC = true;
        }
        int[] order = new int[] { 1, 3, 5, 7, 0, 2, 4, 6 };
        for (int i : order) {
            buildScoutedDirs.add(i);
        }
    }

    public static void run() throws GameActionException {

        // fields that reset each turn and get manipulated by other functions lmao
        bigEnemyMuckSizes = new LinkedList<>();
        bigEnemyMuckLocs = new LinkedList<>();
        numberofattackingpolisneedingnewattacktarget = 0;
        totalMoneyMakingSlanderersAlive = 0;
        setFlagThisTurn = false;
        // how much influence is spent on building
        int spentInfluence = 0;

        int influenceGainedLastTurn = rc.getInfluence() - lastTurnInfluence;
        // determine if we won or lost/tied bid?
        if (!wonInVotes() && turnCount >= 100) {
            if (rc.getTeamVotes() > lastTeamVotes) {
                // won, lower the bid
                minBidAmount /= 1.25;
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
                if (rc.getInfluence() < 1000 && influenceGainedLastTurn > 0) {
                    minBidAmount = Math.min(minBidAmount, Math.ceil(influenceGainedLastTurn * factor));
                }
                // dont let ourselves bid insane amounts
                if (minBidAmount > rc.getInfluence() * 0.05) {
                    minBidAmount = Math.ceil(rc.getInfluence() * 0.025);
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
        System.out.println("My buff: " + rc.getEmpowerFactor(myTeam, 0) + " | Opp buff: "
                + rc.getEmpowerFactor(oppTeam, 0) + " | my buff in 10: " + rc.getEmpowerFactor(myTeam, 10) + " | money making slands: " + totalMoneyMakingSlanderersAlive);

        // strategy to take nearby neutral HQ asap
        boolean buildEarlyPoliticianToTakeNeutralHQ = false;
        // if enemy HQ is near
        boolean nearbyEnemyHQ = false;
        int enemyMuckrakersSeen = 0;
        int nearbyPolis = 0;
        // list of enemy mucks with larger inf

        RobotInfo[] nearbyBots = rc.senseNearbyRobots();

        int nearbyEnemyFirePower = 0;
        int nearbyEnemyPolis = 0;
        int nearbyAntiBuffMuckFirepower = 0;
        for (int i = nearbyBots.length; --i >= 0;) {
            RobotInfo bot = nearbyBots[i];
            if (bot.team == oppTeam) {
                if (bot.type == RobotType.POLITICIAN) {
                    nearbyEnemyPolis += 1;
                    nearbyEnemyFirePower += bot.conviction;
                } else if (bot.type == RobotType.MUCKRAKER) {
                    enemyMuckrakersSeen += 1;
                    lastTurnSawEnemyMuck = turnCount;
                    if (bot.conviction >= 30) {
                        bigEnemyMuckSizes.add(bot.conviction);
                        bigEnemyMuckLocs.add(bot.location);
                    }
                }
            } else if (bot.team == myTeam) {
                if (bot.type == RobotType.POLITICIAN) {
                    nearbyPolis += 1;
                    if (rc.getFlag(bot.ID) == Comms.IM_STOPPING_BUFF_MUCK) {
                        // TODO: perhaps more accurately calcultae this based on the buff value in the
                        // future?
                        // concern, would need to be conservative with how near future we calc buff for.
                        // probably want to use maybe 40 turns before we expect a spawned anti buff muck
                        // poli to actually be used maybe
                        nearbyAntiBuffMuckFirepower += (bot.conviction - GameConstants.EMPOWER_TAX);
                    }
                }
            }
        }

        // initial build
        if (firstEC && rc.isReady()) {
            if (buildCount == 0) {
                tryToBuildAnywhere(RobotType.SLANDERER, 130);
                buildCount += 1;
            } else if (buildCount <= 20) {
                if (buildCount == 7) {
                    RobotInfo bot = tryToBuildAnywhere(RobotType.POLITICIAN, 16);
                    if (bot != null) {
                        buildCount += 1;
                    }
                }
                if (buildCount % 2 == 0) {
                    tryToBuildAnywhere(RobotType.SLANDERER, findOptimalSlandererInfluenceUnderX(rc.getInfluence()));
                    buildCount += 1;
                } else {
                    lastRushBuildIndex = (lastRushBuildIndex + 1) % DIRECTIONS.length;
                    Direction dir = DIRECTIONS[lastRushBuildIndex];

                    boolean built = false;
                    if (buildScoutedDirs.size > 0) {
                        Node<Integer> node = buildScoutedDirs.head;
                        while (node != null) {
                            if (rc.canBuildRobot(RobotType.MUCKRAKER, DIRECTIONS[node.val], 1)) {
                                rc.buildRobot(RobotType.MUCKRAKER, DIRECTIONS[node.val], 1);
                                buildScoutedDirs.remove(node.val);
                                buildCount += 1;
                                specialMessageQueue.add(SKIP_FLAG);
                                specialMessageQueue.add(Comms.GO_SCOUT);
                                RobotInfo bot = rc.senseRobotAtLocation(rc.getLocation().add(DIRECTIONS[node.val]));
                                muckrakerIDs.add(bot.ID);
                                built = true;
                                lastBuildTurn = turnCount;
                                break;
                            }
                            node = node.next;
                        }
                    }
                    if (!built) {
                        RobotInfo bot = tryToBuildAnywhere(RobotType.MUCKRAKER, 1, dir);
                        if (bot != null) {
                            buildCount += 1;
                            if (turnCount <= 40) {
                                specialMessageQueue.add(SKIP_FLAG);
                                specialMessageQueue.add(Comms.GO_SCOUT);
                            }
                        }
                    }

                }
            }
        }

        int allowance = rc.getInfluence() - nearbyEnemyFirePower;
        // strategy for taking ecs
        boolean stockInfluenceForNeutral = false;
        /** closest neutral ec loc */
        ECDetails neutralECLocToTake = null;
        int bestScore = 9999999;
        neutralECLocs.resetIterator();
        System.out.println("There are " + neutralECLocs.size + " neutral ECs - " + enemyECLocs.size + " enemy ECs ");
        HashMapNodeVal<Integer, ECDetails> neutralHashNode = neutralECLocs.next();
        while (neutralHashNode != null) {
            MapLocation loc = neutralHashNode.val.location;
            int hash = Comms.encodeMapLocation(loc);
            if (!locHashesOfCurrentlyAttackedNeutralECs.contains(hash)) {
                int dist = rc.getLocation().distanceSquaredTo(neutralHashNode.val.location);
                int score = dist + neutralHashNode.val.lastKnownConviction * 100;
                if (bestScore > score) {
                    bestScore = score;
                    neutralECLocToTake = neutralHashNode.val;
                }
            }
            neutralHashNode = neutralECLocs.next();
        }
        if (neutralECLocToTake != null) {
            if (allowance >= neutralECLocToTake.lastKnownConviction + 120) {

            } else {
                stockInfluenceForNeutral = true;
            }
        }

        ECDetails enemyECLocToTake = null;
        int closestDist = 99999999;
        ECDetails closestEnemyEC = null;
        int closestEnemyECDist = 9999999;
        enemyECLocs.resetIterator();
        HashMapNodeVal<Integer, ECDetails> enemyHashNode = enemyECLocs.next();
        while (enemyHashNode != null) {
            MapLocation loc = enemyHashNode.val.location;
            int hash = Comms.encodeMapLocation(loc);
            // if (!locHashesOfCurrentlyAttackedNeutralECs.contains(hash)) {

            int dist = loc.distanceSquaredTo(rc.getLocation());
            // System.out.println("Enemy ec " + enemyHashNode.val.location);
            if (dist < closestDist) {
                closestDist = dist;
                enemyECLocToTake = enemyHashNode.val;
            }
            if (dist < closestEnemyECDist) {
                closestEnemyEC = enemyHashNode.val;
                closestEnemyECDist = dist;
            }
            // }
            enemyHashNode = enemyECLocs.next();
        }

        switch (role) {
            case BUILD_SCOUTS:
                break;
            case NORMAL:

                // otherwise spam muckrakers wherever possible and ocassionally build slanderers
                boolean buildSlanderer = false;
                if (muckrakerIDs.size / (slandererIDs.size + 0.1) > 0.5 || turnCount <= 2) {
                    buildSlanderer = true;
                }
                if (enemyMuckrakersSeen > 0) {
                    if (!sawFirstEnemyMuck) {
                        timeToFirstEnemyMuck = turnCount;
                    }
                    sawFirstEnemyMuck = true;
                    buildSlanderer = false;
                }
                boolean buildPoli = false;
                double ratiocap = 0.8;
                double distToClosestEnemyEC = 99999;

                if (closestEnemyEC != null) {

                    distToClosestEnemyEC = closestEnemyEC.location.distanceSquaredTo(rc.getLocation());
                    if (distToClosestEnemyEC <= 155) {
                        ratiocap = 0.25;
                    } else if (distToClosestEnemyEC <= 200) {
                        ratiocap = 0.3;
                    } else if (distToClosestEnemyEC <= 425) {
                        ratiocap = 0.4;
                    } else {
                        ratiocap = 0.8;
                    }
                } else {
                    // use this metric if closest enemy ec not known
                    if (timeToFirstEnemyMuck != -1 && timeToFirstEnemyMuck <= 100) {
                        ratiocap = 0.25;
                    } else {
                        ratiocap = 0.8;
                    }
                }

                double ratio = Math.max(0, ratiocap *0.8 - threatFactor * 0.8);
                if (rc.getRoundNum() < 100 && !sawFirstEnemyMuck) {
                    ratio = ratiocap * 2 - threatFactor * 1.5;
                }
                if (rc.getRoundNum() % 10 == 0) {
                    // every 10 turns reduce threat
                    threatFactor = Math.max(0, (threatFactor - 0.075) * 0.9);
                }

                System.out.println("pratio - " + ratio + " - ratio cap " + ratiocap);
                double comparesize = politicianIDs.size + 0.1;
                comparesize = Math.max(comparesize, 0.1);
                if (totalMoneyMakingSlanderersAlive / (comparesize) > ratio) {
                    buildPoli = true;
                } else {
                    buildSlanderer = true;
                }
                if (enemyMuckrakersSeen > nearbyPolis) {
                    buildPoli = true;
                }

                // if we have this much influence and we're trying to build slanderers, nope,
                // build polis, slanderers wont really help ...
                if (rc.getInfluence() >= 250000 && buildSlanderer == true) {
                    buildSlanderer = false;
                    buildPoli = true;
                }
                // try and maintain this much influence, don't spend it on giant poli units
                if (rc.getInfluence() >= 200000) {
                    gotBuffed = true;
                }
                if (rc.getInfluence() < 4000) {
                    gotBuffed = false;
                }
                // dont build slanderers, spam more polis if we recently saw a muckraker

                if (turnCount - lastTurnSawEnemyMuck < 8) {
                    buildPoli = true;
                }

                boolean considerAttackingEnemy = false;

                boolean bigEnemyMucksToDealWith = false;
                int firePowerLeftToFightAntiBuffMuck = nearbyAntiBuffMuckFirepower;
                int antiBuffMuckPoliSizeNeeded = -1;
                if (bigEnemyMuckSizes.size > 0) {
                    Node<Integer> node = bigEnemyMuckSizes.head;
                    while (node != null) {
                        if (firePowerLeftToFightAntiBuffMuck - node.val > 0) {
                            firePowerLeftToFightAntiBuffMuck -= node.val;
                        } else {
                            break;
                        }
                        node = node.next;
                    }
                    // all nodes left are muck sizes that are not dealt with
                    if (node != null) {
                        antiBuffMuckPoliSizeNeeded = node.val + GameConstants.EMPOWER_TAX;
                    }
                    if (antiBuffMuckPoliSizeNeeded != -1) {
                        bigEnemyMucksToDealWith = true;
                    }
                }

                System.out.println("antiBuffMuckPoliSizeNeeded: " + antiBuffMuckPoliSizeNeeded);
                // if theres a enemy muck to deal with and we have an allowance of at least 20,
                // spawn a poli to defend!
                if (bigEnemyMucksToDealWith && allowance >= 20) {
                    int size = (int) Math.min(((double) antiBuffMuckPoliSizeNeeded) / 0.7, (double) allowance);
                    // TODO: spawn in direction of that buff muck
                    RobotInfo newbot = tryToBuildAnywhere(RobotType.POLITICIAN, size);
                    if (newbot != null) {
                        spentInfluence += size;
                        break;
                    }
                }

                // if we built buff muck last time and want accompanying poli and we still have
                // the money
                if (buildPoliForBuffMuckId != -1 && allowance >= 20) {
                    RobotInfo newbot = tryToBuildAnywhere(RobotType.POLITICIAN, allowance);
                    if (newbot != null) {
                        spentInfluence += allowance;
                        specialMessageQueue.add(SKIP_FLAG);
                        int sig = Comms.getProtectBuffMuckSignal(buildPoliForBuffMuckId);
                        specialMessageQueue.add(sig);

                        // reset for future
                        buildPoliForBuffMuckId = -1;
                        break;
                    }
                }

                if ((allowance >= 300 && influenceGainedLastTurn * 10 >= allowance)
                        || (allowance >= 300 && influenceGainedLastTurn * 5 >= allowance) || allowance >= 900) {
                    if (!stockInfluenceForNeutral) {
                        considerAttackingEnemy = true;
                    }
                }

                System.out.println("Consider attack: " + considerAttackingEnemy + " | Neutral to take "
                        + (neutralECLocToTake != null ? neutralECLocToTake.location : null) + " | stock? "
                        + stockInfluenceForNeutral);

                // capture netural ECs
                // lower threshold for generation per turn as time progresses
                int extraInfFactor = 8;
                if (rc.getRoundNum() <= 100) {

                } else if (rc.getRoundNum() <= 200) {
                    extraInfFactor = 15;
                } else if (rc.getRoundNum() <= 300) {
                    extraInfFactor = 30;
                } else {
                    extraInfFactor = 10000;
                }
                if (neutralECLocToTake != null && allowance >= neutralECLocToTake.lastKnownConviction + 120
                        && influenceGainedLastTurn * extraInfFactor >= neutralECLocToTake.lastKnownConviction + 120) {
                    int hash = Comms.encodeMapLocation(neutralECLocToTake.location);
                    // limit ourselves to send only one poli per neutral
                    if (!locHashesOfCurrentlyAttackedNeutralECs.contains(hash)) {
                        // TODO: dont do this if enemy is near and can capture easily
                        int want = neutralECLocToTake.lastKnownConviction + 120;
                        RobotInfo newbot = tryToBuildAnywhere(RobotType.POLITICIAN, want,
                                rc.getLocation().directionTo(neutralECLocToTake.location));
                        if (newbot != null) {
                            attackingPolis.add(newbot.ID);
                            turnBuiltNeutralAttackingPoli = turnCount;
                            int sig1 = Comms.getAttackNeutralECSignal(neutralECLocToTake.location);
                            specialMessageQueue.add(SKIP_FLAG);
                            specialMessageQueue.add(sig1);
                            locHashesOfCurrentlyAttackedNeutralECs.add(hash);
                            spentInfluence += want;
                            break;
                        }
                    }
                }
                // spawn buff muck
                // and then set ec to try and build a buff poli to heal or stop buff enemy polis
                if (enemyECLocToTake != null && considerAttackingEnemy
                        && attackingMucks.size < attackingPolis.size / 2) {
                    // int want = Math.min(allowance, allowance / 2 + (int) (influenceGainedLastTurn
                    // * 1.8));
                    int want = allowance;
                    RobotInfo newbot = tryToBuildAnywhere(RobotType.MUCKRAKER, want,
                            rc.getLocation().directionTo(enemyECLocToTake.location));
                    if (newbot != null) {
                        attackingMucks.add(newbot.ID);
                        turnBuiltNeutralAttackingPoli = turnCount;
                        int sig1 = Comms.getAttackECSignal(enemyECLocToTake.location);
                        specialMessageQueue.add(SKIP_FLAG);
                        specialMessageQueue.add(sig1);
                        spentInfluence += want;
                        // buildPoliForBuffMuckId = newbot.ID;
                        break;
                    }
                }

                // try and take enemy EC loc if we have 300 inf and if 10 times the inf / turn
                // >= what we have now
                else if (enemyECLocToTake != null && considerAttackingEnemy) {
                    int want = allowance;
                    want = (int) Math.min(want, MAX_INF_PER_ROBOT * 0.25);

                    // if we got buffed, reduce allowance significantly
                    if (gotBuffed) {
                        want = Math.min(allowance, 1500);
                    }
                    RobotInfo newbot = tryToBuildAnywhere(RobotType.POLITICIAN, want,
                            rc.getLocation().directionTo(enemyECLocToTake.location));
                    if (newbot != null) {
                        attackingPolis.add(newbot.ID);
                        turnBuiltNeutralAttackingPoli = turnCount;
                        int sig1 = Comms.getAttackECSignal(enemyECLocToTake.location);
                        specialMessageQueue.add(SKIP_FLAG);
                        specialMessageQueue.add(sig1);
                        spentInfluence += want;
                        break;
                    }
                }

                // tell polis to attack new place if theyre all just milling around
                if (numberofattackingpolisneedingnewattacktarget > attackingPolis.size / 2
                        && enemyECLocToTake != null) {
                    int sig1 = Comms.getAttackECSignal(enemyECLocToTake.location);
                    specialMessageQueue.add(sig1);
                }

                // build scouting buff mucks that "flank"
                if (neutralECLocToTake == null && enemyECLocToTake == null
                        && turnCount > lastTurnBuiltBuffScoutMuck + 15) {
                    // consider spawning somewhat buff scout mucks
                    if (allowance >= 450) {
                        int want = (int) (allowance * 0.25);
                        // if heavily stocked and nothing to spend on (not even 949 slanderer), build
                        // even bigger muck then
                        if (allowance > 1000) {
                            want = (int) (allowance * 0.5);
                        }

                        Direction scoutDir = CARD_DIRECTIONS[lastScoutBuildDirIndex];
                        // first choose from promising directions

                        boolean usedPromisingDir = false;
                        for (int i = 0; i < dirsToEnemySlands.length; i++) {
                            if (dirsToEnemySlands[i] != -1) {
                                // promising if not default sentinel val of -1
                                Direction dir = DIRECTIONS[i];
                                scoutDir = dir;
                                dirsToEnemySlands[i] = -1;
                                usedPromisingDir = true;
                                break;
                            }

                        }
                        // if havvent found promisig direction, use these 2nd rate guesses
                        if (!usedPromisingDir) {
                            for (int i = 0; i < dirsToEnemyPolis.length; i++) {
                                if (dirsToEnemyPolis[i] != -1) {
                                    // promising if not default sentinel val of -1
                                    Direction dir = DIRECTIONS[i];
                                    scoutDir = dir;
                                    dirsToEnemyPolis[i] = -1;
                                    usedPromisingDir = true;
                                    break;
                                }

                            }
                        }
                        // scout directions that arent off map / we can see
                        while (!rc.onTheMap(rc.getLocation().add(scoutDir).add(scoutDir).add(scoutDir).add(scoutDir))) {
                            scoutDir = CARD_DIRECTIONS[lastScoutBuildDirIndex];
                            lastScoutBuildDirIndex = (lastScoutBuildDirIndex + 1) % CARD_DIRECTIONS.length;
                        }
                        RobotInfo newbot = tryToBuildAnywhere(RobotType.MUCKRAKER, want, scoutDir);
                        if (newbot != null) {
                            lastScoutBuildDirIndex = (lastScoutBuildDirIndex + 1) % CARD_DIRECTIONS.length;
                            lastTurnBuiltBuffScoutMuck = turnCount;
                            specialMessageQueue.add(SKIP_FLAG);
                            switch (scoutDir) {
                                case NORTH:
                                    specialMessageQueue.add(Comms.GO_SCOUT_NORTH);
                                    break;
                                case NORTHEAST:
                                    specialMessageQueue.add(Comms.GO_SCOUT_NORTHEAST);
                                    break;
                                case EAST:
                                    specialMessageQueue.add(Comms.GO_SCOUT_EAST);
                                    break;
                                case SOUTHEAST:
                                    specialMessageQueue.add(Comms.GO_SCOUT_SOUTHEAST);
                                    break;
                                case SOUTH:
                                    specialMessageQueue.add(Comms.GO_SCOUT_SOUTH);
                                    break;
                                case SOUTHWEST:
                                    specialMessageQueue.add(Comms.GO_SCOUT_SOUTHWEST);
                                    break;
                                case WEST:
                                    specialMessageQueue.add(Comms.GO_SCOUT_WEST);
                                    break;
                                case NORTHWEST:
                                    specialMessageQueue.add(Comms.GO_SCOUT_NORTHWEST);
                                    break;
                                default:
                                    // shouldn't happen
                                    specialMessageQueue.add(Comms.GO_SCOUT);
                                    break;
                            }
                            spentInfluence += want;
                            break;
                        } else if (usedPromisingDir) {
                            // if used primising direction but didnt build anything, reset val
                            int d = dirToInt(scoutDir);
                            dirsToEnemySlands[d] = 0;
                        }
                    }
                }

                lastRushBuildIndex = (lastRushBuildIndex + 1) % DIRECTIONS.length;
                Direction dir = DIRECTIONS[lastRushBuildIndex];

                if (buildPoli && allowance >= 20) {
                    int influenceWant = 20;
                    RobotInfo newbot = tryToBuildAnywhere(RobotType.POLITICIAN, influenceWant, dir);
                    if (newbot != null) {
                        spentInfluence += influenceWant;
                        break;
                    }
                }

                if (buildSlanderer && allowance >= 41) {
                    // TODO: cap size of slanderer based on nearby power

                    int want = findOptimalSlandererInfluenceUnderX(allowance);
                    RobotInfo newbot = tryToBuildAnywhere(RobotType.SLANDERER, want, dir);
                    if (newbot != null) {
                        spentInfluence += want;
                        break;
                    }
                }
                int mucksize = 1;
                if (allowance <= 100) {

                } else if (allowance <= 200) {
                    mucksize = 2;
                } else if (allowance <= 300) {
                    mucksize = 3;
                } else if (allowance >= 400) {
                    mucksize = allowance / 100;
                }
                if (turnCount - lastTurnBuiltMediumMuck > 30) {

                }

                RobotInfo newbot = tryToBuildAnywhere(RobotType.MUCKRAKER, mucksize, dir);
                if (newbot != null) {
                    spentInfluence += 1;
                    break;
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
            } else {
                int flag = rc.getFlag(attackPoliNode.val);

            }
            attackPoliNode = attackingPolis.next();
        }

        while (idsToRemove.size > 0) {
            Node<Integer> currIdToRemoveNode = idsToRemove.dequeue();
            attackingPolis.remove(currIdToRemoveNode.val);
        }

        if (specialMessageQueue.size > 0) {
            int flag = specialMessageQueue.dequeue().val;
            if (flag != SKIP_FLAG) {
                setFlag(flag);
            }
            // if flag is -1, thenits a skip message meaning we can signal smth else

        }

        sendConstSignals();

        lastTurnInfluence = rc.getInfluence() - spentInfluence;// stuff we do

        if (!setFlagThisTurn) {
            rc.setFlag(0);
        }
    }

    private static void sendConstSignals() throws GameActionException {
        // signal rotation length
        int turnCountModulus = 2;
        if (enemyECLocs.size > 0) {
            turnCountModulus = 5;
        }

        // send locations of changed ECs
        if (!setFlagThisTurn && recentlyAddedEnemyECHashes.size > 0) {
            Node<Integer> hash = recentlyAddedEnemyECHashes.dequeue();
            ECDetails ecDetails = enemyECLocs.get(hash.val);
            if (ecDetails != null) {
                int signal = Comms.getFoundECSignal(ecDetails.location, TEAM_ENEMY, ecDetails.lastKnownConviction);
                setFlag(signal);
            }
        }

        if (!setFlagThisTurn && recentlyAddedFriendECHashes.size > 0) {
            Node<Integer> hash = recentlyAddedFriendECHashes.dequeue();
            ECDetails ecDetails = enemyECLocs.get(hash.val);
            if (ecDetails != null) {
                int signal = Comms.getFoundECSignal(ecDetails.location, TEAM_FRIEND, ecDetails.lastKnownConviction);
                setFlag(signal);

            }
        }

        // send locations of enemy ECs then our own Ecs
        if (!setFlagThisTurn && enemyECLocs.size > 0) {

            HashMapNodeVal<Integer, ECDetails> ecLocHashNode = enemyECLocs.next();
            if (ecLocHashNode == null) {
                enemyECLocs.resetIterator();
                ecLocHashNode = enemyECLocs.next();
            }
            MapLocation ECLoc = ecLocHashNode.val.location;
            int signal = Comms.getFoundECSignal(ECLoc, TEAM_ENEMY, ecLocHashNode.val.lastKnownConviction);
            setFlag(signal);
        }

        // send map dimensions
        // if (mapHeight >= 32 && mapWidth >= 32) {
        // System.out.println("Map Details: Offsets: (" + offsetx + ", " + offsety + ")
        // - Width: " + mapWidth
        // + " - Height: " + mapHeight);
        // if (!setFlagThisTurn) {
        // if (turnCount % turnCountModulus == 0) {
        // int sig = Comms.getMapOffsetSignalXWidth(offsetx, mapWidth);
        // setFlag(sig);
        // } else if (turnCount % turnCountModulus == 1) {
        // int sig = Comms.getMapOffsetSignalYHeight(offsety, mapHeight);
        // setFlag(sig);
        // }
        // }
        // }
    }

    private static RobotInfo tryToBuildAnywhere(RobotType type, int influence) throws GameActionException {
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
                    slandererIDs.add(newbot.ID);
                    if (newbot != null) {
                        slanderersBuildTurn.put(newbot.ID, rc.getRoundNum());
                    }
                }
                lastBuildTurn = turnCount;
                
                return newbot;
            }
        }
        return null;
    }

    private static RobotInfo tryToBuildAnywhere(RobotType type, int influence, Direction preferredDir)
            throws GameActionException {
        // TODO: optimize, can hardcode this based on preferred dir
        Direction[] dirs = new Direction[] { preferredDir, preferredDir.rotateLeft(), preferredDir.rotateRight(),
                preferredDir.rotateLeft().rotateLeft(), preferredDir.rotateRight().rotateRight(),
                preferredDir.rotateLeft().rotateLeft().rotateLeft(),
                preferredDir.rotateRight().rotateRight().rotateRight(), preferredDir.opposite() };
        // Direction dirAvoidingSacrificeDirs = null;
        Direction chosenDir = null;
        for (Direction dir : dirs) {
            // MapLocation buildLoc = rc.getLocation().add(dir);
            if (rc.canBuildRobot(type, dir, influence)) {
                // code that doesn't reallyy work to avoid interfering with sacrificing
                // politicians
                // int ind = dirToInt(dir);
                // if (turnCount - SacrificePoliBuildDirToTurnCount[ind] != 10) {
                // System.out.println("dir " + dir + " - avoids sacrificing poli");
                // dirAvoidingSacrificeDirs = dir;
                // break;
                // }
                if (chosenDir != null) {
                    continue;
                }
                chosenDir = dir;
            }
        }
        // if (dirAvoidingSacrificeDirs != null) {
        // chosenDir = dirAvoidingSacrificeDirs;
        // }
        if (chosenDir != null) {
            rc.buildRobot(type, chosenDir, influence);
            RobotInfo newbot = rc.senseRobotAtLocation(rc.getLocation().add(chosenDir));
            if (type == RobotType.POLITICIAN) {
                politicianIDs.add(newbot.ID);
            } else if (type == RobotType.MUCKRAKER) {
                muckrakerIDs.add(newbot.ID);
            } else if (type == RobotType.SLANDERER) {
                slandererIDs.add(newbot.ID);
                if (newbot != null) {
                    slanderersBuildTurn.put(newbot.ID, rc.getRoundNum());
                }
            }
            lastBuildTurn = turnCount;
            return newbot;
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
            if (Clock.getBytecodesLeft() < 10000) {
                System.out.println("==== quit muck loop early ====");
                break;
            }
            if (rc.canGetFlag(currIDNode.val)) {
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
                    case Comms.SPOTTED_MUCK: {
                        int[] data = Comms.readSpottedMuckSignal(flag, rc);
                        MapLocation muckloc = Comms.decodeMapLocation(data[0], rc);
                        if (rc.getLocation().distanceSquaredTo(muckloc) <= 200) {
                            bigEnemyMuckSizes.add(data[1]);
                            bigEnemyMuckLocs.add(muckloc);
                        }
                        // System.out.println("found muck by muck at " + muckloc + " size: " + data[1]);
                        break;
                    }
                    case Comms.FOUND_ENEMY_SLANDERER: {
                        int[] data = Comms.readFoundEnemyUnitSignal(flag, rc);
                        MapLocation unitLoc = Comms.decodeMapLocation(data[0], rc);
                        Direction dir = rc.getLocation().directionTo(unitLoc);
                        int ind = dirToInt(dir);
                        if (data[1] == TYPE_POLITICIAN) {
                            dirsToEnemyPolis[ind] = 0;
                        } else if (data[1] == TYPE_SLANDERER) {
                            dirsToEnemySlands[ind] = 0;
                        }

                        break;
                    }

                }
            } else {
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
            if (Clock.getBytecodesLeft() < 8000) {
                System.out.println("==== quit sland loop early ====");
                break;
            }
            if (rc.canGetFlag(currIDNode.val)) {

                int flag = rc.getFlag(currIDNode.val);
                if (flag == Comms.IM_SLAND_CONVERTED_TO_POLI) {
                    // check if switched to politican, and remove/add appropriately
                    int[] data = Comms.readUnitDetails(flag);
                    if (data[0] == TYPE_POLITICIAN) {
                        idsToRemove.add(currIDNode.val);
                        politicianIDs.add(currIDNode.val);
                    }
                } else {
                    // if slanderer, check their build turn and remove from this set from
                    // computation
                    Integer buildTurn = slanderersBuildTurn.get(currIDNode.val);
                    if (buildTurn != null) {
                        System.out.println(currIDNode.val + " - built " + buildTurn);
                        if (rc.getRoundNum() - buildTurn < 50) {
                            totalMoneyMakingSlanderersAlive += 1;
                        } else {
                            slanderersBuildTurn.remove(currIDNode.val);
                        }
                    }
                }
            } else {
                threatFactor = (threatFactor + .1) * 0.9;
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
            if (Clock.getBytecodesLeft() < 6000) {
                System.out.println("==== quit poli loop early ====");
                break;
            }
            if (rc.canGetFlag(currIDNode.val)) {
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
                    case Comms.SMALL_SIGNAL: {
                        if (flag == Comms.I_NEED_EC_ATTACK_LOC) {
                            numberofattackingpolisneedingnewattacktarget += 1;
                        }
                    }

                }
            } else {
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

    public static int findOptimalSlandererInfluenceUnderX(int x) {
        int low = 0;
        int high = optimalSlandBuildVals.length;
        while (low < high) {
            int mid = (low + high) / 2;
            if (optimalSlandBuildVals[mid] > x) {
                high = mid;
            } else if (optimalSlandBuildVals[mid] < x) {
                low = mid + 1;
            } else {
                return x;
            }
        }
        return optimalSlandBuildVals[low - 1];
    };
}
