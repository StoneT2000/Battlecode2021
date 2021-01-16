package givemeaname;

import battlecode.common.*;
import static givemeaname.Constants.*;

public class Muckraker extends Unit {
    /** Roles for this unit */
    static final int SCOUT_CORNERS = 0;
    static final int LATTICE_NETWORK = 10;
    static int role = SCOUT_CORNERS;
    static Direction scoutDir = null;
    static MapLocation homeEC = null;
    static int homeECID = -1;

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
        // find ec spawned from
        MapLocation selfLoc = rc.getLocation();
        for (int i = DIRECTIONS.length; --i >= 0;) {
            MapLocation checkLoc = selfLoc.add(DIRECTIONS[i]);
            RobotInfo bot = rc.senseRobotAtLocation(checkLoc);
            if (bot != null && bot.type == RobotType.ENLIGHTENMENT_CENTER && bot.team == myTeam) {
                int flag = rc.getFlag(bot.ID);
                if (flag != 0) {
                    homeEC = bot.location;
                    homeECID = bot.ID;
                    scoutDir = rc.getLocation().directionTo(homeEC).opposite();
                    break;
                }
            }
        }
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
        System.out.println("Map Details: Offsets: (" + offsetx + ", " + offsety +") - Width: " + mapWidth + " - Height: " + mapHeight);

        // if values valid, we are done scouting, we have map dimensions
        if (mapWidth >= 32 && mapHeight >= 32) {
            role = LATTICE_NETWORK;
        }

        // RobotInfo[] nearbyBots = rc.senseNearbyRobots();

        // for (int i = nearbyBots.length; --i >= 0;) {
        //     RobotInfo bot = nearbyBots[i];
        //     if (bot.team == myTeam && bot.type == RobotType.MUCKRAKER) {
        //         int flag = rc.getFlag(bot.ID);
        //         switch (Comms.SIGNAL_TYPE_MASK & flag) {
        //             case Comms.MAP_OFFSET_X_AND_WIDTH:
        //                 int cx = Comms.readMapOffsetSignalXWidth(flag);
        //                 break;
        //             case Comms.MAP_OFFSET_Y_AND_HEIGHT:
        //                 int cy = Comms.readCornerLocSignalY(flag);
        //                 highMapY = Math.max(highMapY, cy);
        //                 offsety = Math.min(offsety, cy);
        //                 mapHeight = highMapY - offsety + 1;
        //                 break;
        //         }
        //     }
        // }

        switch (role) {
            case SCOUT_CORNERS:
                scoutCorners();

                break;
            case LATTICE_NETWORK:
                targetLoc = rc.getLocation().add(randomDirection());
                break;
        }
        Direction dir = getNextDirOnPath(targetLoc);
        if (dir != Direction.CENTER) {
            rc.move(dir);
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
                    System.out.println("Corner at " + checkLoc);
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
