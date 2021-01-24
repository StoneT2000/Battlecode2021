package sprinttuna;

import battlecode.common.*;
import sprinttuna.utils.LinkedList;
import sprinttuna.utils.Node;
import sprinttuna.utils.HashMapNodeVal;
import sprinttuna.utils.HashTable;

import static sprinttuna.Constants.*;

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
    static final int RUSH = 1;
    static final int LATTICE_NETWORK = 10;
    static int role = LATTICE_NETWORK;
    static final int LATTICE_SIZE = 5;
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

    public static void handleFlag(int flag) {

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
                if (turnCount < 2) {
                    role = RUSH;
                    attackLoc = Comms.readAttackECSignal(flag, rc);
                }
            case Comms.SMALL_SIGNAL:
                break;
        }
    }

    public static void run() throws GameActionException {
        rc.setFlag(0);
        setFlagThisTurn = false;
        // global comms code independent of role

        // sense ec flags
        // TODO: possible issue if home ec is taken by opponent...
        if (rc.canGetFlag(homeECID)) {
            int homeECFlag = rc.getFlag(homeECID);
            handleFlag(homeECFlag);
            if (homeECFlag == Comms.GO_SCOUT && turnCount < 4) {
                role = SCOUT;
            }
        }

        RobotInfo[] nearbyBots = rc.senseNearbyRobots();

        MapLocation locOfClosestSlanderer = null;
        int distToSlosestSlanderer = 99999999;
        MapLocation locOfClosestFriendlyMuckraker = null;
        int distToClosestFriendlyMuckraker = 999999999;
        int distToClosestEnemyMuck = 9999999;
        RobotInfo closestEnemyMuck = null;
        boolean friendlySlandererInSlandererRange = false;
        for (int i = nearbyBots.length; --i >= 0;) {
            RobotInfo bot = nearbyBots[i];
            if (bot.team == myTeam) {
                handleFlag(rc.getFlag(bot.ID));
            }
            if (bot.team == oppTeam && bot.type == RobotType.SLANDERER) {
                int dist = rc.getLocation().distanceSquaredTo(bot.location);
                if (dist < distToSlosestSlanderer) {
                    distToSlosestSlanderer = dist;
                    locOfClosestSlanderer = bot.location;
                }
            } else if (bot.team == myTeam && bot.type == RobotType.MUCKRAKER) {
                int dist = rc.getLocation().distanceSquaredTo(bot.location);
                if (dist < distToClosestFriendlyMuckraker) {
                    distToClosestFriendlyMuckraker = dist;
                    locOfClosestFriendlyMuckraker = bot.location;
                }
            } else if (bot.type == RobotType.ENLIGHTENMENT_CENTER) {
                // we always send these signals out in the event the EC changes team
                handleFoundEC(bot);
            } else if (bot.team == oppTeam && bot.type == RobotType.MUCKRAKER) {
                int dist = rc.getLocation().distanceSquaredTo(bot.location);
                if (dist < distToClosestEnemyMuck) {
                    distToClosestEnemyMuck = dist;
                    closestEnemyMuck = bot;
                }
            } else if (bot.team == myTeam && bot.type == RobotType.SLANDERER) {
                int dist = rc.getLocation().distanceSquaredTo(bot.location);
                if (dist <= SLANDERER_SENSE_RADIUS + 10) {
                    friendlySlandererInSlandererRange = true;
                }
            }
        }

        // search in sensor range for close stuff
        MapLocation currLoc = rc.getLocation();
        MapLocation closestLatticeLoc = null;
        if (currLoc.x % LATTICE_SIZE == 0 && currLoc.y % LATTICE_SIZE == 0 && currLoc.distanceSquaredTo(homeEC) > 4) {
            closestLatticeLoc = currLoc;
        }
        for (int i = 0; ++i < BFS30.length;) {
            int[] deltas = BFS30[i];

            MapLocation checkLoc = new MapLocation(currLoc.x + deltas[0], currLoc.y + deltas[1]);
            if (rc.onTheMap(checkLoc)) {
                if (closestLatticeLoc == null && checkLoc.x % LATTICE_SIZE == 0 && checkLoc.y % LATTICE_SIZE == 0
                        && checkLoc.distanceSquaredTo(homeEC) > 4) {
                    RobotInfo bot = rc.senseRobotAtLocation(checkLoc);
                    if (bot == null || bot.ID == rc.getID()) {
                        closestLatticeLoc = checkLoc;
                    }
                }
            }
        }
        // scout corners / scout map if no good lattice position found
        if (closestLatticeLoc == null) {
            role = SCOUT;
        }
        if (enemyECLocs.size > 0) {
            // role = RUSH
        }

        switch (role) {
            case SCOUT:
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
            case RUSH:
                targetLoc = attackLoc;
                if (!haveMapDimensions()) {
                    scoutCorners();
                }
                if (rc.isReady()) {
                    targetSlanderers(locOfClosestSlanderer);
                    if (rc.canSenseLocation(attackLoc)) {
                        RobotInfo info = rc.senseRobotAtLocation(attackLoc);
                        if (info.team != oppTeam) {
                            // TODO no longer attack here
                        }
                    }
                }
                break;
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

            int sig = Comms.getSpottedMuckSignal(closestEnemyMuck.location);
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
            } else if (!rc.getLocation().equals(targetLoc)) {
                // wiggle out if perhaps stuck
                for (Direction wiggleDir : DIRECTIONS) {
                    if (rc.canMove(wiggleDir)) {
                        rc.move(wiggleDir);
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
