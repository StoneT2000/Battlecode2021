package superecosushi;

import battlecode.common.*;
import superecosushi.utils.LinkedList;
import superecosushi.utils.Node;
import superecosushi.utils.HashMapNodeVal;
import superecosushi.utils.HashTable;

import static superecosushi.Constants.*;

public class Muckraker extends Unit {
    public static final int[][] BFS30 = { { 0, 0 }, { 1, 0 }, { 0, -1 }, { -1, 0 }, { 0, 1 }, { 2, 0 }, { 1, -1 },
            { 0, -2 }, { -1, -1 }, { -2, 0 }, { -1, 1 }, { 0, 2 }, { 1, 1 }, { 3, 0 }, { 2, -1 }, { 1, -2 }, { 0, -3 },
            { -1, -2 }, { -2, -1 }, { -3, 0 }, { -2, 1 }, { -1, 2 }, { 0, 3 }, { 1, 2 }, { 2, 1 }, { 4, 0 }, { 3, -1 },
            { 2, -2 }, { 1, -3 }, { 0, -4 }, { -1, -3 }, { -2, -2 }, { -3, -1 }, { -4, 0 }, { -3, 1 }, { -2, 2 },
            { -1, 3 }, { 0, 4 }, { 1, 3 }, { 2, 2 }, { 3, 1 }, { 5, 0 }, { 4, -1 }, { 3, -2 }, { 2, -3 }, { 1, -4 },
            { 0, -5 }, { -1, -4 }, { -2, -3 }, { -3, -2 }, { -4, -1 }, { -5, 0 }, { -4, 1 }, { -3, 2 }, { -2, 3 },
            { -1, 4 }, { 0, 5 }, { 1, 4 }, { 2, 3 }, { 3, 2 }, { 4, 1 }, { 5, -1 }, { 4, -2 }, { 3, -3 }, { 2, -4 },
            { 1, -5 }, { -1, -5 }, { -2, -4 }, { -3, -3 }, { -4, -2 }, { -5, -1 }, { -5, 1 }, { -4, 2 }, { -3, 3 },
            { -2, 4 }, { -1, 5 }, { 1, 5 }, { 2, 4 }, { 3, 3 }, { 4, 2 }, { 5, 1 }, { 5, -2 }, { 4, -3 }, { 3, -4 },
            { 2, -5 }, { -2, -5 }, { -3, -4 }, { -4, -3 }, { -5, -2 }, { -5, 2 }, { -4, 3 }, { -3, 4 }, { -2, 5 },
            { 2, 5 }, { 3, 4 }, { 4, 3 }, { 5, 2 } };
    /** Roles for this unit */
    static final int SCOUT = 0;
    static final int SCOUT_BUT_ALLOW_RUSH = 3;
    static final int RUSH = 1;
    static final int LATTICE_NETWORK = 10;
    static int role = SCOUT_BUT_ALLOW_RUSH;
    static final int LATTICE_SIZE = 5;
    static boolean announcedToProtectingPoliAttackLoc = false;
    static Direction scoutDir = null;

    static Direction lastMoveAwayFromMucksDir = null;

    static HashTable<Integer> cornerXs = new HashTable<>(4);
    static HashTable<Integer> cornerYs = new HashTable<>(4);
    /**
     * set this maploc to wherever u want the unit to go, pathing code then auto
     * handles the unit movement to go there
     */
    static MapLocation targetLoc = null;

    static MapLocation attackLoc = null;

    // whether to always rotate left or rotate right
    static boolean rotateLeftScoutDir = false;

    static int protectingPoliID = -1;

    static int lastTurnSawSlanderer = -100;

    static HashTable<Integer> checkedECHashes = new HashTable<>(10);

    public static void setup() throws GameActionException {
        setHomeEC();
        scoutDir = rc.getLocation().directionTo(homeEC).opposite();
        switch (scoutDir) {
            case NORTH:
            case SOUTH:
            case NORTHWEST:
            case SOUTHEAST:
                rotateLeftScoutDir = true;
                break;
            // case WEST:
            // case EAST:
            // case NORTHEAST:
            // case SOUTHWEST:
            // scoutDir = scoutDir.rotateRight();
            // break;
            default:
                break;
        }
    }

    public static void handleFlag(int flag, boolean homeEC) {

        switch (Comms.SIGNAL_TYPE_MASK & flag) {
            case Comms.CORNER_LOC_X:
                int cx = Comms.readCornerLocSignalX(flag);
                if (!cornerXs.contains(cx)) {
                    // a different corner found by another unit, spread the word!
                    cornerXs.add(cx);
                }
                break;
            case Comms.CORNER_LOC_Y:
                int cy = Comms.readCornerLocSignalX(flag);
                if (!cornerYs.contains(cy)) {
                    // a different corner found by another unit, spread the word!
                    cornerYs.add(cy);
                }
                break;
            case Comms.MAP_OFFSET_X_AND_WIDTH:
                int[] vs = Comms.readMapOffsetSignalXWidth(flag);
                offsetx = vs[0];
                mapWidth = vs[1];
                break;
            case Comms.MAP_OFFSET_Y_AND_HEIGHT:
                int[] vs2 = Comms.readMapOffsetSignalYHeight(flag);
                offsety = vs2[0];
                mapHeight = vs2[1];
                break;
            case Comms.FOUND_EC:
                processFoundECFlag(flag);
                break;
            case Comms.ATTACK_EC:
                if (homeEC) {
                    if ((Comms.SIGNAL_TYPE_5BIT_MASK & flag) == Comms.ATTACK_EC) {

                        if (turnCount < 2 || role == SCOUT_BUT_ALLOW_RUSH) {
                            role = RUSH;
                            attackLoc = Comms.readAttackECSignal(flag, rc);
                        }
                    }
                }
            case Comms.SMALL_SIGNAL:
                break;
        }
    }

    public static void run() throws GameActionException {
        rc.setFlag(0);
        setFlagThisTurn = false;
        boolean absolutelyDONOTWIGGLE = false;
        // global comms code independent of role

        // sense ec flags
        // TODO: possible issue if home ec is taken by opponent...
        if (rc.canGetFlag(homeECID)) {
            int homeECFlag = rc.getFlag(homeECID);
            handleFlag(homeECFlag, true);
            if (turnCount < 4) {
                switch (homeECFlag) {
                    case Comms.GO_SCOUT:
                        role = SCOUT;
                        break;
                    case Comms.GO_SCOUT_NORTH:
                        role = SCOUT;
                        scoutDir = Direction.NORTH;
                        break;
                    case Comms.GO_SCOUT_SOUTH:
                        role = SCOUT;
                        scoutDir = Direction.SOUTH;
                        break;
                    case Comms.GO_SCOUT_EAST:
                        role = SCOUT;
                        scoutDir = Direction.EAST;
                        break;
                    case Comms.GO_SCOUT_WEST:
                        role = SCOUT;
                        scoutDir = Direction.WEST;
                        break;
                    case Comms.GO_SCOUT_NORTHEAST:
                        role = SCOUT;
                        scoutDir = Direction.NORTHEAST;
                        break;
                    case Comms.GO_SCOUT_NORTHWEST:
                        role = SCOUT;
                        scoutDir = Direction.NORTHWEST;
                        break;
                    case Comms.GO_SCOUT_SOUTHEAST:
                        role = SCOUT;
                        scoutDir = Direction.SOUTHEAST;
                        break;
                    case Comms.GO_SCOUT_SOUTHWEST:
                        role = SCOUT;
                        scoutDir = Direction.SOUTHWEST;
                        break;

                }
                if (role == SCOUT) {
                    System.out.println("Scout dir: " + scoutDir);
                }
            }

        }
        RobotInfo protectingPoli = null;
        RobotInfo[] nearbyBots = rc.senseNearbyRobots();

        MapLocation locOfClosestSlanderer = null;
        int distToSlosestSlanderer = 99999999;
        MapLocation locOfClosestEnemyPoli = null;
        int distToClosestEnemyPoli = 99999999;
        MapLocation locOfClosestFriendlyMuckraker = null;
        int distToClosestFriendlyMuckraker = 999999999;
        int distToClosestEnemyMuck = 9999999;
        RobotInfo closestEnemyMuck = null;
        boolean friendlySlandererInSlandererRange = false;
        for (int i = nearbyBots.length; --i >= 0;) {
            RobotInfo bot = nearbyBots[i];
            if (bot.team == myTeam) {
                int flag = rc.getFlag(bot.ID);
                boolean isEC = false;
                if (bot.type == RobotType.ENLIGHTENMENT_CENTER) {
                    isEC = true;
                }
                handleFlag(flag, isEC);

                switch (bot.type) {
                    case MUCKRAKER: {
                        int dist = rc.getLocation().distanceSquaredTo(bot.location);
                        if (dist < distToClosestFriendlyMuckraker) {
                            distToClosestFriendlyMuckraker = dist;
                            locOfClosestFriendlyMuckraker = bot.location;
                        }
                        break;
                    }
                    case ENLIGHTENMENT_CENTER:
                        handleFoundEC(bot);
                        break;
                    case SLANDERER: {
                        int dist = rc.getLocation().distanceSquaredTo(bot.location);
                        if (dist <= SLANDERER_SENSE_RADIUS + 10) {
                            friendlySlandererInSlandererRange = true;
                        }
                        break;
                    }
                    case POLITICIAN: {
                        if ((Comms.SIGNAL_TYPE_MASK & flag) == Comms.PROTECT_BUFF_MUCK) {
                            int id = Comms.readProtectBuffMuckSignal(flag);
                            if (id == rc.getID()) {
                                protectingPoliID = bot.ID;
                                protectingPoli = bot;
                            }
                        }
                        break;
                    }
                }
            } else if (bot.team == oppTeam) {
                switch (bot.type) {
                    case SLANDERER: {
                        int dist = rc.getLocation().distanceSquaredTo(bot.location);
                        if (dist < distToSlosestSlanderer) {
                            distToSlosestSlanderer = dist;
                            locOfClosestSlanderer = bot.location;
                            lastTurnSawSlanderer = turnCount;
                        }
                        break;
                    }
                    case ENLIGHTENMENT_CENTER:
                        handleFoundEC(bot);
                        break;
                    case MUCKRAKER: {
                        int dist = rc.getLocation().distanceSquaredTo(bot.location);
                        if (dist < distToClosestEnemyMuck) {
                            distToClosestEnemyMuck = dist;
                            closestEnemyMuck = bot;
                        }
                        break;
                    }
                    case POLITICIAN: {
                        int dist = rc.getLocation().distanceSquaredTo(bot.location);
                        if (dist < distToClosestEnemyPoli) {
                            locOfClosestEnemyPoli = bot.location;
                            distToClosestEnemyPoli = dist;
                        }
                        break;
                    }

                }
            } else if (bot.type == RobotType.ENLIGHTENMENT_CENTER) {
                handleFoundEC(bot);
            }
        }
        if (protectingPoli == null && protectingPoliID != -1 && rc.canSenseRobot(protectingPoliID)) {
            protectingPoli = rc.senseRobot(protectingPoliID);
        }

        switch (role) {
            case SCOUT_BUT_ALLOW_RUSH:
                System.out.println("there are " + enemyECLocs.size + " enemy lcos");
            case SCOUT: {
                scoutCorners();
                targetLoc = rc.getLocation().add(scoutDir).add(scoutDir).add(scoutDir);
                Direction[] dirs = new Direction[] { scoutDir.rotateLeft(), scoutDir.rotateRight(),
                        scoutDir.rotateLeft().rotateLeft(), scoutDir.rotateRight().rotateRight(),
                        scoutDir.rotateLeft().rotateLeft().rotateLeft(),
                        scoutDir.rotateRight().rotateRight().rotateRight(), scoutDir.opposite() };
                int i = -1;
                while (!rc.onTheMap(targetLoc)) {
                    i++;
                    targetLoc = rc.getLocation().add(dirs[i]).add(dirs[i]).add(dirs[i]);

                }
                if (i != -1) {
                    scoutDir = dirs[i];
                }
                targetSlanderers(locOfClosestSlanderer);
                break;
            }
            case RUSH: {
                targetLoc = attackLoc;
                System.out.println("there are " + enemyECLocs.size + " enemy lcos");
                if (!haveMapDimensions()) {
                    scoutCorners();
                }
                int curhash = Comms.encodeMapLocation(attackLoc);
                if (!enemyECLocs.contains(curhash)) {
                    // go back to scouting
                    role = SCOUT_BUT_ALLOW_RUSH;
                    HashMapNodeVal<Integer, ECDetails> node = enemyECLocs.next();
                    if (node == null) {
                        enemyECLocs.resetIterator();
                        node = enemyECLocs.next();
                    }
                    while (node != null) {
                        if (!node.val.location.equals(attackLoc)) {
                            int hashthere = Comms.encodeMapLocation(node.val.location);
                            if (!checkedECHashes.contains(hashthere)) {
                                role = RUSH;
                                attackLoc = node.val.location;
                                break;
                            }
                        }
                        node = enemyECLocs.next();
                    }
                }
                // if we see ec and get close but no slands seen we move on
                if (rc.canSenseLocation(attackLoc) && rc.getLocation().distanceSquaredTo(attackLoc) <= 5) {
                    RobotInfo info = rc.senseRobotAtLocation(attackLoc);
                    checkedECHashes.add(Comms.encodeMapLocation(attackLoc));
                    if (info.team == myTeam || turnCount - lastTurnSawSlanderer > 10) {
                        // go back to scouting
                        role = SCOUT_BUT_ALLOW_RUSH;
                        HashMapNodeVal<Integer, ECDetails> node = enemyECLocs.next();
                        if (node == null) {
                            enemyECLocs.resetIterator();
                            node = enemyECLocs.next();
                        }
                        while (node != null) {
                            if (!node.val.location.equals(attackLoc)) {
                                int hashthere = Comms.encodeMapLocation(node.val.location);
                                if (!checkedECHashes.contains(hashthere)) {
                                    role = RUSH;
                                    attackLoc = node.val.location;
                                    break;
                                }
                            }
                            node = enemyECLocs.next();
                        }
                    }
                }
                if (rc.isReady()) {
                    targetSlanderers(locOfClosestSlanderer);

                    RobotInfo[] enemyBots = rc.senseNearbyRobots(rc.getLocation(), 2, oppTeam);
                    int potentialMaxDamageInOneTurn = 0;
                    for (int i = 0; i < enemyBots.length; i++) {
                        RobotInfo bot = enemyBots[i];
                        if (bot.type == RobotType.POLITICIAN) {
                            int damage = calculatePoliticianEmpowerConviction(oppTeam, bot.conviction, 0);
                            if (damage > potentialMaxDamageInOneTurn) {
                                potentialMaxDamageInOneTurn = damage;
                            }
                        }
                    }
                    int protectivePoliConviction = 0;
                    if (protectingPoli != null) {
                        protectivePoliConviction += protectingPoli.conviction;
                        System.out.println(" PROTECT " + protectingPoli.ID);
                    }

                    if (protectingPoli != null && muckShouldHeal(rc.getInfluence(), rc.getConviction(), potentialMaxDamageInOneTurn)) {

                        // stay put if already close enogh to friend poli
                        int distToProtectivePoli = rc.getLocation().distanceSquaredTo(protectingPoli.location);
                        System.out.println("trying to find poli to heal self! dist " + distToProtectivePoli);
                        targetLoc = protectingPoli.location;
                        // if (distToProtectivePoli > 1) {
                        absolutelyDONOTWIGGLE = true;
                        // } els
                    }
                }
                break;
            }
            case LATTICE_NETWORK:
                targetLoc = rc.getLocation();
                if (!haveMapDimensions()) {
                    scoutCorners();
                }
                if (rc.isReady()) {
                    if (locOfClosestSlanderer != null) {
                        targetSlanderers(locOfClosestSlanderer);
                    } else {
                        if (locOfClosestFriendlyMuckraker != null) {
                            // head in scoutDir, direction of spawning, and find lattice points, rotate left
                            // if hit edge and no spots found
                            Direction momentumDir = locOfClosestFriendlyMuckraker.directionTo(rc.getLocation());
                            targetLoc = rc.getLocation().add(momentumDir);
                            lastDir = momentumDir;

                        } else if (lastMoveAwayFromMucksDir != null) {
                            targetLoc = rc.getLocation().add(lastMoveAwayFromMucksDir);
                        }
                    }
                }
                break;
        }

        /** COMMS */

        if (friendlySlandererInSlandererRange && closestEnemyMuck != null) {

            int sig = Comms.getSpottedMuckSignal(closestEnemyMuck.location, closestEnemyMuck.conviction);
            setFlag(sig);
        }

        // report buff mucks
        if (closestEnemyMuck != null && closestEnemyMuck.conviction >= BUFF_MUCK_THRESHOLD) {
            int sig = Comms.getSpottedMuckSignal(closestEnemyMuck.location, closestEnemyMuck.conviction);
            setFlag(sig);
        }

        // if we have map dimensions, send out scouting info
        if (ECDetailsToSend.size > 0) {
            Node<ECDetails> ecDetailsNode = ECDetailsToSend.dequeue();
            int signal = Comms.getFoundECSignal(ecDetailsNode.val.location, ecDetailsNode.val.teamind,
                    ecDetailsNode.val.lastKnownConviction);
            specialMessageQueue.add(signal);
            foundECLocHashes.remove(Comms.encodeMapLocation(ecDetailsNode.val.location));
        }

        if (role == RUSH && protectingPoli != null && attackLoc != null) {
            // announce rush attack loc once
            setFlag(Comms.getAttackECSignal(attackLoc));
            announcedToProtectingPoliAttackLoc = true;
        }

        // report slanderer locations or poli locs to EC to indicate promising scouting directions
        if (locOfClosestSlanderer != null) {
            int sig = Comms.getFoundEnemyUnitSignal(locOfClosestSlanderer, TYPE_SLANDERER);
            specialMessageQueue.add(sig);
        }
        else if (locOfClosestEnemyPoli != null) {
            int sig = Comms.getFoundEnemyUnitSignal(locOfClosestEnemyPoli, TYPE_POLITICIAN);
            specialMessageQueue.add(sig);
        }

        // handle flags that arernt corner stuff
        if (!setFlagThisTurn && specialMessageQueue.size > 0) {
            setFlag(specialMessageQueue.dequeue().val);
        }

        // YOU ACTUALLY CAN SEE EC FLAGS AND ECS CAN SEE ALL FLAGS
        int turnCountMod = 2;

        if (!haveMapDimensions() && !setFlagThisTurn) {
            // if we have more corner points, send those out as well
            int signalX = -1;
            if (turnCount % turnCountMod == 0) {
                if (cornerXs.size > 0) {
                    signalX = 1;
                }
            } else if (turnCount % turnCountMod == 1) {
                if (cornerYs.size > 0) {
                    signalX = 0;
                }
            }
            if (signalX == 1) {
                Node<Integer> cornernode = cornerXs.next();
                if (cornernode == null) {
                    cornerXs.resetIterator();
                    cornernode = cornerXs.next();
                }
                setFlag(Comms.getCornerLocSignalX(cornernode.val));
            } else if (signalX == 0) {
                // signal y then
                Node<Integer> cornernode = cornerYs.next();
                if (cornernode == null) {
                    cornerYs.resetIterator();
                    cornernode = cornerYs.next();
                }
                setFlag(Comms.getCornerLocSignalY(cornernode.val));
            }
        }

        if (rc.isReady()) {
            Direction dir = getNextDirOnPath(targetLoc);
            if (dir != Direction.CENTER && rc.canMove(dir)) {
                rc.move(dir);
            } else if (!absolutelyDONOTWIGGLE && !rc.getLocation().equals(targetLoc)) {
                // wiggle out if perhaps stuck
                for (Direction wiggleDir : DIRECTIONS) {
                    if (rc.canMove(wiggleDir)) {
                        rc.move(wiggleDir);
                        break;
                    }
                }
            }
        }
    }

    private static void targetSlanderers(MapLocation locOfClosestSlanderer) throws GameActionException {
        if (locOfClosestSlanderer != null) {
            if (rc.canExpose(locOfClosestSlanderer)) {
                rc.expose(locOfClosestSlanderer);
            } else {
                // not in range
                targetLoc = locOfClosestSlanderer;
            }
        }
    }

    // finds edges by searching in straight line following direction dir
    // returns null if not found
    private static MapLocation findEdgeLocation(Direction dir) throws GameActionException {
        MapLocation currLoc = rc.getLocation();
        MapLocation checkLoc = currLoc.add(dir).add(dir).add(dir).add(dir);
        Direction oppdir = dir.opposite();
        int edgeReached = -1;
        for (int i = 4; --i >= 0;) {
            checkLoc = checkLoc.add(oppdir);
            if (!rc.onTheMap(checkLoc)) {
                // must see off map first before seeing on map
                edgeReached = 0;
                continue;
            } else {
                // if finally on map after previously not on map
                if (edgeReached == 0) {
                    edgeReached = 1;
                }
                break;
            }
        }
        if (edgeReached == 1) {
            return checkLoc;
        }
        return null;
    }

    public static void scoutCorners() throws GameActionException {
        // scouts by finding intersection of line of scout dir with edge
        MapLocation currLoc = rc.getLocation();

        // shoot 4 cardinal lines to find edges
        MapLocation northEdge = findEdgeLocation(Direction.NORTH);
        MapLocation westEdge = findEdgeLocation(Direction.WEST);
        MapLocation eastEdge = findEdgeLocation(Direction.EAST);
        MapLocation southEdge = findEdgeLocation(Direction.SOUTH);

        if (northEdge != null) {
            cornerYs.add(northEdge.y);
        }
        if (southEdge != null) {
            cornerYs.add(southEdge.y);
        }
        if (westEdge != null) {
            cornerXs.add(westEdge.x);
        }
        if (eastEdge != null) {
            cornerXs.add(eastEdge.x);
        }
    }
}
