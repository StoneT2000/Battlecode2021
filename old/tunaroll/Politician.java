package tunaroll;

import battlecode.common.*;
import tunaroll.utils.HashMap;
import tunaroll.utils.HashTable;
import tunaroll.utils.LinkedList;
import tunaroll.utils.Node;

import static tunaroll.Constants.*;

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
    static final int DEFEND_SLANDERER = 2;
    static final int ATTACK_EC = 3;
    static Direction exploreDir = Direction.NORTH;
    static int role = DEFEND_SLANDERER;
    static MapLocation targetLoc = null;
    /** used for attack ec role, where to attack the ec */
    static MapLocation attackLoc = null;
    static int LATTICE_SIZE = 3;

    static MapLocation targetedEnemyLoc = null;

    static MapLocation protectLocation = null;
    static int minDistAwayFromProtectLoc = 3;

    public static void setup() throws GameActionException {
        // find ec spawned from
        setHomeEC();
        if (homeEC == null) {
            // role = SACRIFICE;
            // this means we were captured probably
            exploreDir = randomDirection();
        } else {
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
            case Comms.ATTACK_EC:
                if (turnCount < 2) {
                    role = ATTACK_EC;
                    attackLoc = Comms.readAttackECSignal(flag, rc);
                }
                break;

        }
    }

    public static void run() throws GameActionException {
        setFlagThisTurn = false;
        if (rc.canGetFlag(homeECID)) {
            int homeECFlag = rc.getFlag(homeECID);
            handleFlag(homeECFlag);
        } else {
            // clean out this
            multiPartMessagesByBotID.remove(homeECID);
        }

        // determine location to try and protect
        if (homeEC != null) {
            protectLocation = homeEC;
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

        for (int i = nearbyFriendBots.length; --i >= 0;) {
            RobotInfo bot = nearbyFriendBots[i];
            int dist = rc.getLocation().distanceSquaredTo(bot.location);
            boolean withinDist = dist <= 9;
            if (withinDist) {
                friendlyUnitsAtDistanceCount[dist] += 1;
            }
            if (rc.canGetFlag(bot.ID) && rc.getFlag(bot.ID) == Comms.IMASLANDERERR) {
                locsOfFriendSlands.add(bot.location);
                if (homeEC != null) {
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
                int flag = rc.getFlag(bot.ID);
                switch (Comms.SIGNAL_TYPE_MASK & flag) {
                    case Comms.TARGETED_MUCK:
                        int id = Comms.readTargetedMuckSignal(flag);
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
                        if (targetedECLoc.equals(attackLoc)) {
                            // same enemy, combine firepower
                            nearbyFirePower += bot.influence;
                        }
                        break;
                }

            } else if (bot.type == RobotType.ENLIGHTENMENT_CENTER) {
                if (homeEC == null) {
                    homeEC = bot.location;
                }
                // handleFoundEC(bot);
            }
        }

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
                if (dist < distToTargetedEnemyMuck) {
                    Integer num = numberOfPolisTargetingMucks.get(bot.ID);
                    if (num == null || num < 1) {
                        distToTargetedEnemyMuck = dist;
                        targetedEnemyMuck = bot;
                    }
                }
                if (dist < distToClosestEnemyMuck) {
                    distToClosestEnemyMuck = dist;
                    closestEnemyMuck = bot;
                }
            } else if (bot.type == RobotType.ENLIGHTENMENT_CENTER) {
                handleFoundEC(bot);
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
        if (homeEC != null) {
            if (rc.getLocation().distanceSquaredTo(homeEC) == 1) {
                // if buff is high, suicide?
                if (rc.canEmpower(1) && calculatePoliticianEmpowerConviction(myTeam, rc.getConviction(), 0)
                        / 4 > rc.getConviction() * 2) {
                    rc.empower(1);
                }
            } else if (rc.getLocation().distanceSquaredTo(homeEC) == 2) {
                // if buff is high, suicide?
                if (rc.canEmpower(2) && calculatePoliticianEmpowerConviction(myTeam, rc.getConviction(), 0)
                        / 6 > rc.getConviction() * 2) {
                    rc.empower(2);
                }
            }
        }

        boolean succesfullyCapturedEC = false;

        if (role == DEFEND_SLANDERER) {
            if (targetedEnemyMuck != null) {

                // find an optimal empower distance that destroys as many muckrakers as possible
                int mucksCountInRadius = 0;
                int friendlyUnitsInRadius = 0;
                int oppUnitsInRadius = 0;
                int neutralsInRadius = 0;
                int maxMucksDestroyed = 0;
                int optimalEmpowerRadius = -1;
                for (int i = 1; i <= 9; i++) {
                    mucksCountInRadius += oppMuckUnitsAtDistanceCount[i];
                    oppUnitsInRadius += oppUnitsAtDistanceCount[i];
                    neutralsInRadius += neutralUnitsAtDistanceCount[i];
                    friendlyUnitsInRadius += friendlyUnitsAtDistanceCount[i];
                    int n = (oppUnitsInRadius + friendlyUnitsInRadius + neutralsInRadius);
                    if (mucksCountInRadius > 0) {
                        int speechInfluencePerUnit = calculatePoliticianEmpowerConviction(myTeam, rc.getConviction(), 0)
                                / n;
                        if (speechInfluencePerUnit >= 2) {
                            if (mucksCountInRadius > maxMucksDestroyed) {
                                maxMucksDestroyed = mucksCountInRadius;
                                optimalEmpowerRadius = i;
                            }
                        }
                    }
                }

                Node<MapLocation> currNode = locsOfFriendSlands.dequeue();
                boolean slandererInDanger = false;
                // TODO: optimize to kill more mucks if not in danger and we see more than 2
                // relatively close
                while (currNode != null) {
                    if (currNode.val.distanceSquaredTo(targetedEnemyMuck.location) <= MUCKRAKER_ACTION_RADIUS + 10) {
                        slandererInDanger = true;
                        break;
                    }
                    currNode = locsOfFriendSlands.dequeue();
                }

                // we keep following mucks if we cant optimally empower muckrakers or no
                // slanderers in danger and we can't do a 2 birds one stone.
                if (optimalEmpowerRadius == -1 || (!slandererInDanger && maxMucksDestroyed < 2)) {
                    // go towards closest muckraker in hope of more optimal empowering.
                    targetLoc = rc.getLocation().add(rc.getLocation().directionTo(targetedEnemyMuck.location));
                } else {
                    if (rc.canEmpower(optimalEmpowerRadius)) {
                        rc.empower(optimalEmpowerRadius);
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
        } else if (role == ATTACK_EC) {
            targetLoc = attackLoc;
            int distToEC = rc.getLocation().distanceSquaredTo(attackLoc);
            if (rc.canEmpower(1)) {
                if (distToEC <= POLI_ACTION_RADIUS) {
                    RobotInfo enemyEC = rc.senseRobotAtLocation(attackLoc);
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
                            int speechInfluencePerUnit = calculatePoliticianEmpowerConviction(myTeam,
                                    rc.getConviction() + nearbyFirePower / 2, 0) / n;


                            double discountFactor = 0.5;
                            if (speechInfluencePerUnit >= enemyEC.conviction + (nearbyEnemyFirePower - GameConstants.EMPOWER_TAX * nearbyEnemyPolis) * discountFactor) {
                                rc.empower(i);
                                break;
                            }
                        }
                    }
                }
            }
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
        if (specialMessageQueue.size > 0) {
            setFlag(specialMessageQueue.dequeue().val);
        }

        if (!setFlagThisTurn) {
            // System.out.println("targeting " + targetedEnemyMuck);
            if (role == DEFEND_SLANDERER && targetedEnemyMuck != null) {
                setFlag(Comms.getTargetedMuckSignal(targetedEnemyMuck.ID));
            } else if (role == ATTACK_EC) {
                int sig = Comms.getTargetedECSignal(attackLoc);
                setFlag(sig);
            }
        }
        if (!setFlagThisTurn) {
            rc.setFlag(0);
        }

        boolean canWiggle = true;
        if (role == ATTACK_EC && rc.getLocation().distanceSquaredTo(attackLoc) <= 5) {
            canWiggle = false;
        }
        if (rc.isReady()) {
            Direction dir = getNextDirOnPath(targetLoc);
            if (dir != Direction.CENTER) {
                rc.move(dir);
            } else if (!rc.getLocation().equals(targetLoc) && canWiggle) {
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
