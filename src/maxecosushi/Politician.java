package maxecosushi;

import battlecode.common.*;
import maxecosushi.utils.HashMap;
import maxecosushi.utils.HashTable;
import maxecosushi.utils.LinkedList;
import maxecosushi.utils.Node;

import static maxecosushi.Constants.*;

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
    static final int DEFEND_SLANDERER = 2;
    static final int DEFEND_SLANDERER_BUT_OPEN_TO_RUSH_CHANGE = 10;
    static final int ATTACK_EC = 3;
    static final int ATTACK_NEUTRAL_EC = 4;
    static final int SCOUT = 5;
    static final int PROTECT_BUFF_MUCK = 6;
    static Direction exploreDir = Direction.NORTH;
    static int role = DEFEND_SLANDERER;
    static MapLocation targetLoc = null;
    /** used for attack ec role, where to attack the ec */
    static MapLocation attackLoc = null;
    static int LATTICE_SIZE = 3;

    static MapLocation targetedEnemyLoc = null;

    static MapLocation protectLocation = null;
    static MapLocation protectedBuffMuckAttackLoc = null;
    /** radius of defence lattice */
    static int minDistAwayFromProtectLoc = 3;

    static int idOfMuckToProtect = -1;

    public static void setup() throws GameActionException {
        // find ec spawned from
        setHomeEC();
        if (homeEC == null) {
            // this means we were captured probably
            exploreDir = randomDirection();
        } else {
            exploreDir = homeEC.directionTo(rc.getLocation());
        }
        // first turn set flag to indiciate unit type
    }

    public static void handleFlag(int flag, boolean homeEC) throws GameActionException {
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
                break;
            case Comms.ATTACK_EC:
                switch (Comms.SIGNAL_TYPE_5BIT_MASK & flag) {
                    case Comms.ATTACK_EC:
                        if (homeEC) {
                            boolean needsnewTarget = false;
                            if (attackLoc != null && rc.canSenseLocation(attackLoc)) {
                                RobotInfo info = rc.senseRobotAtLocation(attackLoc);
                                if (info != null && info.team == myTeam) {
                                    needsnewTarget = true;
                                }
                            }

                            boolean acceptRole = false;
                            if (rc.getConviction() >= 100 && (role == SCOUT || role == DEFEND_SLANDERER_BUT_OPEN_TO_RUSH_CHANGE)) {
                                // if high conviction but was scout or defending slanderers, switch to an attacking role!
                                acceptRole = true;
                            }
                            if (turnCount < 2 || (needsnewTarget && role == ATTACK_EC) || acceptRole) {
                                role = ATTACK_EC;
                                attackLoc = Comms.readAttackECSignal(flag, rc);
                            }
                            break;
                        } else {
                            // is unit announcing this
                            if (role == PROTECT_BUFF_MUCK) {
                                protectedBuffMuckAttackLoc = Comms.readAttackECSignal(flag, rc);
                            }
                        }
                    case Comms.ATTACK_NEUTRAL_EC:
                        if (turnCount < 2) {
                            role = ATTACK_NEUTRAL_EC;
                            attackLoc = Comms.readAttackECSignal(flag, rc);
                        }
                        break;
                }

                break;
            case Comms.PROTECT_BUFF_MUCK:
                if (turnCount < 2 && homeEC) {
                    int idToProtect = Comms.readProtectBuffMuckSignal(flag);
                    idOfMuckToProtect = idToProtect;
                    role = PROTECT_BUFF_MUCK;
                }

        }
    }

    public static void run() throws GameActionException {
        if (exploreDir == null) {
            // for units that convert
            if (homeEC == null) {
                // this means we were captured probably
                exploreDir = randomDirection();
            } else {
                exploreDir = homeEC.directionTo(rc.getLocation());
            }
        }
        setFlagThisTurn = false;
        // control whether the unit will prefer to stay still as opposed to wigglign out
        // of tight space
        boolean absolutelydonotwiggle = false;
        if (rc.canGetFlag(homeECID)) {
            int homeECFlag = rc.getFlag(homeECID);
            handleFlag(homeECFlag, true);
        } else {
            // clean out this
            multiPartMessagesByBotID.remove(homeECID);
        }

        if (rc.getConviction() < 14) {
            role = SCOUT;
        }

        // determine location to try and protect
        if (homeEC != null) {
            protectLocation = homeEC;
        } else if (protectLocation == null) {
            protectLocation = rc.getLocation();
        }

        RobotInfo muckToProtect = null;

        if (role == PROTECT_BUFF_MUCK) {
            if (rc.canSenseRobot(idOfMuckToProtect)) {
                RobotInfo bot = rc.senseRobot(idOfMuckToProtect);
                // verify this is our bot lmao
                if (bot.team == myTeam) {
                    // targetLoc = bot.location;
                    muckToProtect = bot;
                }
            }
        }

        RobotInfo[] nearbyEnemyBots = rc.senseNearbyRobots(POLI_SENSE_RADIUS, oppTeam);
        RobotInfo[] nearbyFriendBots = rc.senseNearbyRobots(POLI_SENSE_RADIUS, myTeam);
        RobotInfo[] nearbyNeutralBots = rc.senseNearbyRobots(POLI_SENSE_RADIUS, Team.NEUTRAL);
        MapLocation locOfClosestFriendlyPoli = null;
        int distToClosestFriendlyPoli = 999999999;
        RobotInfo targetedEnemyMuck = null;
        RobotInfo closestEnemyMuck = null;
        int distToTargetedEnemyMuck = 9999999;
        int distToClosestEnemyMuck = 999999;

        // array[n] = number of friendlies within n r^2 distance
        int[] friendlyUnitsAtDistanceCount = new int[10];
        int[] oppUnitsAtDistanceCount = new int[10];
        int[] oppMuckUnitsAtDistanceCount = new int[10];
        int[] neutralUnitsAtDistanceCount = new int[10];
        // int[] oppSlandUnitsAtDistanceCount = new int[10];
        // int[] oppECUnitsAtDistanceCount = new int[10];
        // int[] oppPoliUnitsAtDistanceCount = new int[10];

        LinkedList<MapLocation> locsOfFriendSlands = new LinkedList<>();

        /** maps id to # of polis it is targeted by */
        HashMap<Integer, Integer> numberOfPolisTargetingMucks = new HashMap<>(30);

        // amount of influence nearby from other polis attacking the same EC
        int nearbyFirePower = 0;
        int nearbyEnemyFirePower = 0;
        int nearbyEnemyPolis = 0;
        int numberOfNearbyFriendlySlanderers = 0;
        RobotInfo highestTargetableBuffMuck = null;
        int highestTargetableBuffMuckConviction = 0;

        for (int i = nearbyFriendBots.length; --i >= 0;) {
            RobotInfo bot = nearbyFriendBots[i];
            int dist = rc.getLocation().distanceSquaredTo(bot.location);
            boolean withinDist = dist <= 9;
            if (withinDist) {
                friendlyUnitsAtDistanceCount[dist] += 1;
            }
            int flag = 0;

            if (rc.canGetFlag(bot.ID)) {
                flag = rc.getFlag(bot.ID);
                if (bot.type == RobotType.ENLIGHTENMENT_CENTER) {
                    handleFlag(flag, true);
                } else {
                    handleFlag(flag, false);
                }
            }
            // check if flag is slanderer flag
            if (flag == Comms.IMASLANDERERR || ((Comms.SIGNAL_TYPE_MASK & flag) == Comms.SLAND_SPOTTED_MUCK)) {
                locsOfFriendSlands.add(bot.location);
                if (homeEC != null) {
                    numberOfNearbyFriendlySlanderers += 1;
                    int distToEC = bot.location.distanceSquaredTo(protectLocation);
                    // consider cmparing with slands ec?
                    if (distToEC > minDistAwayFromProtectLoc) {
                        minDistAwayFromProtectLoc = (int) Math.pow(Math.sqrt(distToEC) + 2, 2);
                    }
                }
            } else if (bot.type == RobotType.POLITICIAN) {
                if (dist < distToClosestFriendlyPoli) {
                    distToClosestFriendlyPoli = dist;
                    locOfClosestFriendlyPoli = bot.location;
                }
                switch (Comms.SIGNAL_TYPE_MASK & flag) {
                    case Comms.TARGETED_MUCK:
                        int[] data = Comms.readTargetedMuckSignal(flag);
                        int id = data[0];
                        if (numberOfPolisTargetingMucks.contains(id)) {
                            // TODO: this can be optimized by moving the function out
                            int a = numberOfPolisTargetingMucks.get(id);
                            numberOfPolisTargetingMucks.setAlreadyContainedValue(id, a + 1);
                        } else {
                            numberOfPolisTargetingMucks.put(id, 1);
                        }
                        break;
                    case Comms.TARGETED_EC:
                        MapLocation targetedECLoc = Comms.readTargetedECSignal(flag, rc);
                        if (attackLoc != null && targetedECLoc.distanceSquaredTo(attackLoc) < 5) {
                            // same enemy, combine firepower
                            nearbyFirePower += bot.influence;
                        }
                        break;
                }

            } else if (bot.type == RobotType.ENLIGHTENMENT_CENTER) {
                if (homeEC == null) {
                    homeEC = bot.location;
                }
                handleFoundEC(bot);
            }
        }

        if (numberOfNearbyFriendlySlanderers == 0) {
            // move closer to base if we dont see any
            minDistAwayFromProtectLoc = 3;
        }

        RobotInfo nearestEnemyEC = null;
        int cloestsEnemyECDist = 99999999;
        for (int i = nearbyEnemyBots.length; --i >= 0;) {
            RobotInfo bot = nearbyEnemyBots[i];
            int dist = rc.getLocation().distanceSquaredTo(bot.location);
            boolean withinDist = dist <= 9;
            if (withinDist) {
                oppUnitsAtDistanceCount[dist] += 1;
            }
            if (bot.type == RobotType.MUCKRAKER) {
                if (withinDist) {
                    oppMuckUnitsAtDistanceCount[dist] += 1;
                }
                // check if muck is already completely suurrounded if its buff
                boolean mucksurrounded = true;
                if (bot.conviction >= BUFF_MUCK_THRESHOLD) {
                    for (Direction dir : DIRECTIONS) {
                        MapLocation check = bot.location.add(dir);
                        if (rc.canSenseLocation(check)) {
                            RobotInfo info = rc.senseRobotAtLocation(check);
                            // TOOD: check our team?
                            if (info == null || info.team == oppTeam) {
                                mucksurrounded = false;
                                break;
                            }
                        }
                    }
                    if (mucksurrounded && dist > 2) {
                        continue;
                    }
                    // if ()
                }
                if (dist < distToTargetedEnemyMuck) {
                    Integer num = numberOfPolisTargetingMucks.get(bot.ID);
                    if (num == null || num < 1 || bot.conviction >= BUFF_MUCK_THRESHOLD) {
                        distToTargetedEnemyMuck = dist;
                        targetedEnemyMuck = bot;
                    }
                }
                if (dist < distToClosestEnemyMuck) {
                    distToClosestEnemyMuck = dist;
                    closestEnemyMuck = bot;
                }
                if (bot.conviction >= BUFF_MUCK_THRESHOLD) {
                    if (bot.conviction > highestTargetableBuffMuckConviction) {
                        highestTargetableBuffMuck = bot;
                    }
                }
            } else if (bot.type == RobotType.ENLIGHTENMENT_CENTER) {
                handleFoundEC(bot);
                if (dist < cloestsEnemyECDist) {
                    cloestsEnemyECDist = dist;
                    nearestEnemyEC = bot;
                }

            } else if (bot.type == RobotType.POLITICIAN && dist <= 9) {
                nearbyEnemyFirePower += bot.influence;
                nearbyEnemyPolis += 1;
            }

        }

        for (int i = nearbyNeutralBots.length; --i >= 0;) {
            RobotInfo bot = nearbyNeutralBots[i];
            int dist = rc.getLocation().distanceSquaredTo(bot.location);
            // neutral team
            boolean withinDist = dist <= 9;
            if (withinDist) {
                neutralUnitsAtDistanceCount[dist] += 1;
            }
            if (bot.type == RobotType.ENLIGHTENMENT_CENTER) {
                handleFoundEC(bot);
            }
        }

        MapLocation currLoc = rc.getLocation();
        MapLocation bestLatticeLoc = null;
        int bestLatticeLocVal = Integer.MIN_VALUE;
        findbestLatticeLoc: {
            if (homeEC == null) {
                break findbestLatticeLoc;
            }
            if (currLoc.x % LATTICE_SIZE == 0 && currLoc.y % LATTICE_SIZE == 0
                    && currLoc.distanceSquaredTo(protectLocation) > minDistAwayFromProtectLoc) {
                bestLatticeLoc = currLoc;
                bestLatticeLocVal = -bestLatticeLoc.distanceSquaredTo(protectLocation);
            }

            for (int i = 0; ++i < BFS25.length;) {
                int[] deltas = BFS25[i];

                MapLocation checkLoc = new MapLocation(currLoc.x + deltas[0], currLoc.y + deltas[1]);
                if (rc.onTheMap(checkLoc)) {
                    if (checkLoc.x % LATTICE_SIZE == 0 && checkLoc.y % LATTICE_SIZE == 0
                            && checkLoc.distanceSquaredTo(protectLocation) > minDistAwayFromProtectLoc) {
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
        }

        boolean succesfullyCapturedEC = false;

        boolean attackmodebutneednewtarget = false;
        if (isDefending()) {
            if (targetedEnemyMuck != null || closestEnemyMuck != null) {
                boolean targetBuffMuck = false;
                if (rc.getConviction() > STANDARD_DEFEND_POLI_CONVICTION && highestTargetableBuffMuck != null) {
                    // a buff poli to fight buff mucks
                    targetBuffMuck = true;
                    targetedEnemyMuck = highestTargetableBuffMuck;
                }

                // find an optimal empower distance that destroys as many muckrakers as possible
                int mucksCountInRadius = 0;
                int friendlyUnitsInRadius = 0;
                int oppUnitsInRadius = 0;
                int neutralsInRadius = 0;
                int maxMucksDestroyed = 0;
                int optimalEmpowerRadius = -1;
                int highestInfPerUnit = 0;
                for (int i = 1; i <= 9; i++) {
                    mucksCountInRadius += oppMuckUnitsAtDistanceCount[i];
                    oppUnitsInRadius += oppUnitsAtDistanceCount[i];
                    neutralsInRadius += neutralUnitsAtDistanceCount[i];
                    friendlyUnitsInRadius += friendlyUnitsAtDistanceCount[i];
                    int n = (oppUnitsInRadius + friendlyUnitsInRadius + neutralsInRadius);
                    if (mucksCountInRadius > 0) {

                        int speechInfluencePerUnit = calculatePoliticianEmpowerConviction(myTeam, rc.getConviction(), 0)
                                / n;
                        if (targetBuffMuck && rc.getLocation().distanceSquaredTo(targetedEnemyMuck.location) <= i) {
                            // if targeting buff muck and in range, see how much conictionwe can inflict now
                            // this is find to empower if at least use half our conviction correctly or we
                            // eliminate the enemy unit
                            if ((speechInfluencePerUnit >= rc.getConviction() / 2
                                    || speechInfluencePerUnit >= targetedEnemyMuck.conviction)
                                    && speechInfluencePerUnit > highestInfPerUnit) {
                                optimalEmpowerRadius = i;
                                highestInfPerUnit = speechInfluencePerUnit;
                            }
                        } else {
                            if (speechInfluencePerUnit >= 2) {
                                if (mucksCountInRadius > maxMucksDestroyed) {
                                    maxMucksDestroyed = mucksCountInRadius;
                                    optimalEmpowerRadius = i;
                                }
                            }
                        }
                    }
                }
                if (targetBuffMuck == true) {
                    if (optimalEmpowerRadius != -1 && rc.canEmpower(optimalEmpowerRadius)) {
                        rc.empower(optimalEmpowerRadius);
                    } else {
                        targetLoc = rc.getLocation().add(rc.getLocation().directionTo(targetedEnemyMuck.location));
                    }
                } else {
                    // logic for targeting cheap mucks
                    // target if slanderer in danger or we can hit 2 at a time
                    boolean slandererInDanger = false;
                    // TODO: optimize to kill more mucks if not in danger and we see more than 2
                    // relatively close
                    RobotInfo[] muckLocsToCheck = new RobotInfo[] { targetedEnemyMuck, closestEnemyMuck };

                    checkIfSlandererInDanger: {
                        for (RobotInfo muckInfo : muckLocsToCheck) {
                            if (muckInfo == null) {
                                continue;
                            }
                            MapLocation muckLoc = muckInfo.location;

                            if (homeEC != null) {
                                int muckDistToHome = muckLoc.distanceSquaredTo(homeEC);
                                int myDistToHome = rc.getLocation().distanceSquaredTo(homeEC);
                                if ((myDistToHome >= muckDistToHome && muckDistToHome < 30) || muckDistToHome < 40) {
                                    slandererInDanger = true;
                                    break checkIfSlandererInDanger;
                                } 
                            }
                            Node<MapLocation> currNode = locsOfFriendSlands.head;
                            while (currNode != null) {
                                if (currNode.val.distanceSquaredTo(muckLoc) <= MUCKRAKER_ACTION_RADIUS + 100) {
                                    slandererInDanger = true;
                                    break checkIfSlandererInDanger;
                                }
                                currNode = currNode.next;
                            }
                        }
                    }

                    // we keep following mucks if we cant optimally empower muckrakers or no
                    // slanderers in danger and we can't do a 2 birds one stone.
                    if (optimalEmpowerRadius == -1 || (!slandererInDanger && maxMucksDestroyed < 2)) {
                        // go towards closest muckraker in hope of more optimal empowering.
                        if (targetedEnemyMuck != null) {
                            targetLoc = rc.getLocation().add(rc.getLocation().directionTo(targetedEnemyMuck.location));
                            if (targetedEnemyMuck.conviction >= BUFF_MUCK_THRESHOLD) {
                                absolutelydonotwiggle = true;
                            }
                        } else if (closestEnemyMuck != null && closestEnemyMuck.conviction >= BUFF_MUCK_THRESHOLD) {
                            // move closer if it's probably a buff muck or something
                            targetLoc = rc.getLocation().add(rc.getLocation().directionTo(closestEnemyMuck.location));
                            absolutelydonotwiggle = true;
                        } else {
                            // lastly do latticing
                            // move away from EC...
                            // bottom code copied from else
                            if (homeEC != null && rc.getLocation().distanceSquaredTo(homeEC) <= 2) {
                                targetLoc = rc.getLocation().add(rc.getLocation().directionTo(homeEC).opposite());
                            } else {
                                if (bestLatticeLoc == null) {
                                    targetLoc = rc.getLocation().add(exploreDir).add(exploreDir).add(exploreDir);
                                    if (!rc.onTheMap(targetLoc)) {
                                        exploreDir = exploreDir.rotateLeft().rotateLeft();
                                        targetLoc = rc.getLocation().add(exploreDir).add(exploreDir).add(exploreDir);
                                    }
                                } else {
                                    targetLoc = bestLatticeLoc;
                                }
                            }

                        }
                    } else {
                        if (rc.canEmpower(optimalEmpowerRadius)) {
                            rc.empower(optimalEmpowerRadius);
                        }
                    }
                }
            } else {
                // move away from EC...
                if (homeEC != null && rc.getLocation().distanceSquaredTo(homeEC) <= 2) {
                    targetLoc = rc.getLocation().add(rc.getLocation().directionTo(homeEC).opposite());
                } else {
                    if (bestLatticeLoc == null) {
                        targetLoc = rc.getLocation().add(exploreDir).add(exploreDir).add(exploreDir);
                        if (!rc.onTheMap(targetLoc)) {
                            exploreDir = exploreDir.rotateLeft().rotateLeft();
                            targetLoc = rc.getLocation().add(exploreDir).add(exploreDir).add(exploreDir);
                        }
                    } else {
                        targetLoc = bestLatticeLoc;
                    }
                }

            }
        } else if (role == ATTACK_EC || role == ATTACK_NEUTRAL_EC) {
            targetLoc = attackLoc;
            int distToEC = rc.getLocation().distanceSquaredTo(attackLoc);

            if (rc.canEmpower(1)) {
                if (distToEC <= POLI_ACTION_RADIUS) {
                    // if not enemy anymore, just supply the EC with eco

                    RobotInfo enemyEC = rc.senseRobotAtLocation(attackLoc);
                    if ((enemyEC == null || enemyEC.type != RobotType.ENLIGHTENMENT_CENTER) && nearestEnemyEC != null) {
                        // check surroundings?!??!?
                        attackLoc = nearestEnemyEC.location;
                        enemyEC = nearestEnemyEC;
                    }
                    if (enemyEC != null) {
                        distToEC = rc.getLocation().distanceSquaredTo(enemyEC.location);
                    }

                    if (enemyEC == null) {
                        // System.out.println("attackLoc: " + attackLoc + " - myloc" +
                        // rc.getLocation());
                        // shouldnt happen...
                    } else if (enemyEC.team == myTeam) {
                        // if converted to our team, stand by to recapture if necessary
                        if (distToEC < 20) {
                            Direction awayDir = enemyEC.location.directionTo(rc.getLocation());
                            targetLoc = rc.getLocation().add(awayDir).add(awayDir);
                            attackmodebutneednewtarget = true;
                        }
                        // if (distToEC == 1) {
                        // rc.empower(1);
                        // }
                    } else {
                        // measure if worth
                        int friendlyUnitsInRadius = 0;
                        int oppUnitsInRadius = 0;
                        int neutralsInRadius = 0;
                        for (int i = 1; i <= 9; i++) {
                            oppUnitsInRadius += oppUnitsAtDistanceCount[i];
                            friendlyUnitsInRadius += friendlyUnitsAtDistanceCount[i];
                            neutralsInRadius += neutralUnitsAtDistanceCount[i];
                            int n = (oppUnitsInRadius + friendlyUnitsInRadius + neutralsInRadius);
                            if (distToEC <= i) {
                                if (enemyEC.team == Team.NEUTRAL) {
                                    if (i > 3 && n > 0) {
                                        continue;
                                    }
                                }
                                int speechInfluencePerUnit = calculatePoliticianEmpowerConviction(myTeam,
                                        rc.getConviction() + (int) (nearbyFirePower), 0) / n;

                                double discountFactor = 0.5;
                                // System.out.println("nearby fire " + nearbyFirePower + " - buff" +
                                // rc.getEmpowerFactor(myTeam, 0));
                                // System.out.println("Dist " + i +" - per " + speechInfluencePerUnit + " - ec
                                // conv" + enemyEC.conviction + " - nb " + nearbyEnemyFirePower);
                                if (speechInfluencePerUnit >= enemyEC.conviction
                                        + (nearbyEnemyFirePower - GameConstants.EMPOWER_TAX * nearbyEnemyPolis)
                                                * discountFactor) {
                                    rc.empower(i);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        } else if (role == SCOUT) {
            targetLoc = rc.getLocation().add(exploreDir).add(exploreDir).add(exploreDir);
            Direction[] dirs = new Direction[] { exploreDir.rotateLeft(), exploreDir.rotateRight(),
                    exploreDir.rotateLeft().rotateLeft(), exploreDir.rotateRight().rotateRight(),
                    exploreDir.rotateLeft().rotateLeft().rotateLeft(),
                    exploreDir.rotateRight().rotateRight().rotateRight(), exploreDir.opposite() };
            int i = -1;
            while (!rc.onTheMap(targetLoc)) {
                i++;
                targetLoc = rc.getLocation().add(dirs[i]).add(dirs[i]).add(dirs[i]);

            }
            if (i != -1) {
                exploreDir = dirs[i];
            }
        } else if (role == PROTECT_BUFF_MUCK) {
            // System.out.println("Im at " + rc.getLocation() + " protecting bot: " + idOfMuckToProtect);
            if (muckToProtect != null) {
                targetLoc = protectedBuffMuckAttackLoc;
                if (rc.getLocation().distanceSquaredTo(muckToProtect.location) > 3) {
                    targetLoc = rc.getLocation();
                }
                // check expose range
                int myConviction = rc.getConviction();
                int distToMuck = rc.getLocation().distanceSquaredTo(muckToProtect.location);
                RobotInfo[] enemyBots = rc.senseNearbyRobots(muckToProtect.location, 2, oppTeam);
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

                if (muckShouldHeal(muckToProtect.influence, muckToProtect.conviction, potentialMaxDamageInOneTurn)) {
                    // System.out.println("trying to heal");
                    int friendlyUnitsInRadius = 0;
                    int oppUnitsInRadius = 0;
                    int neutralsInRadius = 0;
                    for (int i = 1; i <= 9; i++) {
                        oppUnitsInRadius += oppUnitsAtDistanceCount[i];
                        friendlyUnitsInRadius += friendlyUnitsAtDistanceCount[i];
                        neutralsInRadius += neutralUnitsAtDistanceCount[i];
                        int n = (oppUnitsInRadius + friendlyUnitsInRadius + neutralsInRadius);

                        if (distToMuck <= i) {
                            // TODO: allow inflence to be diluted onto buff polis close to the muckraker
                            // woudl need to count this separately
                            int speechInfluencePerUnit = calculatePoliticianEmpowerConviction(myTeam,
                                    rc.getConviction(), 0) / n;
                            if (speechInfluencePerUnit >= rc.getConviction() / 2 || muckToProtect.conviction + speechInfluencePerUnit > potentialMaxDamageInOneTurn) {
                                rc.empower(i);
                                break;
                            }
                        }
                    }

                }
            } else {
                // no muck unit found, go towards enemy ec
                targetLoc = protectedBuffMuckAttackLoc;
            }
            // if any of the above fails, head towards attack location
        }

        int pauseSpecialMessageQueue = 1;
        if (role == ATTACK_EC) {
            pauseSpecialMessageQueue = 6;
        }
        // if we have map dimensions, send out scouting info
        if (ECDetailsToSend.size > 0) {
            Node<ECDetails> ecDetailsNode = ECDetailsToSend.dequeue();
            int signal = Comms.getFoundECSignal(ecDetailsNode.val.location, ecDetailsNode.val.teamind,
                    ecDetailsNode.val.lastKnownConviction);
            specialMessageQueue.add(signal);
            foundECLocHashes.remove(Comms.encodeMapLocation(ecDetailsNode.val.location));
        }

        int currRound = rc.getRoundNum();
        // handle flags that arernt corner stuff
        if (specialMessageQueue.size > 0 && currRound % pauseSpecialMessageQueue == 0) {
            setFlag(specialMessageQueue.dequeue().val);
        }

        if (!setFlagThisTurn) {
            // System.out.println("targeting " + targetedEnemyMuck);
            if (role == DEFEND_SLANDERER && rc.getConviction() > STANDARD_DEFEND_POLI_CONVICTION) {
                setFlag(Comms.IM_STOPPING_BUFF_MUCK);
            } else if (role == DEFEND_SLANDERER && targetedEnemyMuck != null && turnCount >= 10) {
                if (targetedEnemyMuck.conviction < BUFF_MUCK_THRESHOLD) {
                    if (rc.getConviction() <= STANDARD_DEFEND_POLI_CONVICTION) {
                        MapLocation myLoc = rc.getLocation();
                        int dx = targetedEnemyMuck.location.x - myLoc.x;
                        int dy = targetedEnemyMuck.location.y - myLoc.y;
                        setFlag(Comms.getTargetedMuckSignal(targetedEnemyMuck.ID, dx, dy));
                    }
                } else {
                    // if buff, send spotted signal
                    setFlag(Comms.getSpottedMuckSignal(targetedEnemyMuck.location, targetedEnemyMuck.conviction));
                }

            } else if (role == ATTACK_EC) {
                if (attackmodebutneednewtarget) {
                    // shout i have no target lol
                    int sig = Comms.I_NEED_EC_ATTACK_LOC;
                    setFlag(sig);
                }
                else {
                    int sig = Comms.getTargetedECSignal(attackLoc);
                    setFlag(sig);
                }
            } else if (role == PROTECT_BUFF_MUCK && idOfMuckToProtect != -1) {
                int sig = Comms.getProtectBuffMuckSignal(idOfMuckToProtect);
                setFlag(sig);
            }
        }
        if (!setFlagThisTurn) {
            rc.setFlag(0);
        }

        boolean canWiggle = true;
        
        if (role == ATTACK_EC && rc.getLocation().distanceSquaredTo(attackLoc) <= 5) {
            canWiggle = false;
        } else if (role == PROTECT_BUFF_MUCK && muckToProtect != null) {
            canWiggle = false;
        }
        if (absolutelydonotwiggle) {
            // do completely greedy movement
            if (rc.isReady() && targetLoc != null) {
                Direction greedyDir = Direction.CENTER;
                int bestDist = rc.getLocation().distanceSquaredTo(targetLoc);
                for (Direction dir : DIRECTIONS) {
                    if (rc.canMove(dir)) {
                        MapLocation newloc = rc.getLocation().add(dir);
                        if (rc.onTheMap(newloc) && rc.senseRobotAtLocation(newloc) == null) {
                            int thisDist = newloc.distanceSquaredTo(targetLoc);
                            // minimize dist
                            if (thisDist < bestDist) {
                                greedyDir = dir;
                                bestDist = thisDist;
                            }
                        }
                    }
                }
                if (greedyDir != Direction.CENTER) {
                    rc.move(greedyDir);
                }
            }
        } else {
            if (rc.isReady() && targetLoc != null) {
                Direction dir = getNextDirOnPath(targetLoc);
                if (dir != Direction.CENTER && rc.canMove(dir)) {
                    rc.move(dir);
                } else if (!rc.getLocation().equals(targetLoc) && canWiggle) {
                    // wiggle out if perhaps stuck
                    for (Direction wiggleDir : DIRECTIONS) {
                        // MapLocation loc = rc.getLocation().add(wiggleDir);
                        if (rc.canMove(wiggleDir)) {
                            rc.move(wiggleDir);
                            break;
                        }
                    }
                }
            }
        }
    }
    private static boolean isDefending() {
        return role == DEFEND_SLANDERER_BUT_OPEN_TO_RUSH_CHANGE || role == DEFEND_SLANDERER;
    }
}
