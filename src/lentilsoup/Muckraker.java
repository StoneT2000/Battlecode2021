package lentilsoup;

import battlecode.common.*;
import lentilsoup.utils.LinkedList;
import lentilsoup.utils.Node;
import lentilsoup.utils.HashTable;

import static lentilsoup.Constants.*;

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
    static final int SCOUT_CORNERS = 0;
    static final int LATTICE_NETWORK = 10;
    static int role = SCOUT_CORNERS;
    static final int LATTICE_SIZE = 5;
    static Direction scoutDir = null;

    static MapLocation foundCorner = null;
    /**
     * set this maploc to wherever u want the unit to go, pathing code then auto
     * handles the unit movement to go there
     */
    static MapLocation targetLoc = null;
    static HashTable<Integer> foundECLocHashes = new HashTable<>(12);
    static LinkedList<Integer> ECLocHashesToSend = new LinkedList<>();
    static LinkedList<Integer> ECLocHashesTeamToSend = new LinkedList<>();

    public static void setup() throws GameActionException {
        setHomeEC();
        scoutDir = rc.getLocation().directionTo(homeEC).opposite();
    }

    public static void handleFlag(int flag) {
        
        switch (Comms.SIGNAL_TYPE_MASK & flag) {
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
        }
    }

    public static void run() throws GameActionException {
        // global comms code independent of role

        // sense ec flags
        // TODO: possible issue if home ec is taken by opponent...
        if (rc.canGetFlag(homeECID)) {
            int homeECFlag = rc.getFlag(homeECID);
            handleFlag(homeECFlag);
        }

        // if values valid, we are done scouting, we have map dimensions
        if (role == SCOUT_CORNERS && haveMapDimensions()) {
            role = LATTICE_NETWORK;
        }

        RobotInfo[] nearbyBots = rc.senseNearbyRobots();

        MapLocation locOfClosestSlanderer = null;
        int distToSlosestSlanderer = 99999999;
        MapLocation locOfClosestFriendlyMuckraker = null;
        int distToClosestFriendlyMuckraker = 999999999;
        for (int i = nearbyBots.length; --i >= 0;) {
            RobotInfo bot = nearbyBots[i];
            if (bot.team == myTeam) {
                handleFlag(rc.getFlag(bot.ID));
            }
            if (bot.team == oppTeam && bot.type == RobotType.SLANDERER) {
                int dist = rc.getLocation().distanceSquaredTo(bot.location);
                if (dist < distToSlosestSlanderer) {
                    distToSlosestSlanderer =dist;
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
                int hash = Comms.encodeMapLocationWithoutOffsets(bot.location);
                if (!foundECLocHashes.contains(hash)) {
                    foundECLocHashes.add(hash);
                    ECLocHashesToSend.add(hash);
                    if (bot.team == oppTeam) {
                        ECLocHashesTeamToSend.add(TEAM_ENEMY);
                    } else if (bot.team == myTeam) {
                        ECLocHashesTeamToSend.add(TEAM_FRIEND);
                    } else {
                        ECLocHashesTeamToSend.add(TEAM_NEUTRAL);
                    }
                }
            }
        }

        // search in sensor range for close stuff
        MapLocation currLoc = rc.getLocation();
        MapLocation closestLatticeLoc = null;
        if (currLoc.x % LATTICE_SIZE == 0 && currLoc.y % LATTICE_SIZE == 0) {
            closestLatticeLoc = currLoc;
        }
        for (int i = 0; ++i < BFS30.length;) {
            int[] deltas = BFS30[i];

            MapLocation checkLoc = new MapLocation(currLoc.x + deltas[0], currLoc.y + deltas[1]);
            if (rc.onTheMap(checkLoc)) {
                if (closestLatticeLoc == null && checkLoc.x % LATTICE_SIZE == 0 && checkLoc.y % LATTICE_SIZE == 0) {
                    RobotInfo bot = rc.senseRobotAtLocation(checkLoc);
                    if (bot == null || bot.ID == rc.getID()) {
                        closestLatticeLoc = checkLoc;
                    }
                }
            }
        }

        switch (role) {
            case SCOUT_CORNERS:
                scoutCorners();
                break;
            case LATTICE_NETWORK:
                targetLoc = rc.getLocation();
                if (!haveMapDimensions()) {
                    scoutCorners();
                }
                if (rc.isReady()) {
                    if (locOfClosestSlanderer != null) {
                        if (locOfClosestSlanderer.distanceSquaredTo(rc.getLocation()) <= Constants.MUCKRAKER_ACTION_RADIUS) {
                            rc.expose(locOfClosestSlanderer);
                        } else {
                            // not in range
                            targetLoc = locOfClosestSlanderer;
                            break;
                        }
                    } else {
                        // in lattice mode, we move into a lattice position, otherwise, we run in a direction and change if we hit wall?
                        if (closestLatticeLoc != null) {
                            targetLoc = closestLatticeLoc;
                        }
                        else {
                            targetLoc = rc.getLocation().add(scoutDir).add(scoutDir).add(scoutDir);
                            if (!rc.onTheMap(targetLoc)) {
                                scoutDir = scoutDir.rotateLeft().rotateLeft();
                                targetLoc = rc.getLocation().add(scoutDir).add(scoutDir).add(scoutDir);
                            }
                        }
                        // else if (locOfClosestFriendlyMuckraker != null) {
                        //     // head in scoutDir, direction of spawning, and find lattice points, rotate left
                        //     // if hit edge and no spots found
                        //     targetLoc = rc.getLocation()
                        //             .add(locOfClosestFriendlyMuckraker.directionTo(rc.getLocation()));

                        // }
                    }

                }
                break;
        }

        // comms work
        if (haveMapDimensions()) {
            // if we have map dimensions, send out scouting info
            if (ECLocHashesToSend.size > 0) {
                Node<Integer> hashnode = ECLocHashesToSend.dequeue();
                Node<Integer> hashnodeteam = ECLocHashesTeamToSend.dequeue();
                MapLocation ECLoc = Comms.decodeMapLocationWithoutOffsets(hashnode.val);
                int signal = Comms.getFoundECSignal(ECLoc, hashnodeteam.val, offsetx, offsety);
                setFlag(signal);
                // remove from table so we can search it again
                foundECLocHashes.remove(hashnode.val);
            }
        }


        if (rc.isReady()) {
            Direction dir = getNextDirOnPath(targetLoc);
            if (dir != Direction.CENTER) {
                rc.move(dir);
            } else if (!rc.getLocation().equals(targetLoc)){
                // wiggle out if perhaps stuck
                for (Direction wiggleDir : DIRECTIONS) {
                    // MapLocation loc = rc.getLocation().add(wiggleDir);
                    if (rc.canMove(wiggleDir)) {
                        rc.move(wiggleDir);
                    }
                }
            }
        }
    }

    public static void scoutCorners() throws GameActionException {
        // scouts by finding intersection of line of scout dir with edge
        MapLocation currLoc = rc.getLocation();
        if (foundCorner == null) {
            // shoot diagonal line and find intersection of line and edge of map (or corner)
            Direction oppScoutDir = scoutDir.opposite();
            MapLocation checkLoc = currLoc.add(scoutDir).add(scoutDir).add(scoutDir).add(scoutDir);
            int edgeOrCornerReached = -1;
            for (int i = 4; --i >= 0;) {
                checkLoc = checkLoc.add(oppScoutDir);
                if (!rc.onTheMap(checkLoc)) {
                    // must see off map first before seeing on map
                    edgeOrCornerReached = 0;
                    continue;
                } else {
                    // if finally on map,
                    if (edgeOrCornerReached == 0) {
                        edgeOrCornerReached = 1;
                    }
                    break;
                }
            }
            if (edgeOrCornerReached == 1) {
                // determrine if edge or corner, if edge, run along edge
                MapLocation leftLoc = checkLoc.add(scoutDir.rotateLeft());
                MapLocation rightLoc = checkLoc.add(scoutDir.rotateRight());
                if (rc.onTheMap(leftLoc)) {
                    // run along edge
                    targetLoc = rc.getLocation().add(scoutDir.rotateLeft());
                } else if (rc.onTheMap(rightLoc)) {
                    targetLoc = rc.getLocation().add(scoutDir.rotateRight());
                } else {
                    // we reached corner,
                    foundCorner = checkLoc;
                }
            } else {
                targetLoc = rc.getLocation().add(scoutDir);
            }
        } else {
            // found corner
            // YOU ACTUALLY CAN SEE EC FLAGS AND ECS CAN SEE ALL FLAGS
            if (!haveMapDimensions()) {
                if (turnCount % 2 == 0) {
                    setFlag(Comms.getCornerLocSignalX(foundCorner));
                } else {
                    setFlag(Comms.getCornerLocSignalY(foundCorner));
                }
            }
        }
    }
}