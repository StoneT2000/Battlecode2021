package veggiesoup;

import battlecode.common.*;
import veggiesoup.utils.Node;

import static veggiesoup.Constants.*;

public class Politician extends Unit {
    public static final int[][] BFS25 = { { 0, 0 }, { 1, 0 }, { 0, -1 }, { -1, 0 }, { 0, 1 }, { 2, 0 }, { 1, -1 },
            { 0, -2 }, { -1, -1 }, { -2, 0 }, { -1, 1 }, { 0, 2 }, { 1, 1 }, { 3, 0 }, { 2, -1 }, { 1, -2 }, { 0, -3 },
            { -1, -2 }, { -2, -1 }, { -3, 0 }, { -2, 1 }, { -1, 2 }, { 0, 3 }, { 1, 2 }, { 2, 1 }, { 4, 0 }, { 3, -1 },
            { 2, -2 }, { 1, -3 }, { 0, -4 }, { -1, -3 }, { -2, -2 }, { -3, -1 }, { -4, 0 }, { -3, 1 }, { -2, 2 },
            { -1, 3 }, { 0, 4 }, { 1, 3 }, { 2, 2 }, { 3, 1 }, { 5, 0 }, { 4, -1 }, { 3, -2 }, { 2, -3 }, { 1, -4 },
            { 0, -5 }, { -1, -4 }, { -2, -3 }, { -3, -2 }, { -4, -1 }, { -5, 0 }, { -4, 1 }, { -3, 2 }, { -2, 3 },
            { -1, 4 }, { 0, 5 }, { 1, 4 }, { 2, 3 }, { 3, 2 }, { 4, 1 }, { 4, -2 }, { 3, -3 }, { 2, -4 }, { -2, -4 },
            { -3, -3 }, { -4, -2 }, { -4, 2 }, { -3, 3 }, { -2, 4 }, { 2, 4 }, { 3, 3 }, { 4, 2 }, { 4, -3 }, { 3, -4 },
            { -3, -4 }, { -4, -3 }, { -4, 3 }, { -3, 4 }, { 3, 4 }, { 4, 3 } };
    static final int SACRIFICE = 0;
    static final int EXPLORE = 1;
    static final int DEFEND_SLANDERER = 2;
    static final int ATTACK_EC = 3;
    static Direction exploreDir = Direction.NORTH;;
    static int role = DEFEND_SLANDERER;
    static MapLocation targetLoc = null;
    static int LATTICE_SIZE = 3;
    static MapLocation closestCorner = null;

    static MapLocation targetedEnemyLoc = null;

    public static void setup() throws GameActionException {
        // find ec spawned from
        setHomeEC();
        // if null, we probably got a signal to do something instant like
        // sacrifice/empower
        // or we converted from slanderer
        if (homeEC == null) {
            // role = SACRIFICE;
        }
        else {
            exploreDir = homeEC.directionTo(rc.getLocation());
        }
        // first turn set flag to indiciate unit type
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
            case Comms.POLI_SACRIFICE:
                role = SACRIFICE;
                break;
            case Comms.FOUND_EC:
                processFoundECFlag(flag);
                break;
        }
    }

    public static void run() throws GameActionException {

        // if (role == SACRIFICE) {
        //     if (rc.isReady()) {
        //         rc.empower(2);
        //     }
        //     return;
        // }
        if (rc.canGetFlag(homeECID)) {
            int homeECFlag = rc.getFlag(homeECID);
            handleFlag(homeECFlag);
        }
        RobotInfo[] nearbyBots = rc.senseNearbyRobots();
        MapLocation locOfClosestFriendlyPoli = null;
        int distToClosestFriendlyPoli = 999999999;
        MapLocation locOfClosestEnemyMuck = null;
        int distToClosestEnemyMuck = 9999999;
        for (int i = nearbyBots.length; --i >= 0;) {
            RobotInfo bot = nearbyBots[i];
            if (bot.team == myTeam && bot.type == RobotType.POLITICIAN) {
                int dist = rc.getLocation().distanceSquaredTo(bot.location);
                if (dist < distToClosestFriendlyPoli) {
                    distToClosestFriendlyPoli = dist;
                    locOfClosestFriendlyPoli = bot.location;
                }
            } else if (bot.team == oppTeam && bot.type == RobotType.MUCKRAKER) {
                int dist = rc.getLocation().distanceSquaredTo(bot.location);
                if (dist < distToClosestEnemyMuck) {
                    distToClosestEnemyMuck = dist;
                    locOfClosestEnemyMuck = bot.location;
                }
            }
        }
        if (haveMapDimensions()) {
            if (closestCorner == null) {
                MapLocation[] corners = new MapLocation[]{
                    new MapLocation(offsetx, offsety),
                    new MapLocation(offsetx + mapWidth, offsety),
                    new MapLocation(offsetx, offsety + mapHeight),
                    new MapLocation(offsetx + mapWidth, offsety + mapHeight)
                };
                int closestDist = 999999999;
                for (int i = -1; ++i < corners.length;) {
                    int dist = corners[i].distanceSquaredTo(rc.getLocation());
                    if (dist < closestDist) {
                        closestCorner = corners[i];
                        closestDist = dist;
                    }
                }
            }
        }

        MapLocation protectLocation = homeEC;
        if (closestCorner != null) {
            protectLocation = closestCorner;
        }
        MapLocation currLoc = rc.getLocation();
        MapLocation bestLatticeLoc = null;
        int bestLatticeLocVal = Integer.MIN_VALUE;
        if (currLoc.x % LATTICE_SIZE == 0 && currLoc.y % LATTICE_SIZE == 0) {
            bestLatticeLoc = currLoc;
            bestLatticeLocVal = - bestLatticeLoc.distanceSquaredTo(protectLocation);
        }
        for (int i = 0; ++i < BFS25.length;) {
            int[] deltas = BFS25[i];

            MapLocation checkLoc = new MapLocation(currLoc.x + deltas[0], currLoc.y + deltas[1]);
            if (rc.onTheMap(checkLoc)) {
                if (checkLoc.x % LATTICE_SIZE == 0 && checkLoc.y % LATTICE_SIZE == 0) {
                    RobotInfo bot = rc.senseRobotAtLocation(checkLoc);
                    if (bot == null || bot.ID == rc.getID()) {
                        int value = -checkLoc.distanceSquaredTo(protectLocation);
                        if (value > bestLatticeLocVal) {
                            bestLatticeLocVal = value;
                            bestLatticeLoc = checkLoc;
                        }
                    }
                }
            }
        }
        if (homeEC != null) {
            if (rc.getLocation().distanceSquaredTo(homeEC) == 1) {
                // if buff is high, suicide?
                if (rc.canEmpower(1) && calculatePoliticianEmpowerConviction(myTeam, rc.getConviction(), 0) / 4 > rc.getConviction() * 2) {
                    rc.empower(1);
                }
            } else if (rc.getLocation().distanceSquaredTo(homeEC) == 2) {
                // if buff is high, suicide?
                if (rc.canEmpower(2) && calculatePoliticianEmpowerConviction(myTeam, rc.getConviction(), 0) / 6 > rc.getConviction() * 2) {
                    rc.empower(2);
                }
            }
        }

        if (rc.getConviction() >= 100) {
            role = ATTACK_EC;
        }

        if (role == EXPLORE) {
            if (locOfClosestFriendlyPoli != null) {
                // head in scoutDir, direction of spawning, and find lattice points, rotate left
                // if hit edge and no spots found
                targetLoc = rc.getLocation().add(locOfClosestFriendlyPoli.directionTo(rc.getLocation()));

            }
        } else if (role == DEFEND_SLANDERER) {
            // to defend, stay near EC. FUTURE, move to cornern where we pack slanderers
            if (locOfClosestEnemyMuck != null) {
                targetLoc = rc.getLocation().add(rc.getLocation().directionTo(locOfClosestEnemyMuck));
                int distToClosestMuck = rc.getLocation().distanceSquaredTo(locOfClosestEnemyMuck);
                if (rc.canEmpower(distToClosestMuck)) {
                    rc.empower(distToClosestEnemyMuck);
                }
            } else {
                // move away from EC...
                if (homeEC != null && rc.getLocation().distanceSquaredTo(homeEC) <= 2) {
                    targetLoc = rc.getLocation().add(rc.getLocation().directionTo(homeEC).opposite());
                }
                else {
                    // if no lattice found, go in exploreDir
                    if (bestLatticeLoc == null) {
                        targetLoc = rc.getLocation().add(exploreDir).add(exploreDir).add(exploreDir);
                        if (!rc.onTheMap(targetLoc)) {
                            exploreDir = exploreDir.rotateLeft().rotateLeft();
                            targetLoc = rc.getLocation().add(exploreDir).add(exploreDir).add(exploreDir);
                        }
                        // targetLoc = rc.getLocation().add(rc.getLocation().directionTo(homeEC).opposite());
                    }
                    else {
                        targetLoc = bestLatticeLoc;
                    }
                }

            }
        } else if (role == ATTACK_EC) {
            // move away from EC...
            // repetitive code
            
            if (enemyECLocs.size > 0) {
                // check current target is still there
                if (targetedEnemyLoc != null) {
                    if (!enemyECLocs.contains(Comms.encodeMapLocation(targetedEnemyLoc, offsetx, offsety))) {
                        targetedEnemyLoc = null;
                    }
                }
                if (targetedEnemyLoc == null) {
                    Node<Integer> eclocnode = enemyECLocs.next();
                    if (eclocnode == null) {
                        enemyECLocs.resetIterator();
                        eclocnode = enemyECLocs.next();
                    }
                    MapLocation ECLoc = Comms.decodeMapLocation(eclocnode.val, offsetx, offsety);
                    targetedEnemyLoc = ECLoc;
                }
                // this shouldnt happen, buut if still null dont break
                if (targetedEnemyLoc != null)  {
                    targetLoc = targetedEnemyLoc;
                    System.out.println("at " +rc.getLocation() + " targeting " + targetedEnemyLoc);
                    if (rc.getLocation().distanceSquaredTo(targetedEnemyLoc) <= 1) {
                        rc.empower(1);
                    }
                }
            } else {
                // lattice instead
                if (rc.getLocation().distanceSquaredTo(homeEC) <= 2) {
                    targetLoc = rc.getLocation().add(rc.getLocation().directionTo(homeEC).opposite());
                }
                else {
                    // if no lattice found, go in exploreDir
                    if (bestLatticeLoc == null) {
                        targetLoc = rc.getLocation().add(exploreDir).add(exploreDir).add(exploreDir);
                        if (!rc.onTheMap(targetLoc)) {
                            exploreDir = exploreDir.rotateLeft().rotateLeft();
                            targetLoc = rc.getLocation().add(exploreDir).add(exploreDir).add(exploreDir);
                        }
                        // targetLoc = rc.getLocation().add(rc.getLocation().directionTo(homeEC).opposite());
                    }
                    else {
                        targetLoc = bestLatticeLoc;
                    }
                }
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
}
