package muckrakerrush;

import battlecode.common.*;
import static muckrakerrush.Constants.*;

public class Muckraker extends Unit {
    /** Roles for this unit */
    static final int SCOUT_CORNERS = 0;
    static final int LATTICE_NETWORK = 10;
    static int role = LATTICE_NETWORK;
    static Direction scoutDir = null;

    static int offsetx = 0;
    static int offsety = 0;
    static int mapWidth = 0;
    static int mapHeight = 0;

    static MapLocation foundCorner = null;
    /**
     * set this maploc to wherever u want the unit to go, pathing code then auto
     * handles the unit movement to go there
     */
    static MapLocation targetLoc = null;

    public static void setup() throws GameActionException {
        setHomeEC();
        scoutDir = rc.getLocation().directionTo(homeEC).opposite();
    }

    public static void run() throws GameActionException {
        // global comms code independent of role

        // sense ec flags
        // TODO: possible issue if home ec is taken by opponent...
        int homeECFlag = rc.getFlag(homeECID);
        switch (Comms.SIGNAL_TYPE_MASK & homeECFlag) {
            case Comms.MAP_OFFSET_X_AND_WIDTH:
                int[] vs = Comms.readMapOffsetSignalXWidth(homeECFlag);
                offsetx = vs[0];
                mapWidth = vs[1];
                break;
            case Comms.MAP_OFFSET_Y_AND_HEIGHT:
                int[] vs2 = Comms.readMapOffsetSignalYHeight(homeECFlag);
                offsety = vs2[0];
                mapHeight = vs2[1];
                break;
        }

        // if values valid, we are done scouting, we have map dimensions
        if (mapWidth >= 32 && mapHeight >= 32) {
            role = LATTICE_NETWORK;
        }

        RobotInfo[] nearbyBots = rc.senseNearbyRobots();

        MapLocation locOfBiggestSlanderer = null;
        int biggestSlandererInfluence = 0;
        MapLocation locOfClosestFriendlyMuckraker = null;
        int distToClosestFriendlyMuckraker = 999999999;
        for (int i = nearbyBots.length; --i >= 0;) {
            RobotInfo bot = nearbyBots[i];
            if (bot.team == oppTeam && bot.type == RobotType.SLANDERER) {
                if (bot.influence > biggestSlandererInfluence) {
                    biggestSlandererInfluence = bot.influence;
                    locOfBiggestSlanderer = bot.location;
                }
            } else if (bot.team == myTeam && bot.type == RobotType.MUCKRAKER) {
                int dist = rc.getLocation().distanceSquaredTo(bot.location);
                if (dist < distToClosestFriendlyMuckraker) {
                    distToClosestFriendlyMuckraker = dist;
                    locOfClosestFriendlyMuckraker = bot.location;
                }
            }
        }

        switch (role) {
            case SCOUT_CORNERS:
                scoutCorners();
                break;
            case LATTICE_NETWORK:
                targetLoc = rc.getLocation();
                scoutCorners();
                if (rc.isReady()) {
                    if (locOfBiggestSlanderer != null) {
                        if (locOfBiggestSlanderer.distanceSquaredTo(rc.getLocation()) <= Constants.MUCKRAKER_ACTION_RADIUS) {
                            rc.expose(locOfBiggestSlanderer);
                        } else {
                            // not in range
                            targetLoc = locOfBiggestSlanderer;
                            break;
                        }
                    } else {
                        if (locOfClosestFriendlyMuckraker != null) {
                            // head in scoutDir, direction of spawning, and find lattice points, rotate left
                            // if hit edge and no spots found
                            targetLoc = rc.getLocation()
                                    .add(locOfClosestFriendlyMuckraker.directionTo(rc.getLocation()));

                        }
                    }

                }
                break;
        }
        if (rc.isReady()) {
            Direction dir = getNextDirOnPath(targetLoc);
            if (dir != Direction.CENTER) {
                rc.move(dir);
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
            // found corner, head back
            targetLoc = homeEC;
            // YOU ACTUALLY CAN SEE EC FLAGS AND ECS CAN SEE ALL FLAGS
            // if (targetLoc.distanceSquaredTo(homeEC) <= MUCKRAKER_SENSE_RADIUS) {
            if (turnCount % 2 == 0) {
                rc.setFlag(Comms.getCornerLocSignalX(foundCorner));
            } else {
                rc.setFlag(Comms.getCornerLocSignalY(foundCorner));
            }
            // }
        }
    }
}
