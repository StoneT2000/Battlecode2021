package tunaroll;

import battlecode.common.*;
import tunaroll.utils.*;

import static tunaroll.Constants.*;

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


    static HashTable<Integer> attackingPolis = new HashTable<>(10);
    static int turnBuiltNeutralAttackingPoli = 0;

    static int multiPartMessageType = 0;
    static final int SKIP_FLAG = -1;
    static LinkedList<Integer> specialMessageQueue = new LinkedList<>();

    static HashTable<Integer> locHashesOfCurrentlyAttackedNeutralECs = new HashTable<>(10);

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
                if (rc.getInfluence() < 1000 && influenceGainedLastTurn > 0) {
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
        int enemyMuckrakersSeen = 0;
        int nearbyPolis = 0;

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
                enemyMuckrakersSeen += 1;
            } else if (bot.team == myTeam && bot.type == RobotType.POLITICIAN) {
                nearbyPolis += 1;
            }
        }
        if (turnCount == 1 && enemyMuckrakersSeen == 0 && rc.getInfluence() >= 150) {
            tryToBuildAnywhere(RobotType.SLANDERER, rc.getInfluence() - rc.getInfluence() % 40 + 1);
        }

        // strategy for taking ecs
        boolean stockInfluence = false;
        /** closest neutral ec loc */
        ECDetails neutralECLocToTake = null;
        int closestDist = 99999999;
        neutralECLocs.resetIterator();
        System.out.println("There are " + neutralECLocs.size + " neutral ECs - " + enemyECLocs.size + " enemy ECs ");
        HashMapNodeVal<Integer, ECDetails> neutralHashNode = neutralECLocs.next();
        while (neutralHashNode != null) {
            MapLocation loc = neutralHashNode.val.location;
            int hash = Comms.encodeMapLocation(loc);
            if (!locHashesOfCurrentlyAttackedNeutralECs.contains(hash)) {
                    
                int dist = loc.distanceSquaredTo(rc.getLocation());
                if (dist < closestDist) {
                    closestDist = dist;
                    neutralECLocToTake = neutralHashNode.val;
                }
            }
            neutralHashNode = neutralECLocs.next();
        }
        if (neutralECLocToTake != null) {
            if (rc.getInfluence() >= MIN_INF_NEEDED_TO_TAKE_NEUTRAL_EC) {

            } else {
                stockInfluence = true;
            }
        }

        ECDetails enemyECLocToTake = null;
        closestDist = 99999999;
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
            // }
            enemyHashNode = enemyECLocs.next();
        }

        switch (role) {
            case BUILD_SCOUTS:
                // TODO: handle edge cases if diagonals are blocked
                if (lastScoutBuildDirIndex <= 2 && rc.isReady()) {
                    lastScoutBuildDirIndex = (lastScoutBuildDirIndex + 1);
                    Direction dir = DIAG_DIRECTIONS[lastScoutBuildDirIndex];
                    

                    Direction checkDir = dir;
                    int i = 0;
                    while(i < 9) {
                        if (rc.canBuildRobot(RobotType.MUCKRAKER, checkDir, 1)) {
                            break;
                        }
                        checkDir = checkDir.rotateLeft();
                        i++;
                    }
                    if (rc.canBuildRobot(RobotType.MUCKRAKER, checkDir, 1)) {
                        // flag of 0 is default no signal value flag of 1-4 represents build direction
                        specialMessageQueue.add(SKIP_FLAG);
                        specialMessageQueue.add(Comms.GO_SCOUT);
                        rc.buildRobot(RobotType.MUCKRAKER, checkDir, 1);
                        Stats.muckrakersBuilt += 1;
                        
                        // add new id
                        MapLocation buildLoc = rc.getLocation().add(checkDir);
                        RobotInfo newbot = rc.senseRobotAtLocation(buildLoc);
                        muckrakerIDs.add(newbot.ID);
                    }
                }
                if (lastScoutBuildDirIndex == 3) {
                    role = NORMAL;
                }
                break;
            case NORMAL:
                // generate infinite influence
                if (calculatePoliticianEmpowerConviction(myTeam, rc.getConviction(), 10) / 6 > rc.getConviction() * 2
                        && rc.getInfluence() < MAX_INF_PER_ROBOT * 0.9) {
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
                } else if (neutralECLocToTake != null && rc.getInfluence() >= neutralECLocToTake.lastKnownConviction + 20) {
                    int hash = Comms.encodeMapLocation(neutralECLocToTake.location);
                    if (!locHashesOfCurrentlyAttackedNeutralECs.contains(hash)) {
                        // TODO: dont do this if enemy is near and can capture easily
                        int want = neutralECLocToTake.lastKnownConviction + 20;
                        RobotInfo newbot = tryToBuildAnywhere(RobotType.POLITICIAN, want,
                                rc.getLocation().directionTo(neutralECLocToTake.location));
                        if (newbot != null) {
                            attackingPolis.add(newbot.ID);
                            politicianIDs.add(newbot.ID);
                            turnBuiltNeutralAttackingPoli = turnCount;
                            int sig1 = Comms.getAttackECSignal(neutralECLocToTake.location);
                            specialMessageQueue.add(SKIP_FLAG);
                            specialMessageQueue.add(sig1);
                            locHashesOfCurrentlyAttackedNeutralECs.add(hash);
                        }
                    }
                }
                // proceed with generic building
                else {

                    // otherwise spam muckrakers wherever possible and ocassionally build slanderers
                    boolean buildSlanderer = false;
                    if (muckrakerIDs.size / (slandererIDs.size + 0.1) > 0.5 || turnCount <= 2) {
                        buildSlanderer = true;
                    }
                    if (enemyMuckrakersSeen > 0) {
                        buildSlanderer = false;
                    }
                    boolean buildPoli = false;

                    if (slandererIDs.size / (politicianIDs.size + 0.1) > 1) {
                        buildPoli = true;
                    }
                    if (enemyMuckrakersSeen > nearbyPolis) {
                        buildPoli = true;
                    }
                    
                    boolean considerAttackingEnemy = false;
                    if ((rc.getInfluence() >= 300 && influenceGainedLastTurn * 10 >= rc.getInfluence()) || rc.getInfluence() >= 900) {
                        // if our inf is at 80% of maximum, we're winning anyway
                        considerAttackingEnemy = true;
                    }
                    
                    // try and take enemy EC loc if we have 300 inf and if 10 times the inf / turn >= what we have now
                    if (enemyECLocToTake != null && considerAttackingEnemy) {
                        int want = Math.min(rc.getInfluence() - 40, rc.getInfluence() - 40);
                        want = (int) Math.min(want, MAX_INF_PER_ROBOT * 0.25);
                        RobotInfo newbot = tryToBuildAnywhere(RobotType.POLITICIAN, want,
                            rc.getLocation().directionTo(enemyECLocToTake.location));
                        if (newbot != null) {
                            attackingPolis.add(newbot.ID);
                            politicianIDs.add(newbot.ID);
                            turnBuiltNeutralAttackingPoli = turnCount;
                            int sig1 = Comms.getAttackECSignal(enemyECLocToTake.location);
                            specialMessageQueue.add(SKIP_FLAG);
                            specialMessageQueue.add(sig1);
                            break;
                        }
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
                        if (buildSlanderer && rc.getInfluence() >= 21) {
                            int want = Math.min(rc.getInfluence() - rc.getInfluence() % 20 + 1, 141);
                            if (rc.canBuildRobot(RobotType.SLANDERER, dir, want)) {
                                rc.buildRobot(RobotType.SLANDERER, dir, want);
                                RobotInfo newbot = rc.senseRobotAtLocation(buildLoc);
                                slandererIDs.add(newbot.ID);
                                lastBuildTurn = turnCount;
                                spentInfluence += want;
                                break;
                            }
                        }
                        else {
                            int influenceWant = 1;
                            if (rc.canBuildRobot(RobotType.MUCKRAKER, dir, influenceWant)) {
                                rc.buildRobot(RobotType.MUCKRAKER, dir, influenceWant);
                                RobotInfo newbot = rc.senseRobotAtLocation(buildLoc);
                                muckrakerIDs.add(newbot.ID);
                                lastBuildTurn = turnCount;
                                spentInfluence += influenceWant;
                                if (turnCount <= 20) {
                                    specialMessageQueue.add(SKIP_FLAG);
                                    specialMessageQueue.add(Comms.GO_SCOUT);
                                }
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
        System.out.println("turncountmod " + turnCountModulus);

        // send locations of enemy ECs
        if (!setFlagThisTurn && turnCount % turnCountModulus > 1 && enemyECLocs.size > 0) {

            HashMapNodeVal<Integer, ECDetails> ecLocHashNode = enemyECLocs.next();
            if (ecLocHashNode == null) {
                enemyECLocs.resetIterator();
                ecLocHashNode = enemyECLocs.next();
            }
            MapLocation ECLoc = ecLocHashNode.val.location;
            System.out.println("Sending " + ECLoc);
            int signal = Comms.getFoundECSignal(ECLoc, TEAM_ENEMY, ecLocHashNode.val.lastKnownConviction);
            setFlag(signal);
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

                }
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
