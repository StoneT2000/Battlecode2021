package slandertest;

import battlecode.common.*;
import slandertest.utils.*;

import static slandertest.Constants.*;

public class EnlightmentCenter extends RobotPlayer {
    static int lastScoutBuildDirIndex = -1;
    static int lastRushBuildIndex = -1;
    static final int BUILD_SCOUTS = 0;
    static final int NORMAL = 1;
    static int role = NORMAL;

    // offsets are also "lowMapX/Y"
    static int offsetx = Integer.MAX_VALUE;
    static int offsety = Integer.MAX_VALUE;
    static int mapWidth = -1;
    static int mapHeight = -1;
    static int highMapX = Integer.MIN_VALUE;
    static int highMapY = Integer.MIN_VALUE;

    static HashTable<Integer> ecIDs = new HashTable<>(10);
    static HashTable<Integer> muckrakerIDs = new HashTable<>(50);
    // TODO: when they convert, we need to remove that id and put new one
    static HashTable<Integer> slandererIDs = new HashTable<>(50);
    static HashTable<Integer> politicianIDs = new HashTable<>(20);

    static class Stats {
        static int muckrakersBuilt = 0;
        static int politiciansBuilt = 0;
        static int slanderersBuilt = 0;
    }

    public static void setup() throws GameActionException {
        boolean firstEC = rc.getTeamVotes() == 0;
        // if (firstEC) {
        // // do scout building
        // role = BUILD_SCOUTS;
        // lastScoutBuildDirIndex = -1;
        // // rc.setFlag(0);
        // } else {
        // // rc.setFlag(rc.getID());
        // }
        ecIDs.add(rc.getID());
    }

    public static void run() throws GameActionException {
        // do smth with rc
        rc.bid(1);
        System.out.println("TURN: " + turnCount + " | EC At " + rc.getLocation() + " - Influence: " + rc.getInfluence()
                + " - Conviction: " + rc.getConviction() + " - CD: " + rc.getCooldownTurns() + " - ROLE: " + role + " - Units Controlled EC: " + ecIDs.size
                + ", S: " + slandererIDs.size + ", P: " + politicianIDs.size + ", M: " + muckrakerIDs.size);

        // global comms code independent of role

        // iterate over all known units, remove if they arent there anymore
        ecIDs.resetIterator();
        muckrakerIDs.resetIterator();
        slandererIDs.resetIterator();
        politicianIDs.resetIterator();

        Node<Integer> currIDNode = muckrakerIDs.next();
        LinkedList<Integer> muckrakerIDsToRemove = new LinkedList<>();
        while (currIDNode != null) {
            if (rc.canGetFlag(currIDNode.val)) {
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
                }

            } else {
                // unit dead?
                muckrakerIDsToRemove.add(currIDNode.val);
            }
            currIDNode = muckrakerIDs.next();
        }

        while (true) {
            Node<Integer> node = muckrakerIDsToRemove.dequeue();
            if (node == null)
                break;
            else {
                muckrakerIDs.remove(node.val);
            }
        }

        RobotInfo[] nearbyBots = rc.senseNearbyRobots();

        int enemyPoliticianConvictionNearby = -1;
        for (int i = nearbyBots.length; --i >= 0;) {
            RobotInfo bot = nearbyBots[i];
            if (bot.team == myTeam && bot.type == RobotType.MUCKRAKER) {
                int flag = rc.getFlag(bot.ID);
            }
            else if (bot.team == oppTeam && bot.type == RobotType.POLITICIAN) {
                int c = calculatePoliticianEmpowerConviction(oppTeam, bot.conviction);
                enemyPoliticianConvictionNearby += c;
            }
        }

        /**
         * If mapheight and width resolved o some valid value [32, 64], then send out
         * signals to everyone OPTIMIZATION: do this only when we build new units, or
         * maybe if nearby unit requests this information
         */
        if (mapHeight >= 32 && mapWidth >= 32) {
            System.out.println("Map Details: Offsets: (" + offsetx + ", " + offsety + ") - Width: " + mapWidth
                    + " - Height: " + mapHeight);
            if (turnCount % 2 == 0) {
                int sig = Comms.getMapOffsetSignalXWidth(offsetx, mapWidth);
                rc.setFlag(sig);
            } else {
                int sig = Comms.getMapOffsetSignalYHeight(offsety, mapHeight);
                rc.setFlag(sig);
            }
        }

        switch (role) {
            case BUILD_SCOUTS:
                // TODO: handle edge cases if diagonals are blocked
                if (lastScoutBuildDirIndex < 3 && rc.isReady()) {
                    lastScoutBuildDirIndex = (lastScoutBuildDirIndex + 1);
                    Direction dir = DIAG_DIRECTIONS[lastScoutBuildDirIndex];
                    MapLocation buildLoc = rc.getLocation().add(dir);
                    if (rc.onTheMap(buildLoc)) {
                        RobotInfo bot = rc.senseRobotAtLocation(buildLoc);
                        if (bot == null) {
                            // flag of 0 is default no signal value flag of 1-4 represents build direction
                            rc.setFlag(lastScoutBuildDirIndex + 1);
                            rc.buildRobot(RobotType.MUCKRAKER, dir, 1);
                            Stats.muckrakersBuilt += 1;
                            // add new id
                            RobotInfo newbot = rc.senseRobotAtLocation(buildLoc);
                            muckrakerIDs.add(newbot.ID);
                        }
                    }
                }
                break;
            case NORMAL:
                // TODO Calculate this
                int dilutingUnits = 2;
                if (enemyPoliticianConvictionNearby / dilutingUnits > rc.getConviction()) {
                    for (Direction dir : DIRECTIONS) {
                        MapLocation buildLoc = rc.getLocation().add(dir);
                        if (rc.canBuildRobot(RobotType.POLITICIAN, dir, rc.getConviction())) {
                            rc.buildRobot(RobotType.POLITICIAN, dir, rc.getConviction());
                            RobotInfo newbot = rc.senseRobotAtLocation(buildLoc);
                            politicianIDs.add(newbot.ID);
                            break;
                        }
                    }
                }
                else {
                
                    // otherwise spam muckrakers wherever possible
                    for (int i = 0; i < 8; i++) {
                        lastRushBuildIndex = (lastRushBuildIndex + 1) % DIRECTIONS.length;
                        Direction dir = DIRECTIONS[lastRushBuildIndex];
                        MapLocation buildLoc = rc.getLocation().add(dir);
                        if (rc.canBuildRobot(RobotType.SLANDERER, dir, 100)) {
                            rc.buildRobot(RobotType.SLANDERER, dir, 100);
                            RobotInfo newbot = rc.senseRobotAtLocation(buildLoc);
                            muckrakerIDs.add(newbot.ID);
                            break;
                        }
                    }
                }
                break;
        }
        // if (rc.canBuildRobot(RobotType.MUCKRAKER, Direction.NORTH, 1)) {
        // rc.buildRobot(RobotType.MUCKRAKER, Direction.NORTH, 1);
        // RobotInfo[] nearbyBots = rc.senseNearbyRobots();

        // }
    }
}
