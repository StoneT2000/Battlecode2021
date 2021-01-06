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

        switch (role) {
            case SCOUT_CORNERS:
                /**
                 * scouting. Scout in direction bot is placed in relative to EC hopefully ECs
                 * arent adjacent...?
                 * 
                 * or EC that built scout will announce direction
                 */

                // skip bfs turn 1 since on turn 1 we spend a lot of
                // bytecode to initialize some big constants
                /*
                 * int startb = Clock.getBytecodeNum(); for (int i = 0; ++i < BFS40.length;) {
                 * MapLocation checkLoc = new MapLocation(currLoc.x + BFS40[i][0], currLoc.y +
                 * BFS40[i][1]); } int endb = Clock.getBytecodeNum();
                 * System.out.println("BFS40 costs " + (endb - startb));
                 */
                // raw bfs40 costs way too much, about 3k bycodes for just one line of code each
                // iteration....
                MapLocation currLoc = rc.getLocation();
                if (foundCorner == null) {
                    // shoot diagonal line and find intersection of line and edge of map (or corner)
                    Direction oppScoutDir = scoutDir.opposite();
                    MapLocation checkLoc = currLoc.add(scoutDir).add(scoutDir).add(scoutDir).add(scoutDir)
                            .add(scoutDir);
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

                }

                break;
            case LATTICE_NETWORK:
                break;
        }
        Direction dir = getNextDirOnPath(targetLoc);
        if (dir != Direction.CENTER) {
            rc.move(dir);
        }
    }
}
