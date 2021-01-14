package salmonroll;

import battlecode.common.*;
import static salmonroll.Constants.*;

public class Comms {
    // reserve first few bits for signal type, here we reserve first 4
    // Optimization: use less bits and use of type of bot bearing that flag as a signal discriminator as well
    /**
     * reserved 4 bits for differentiating signal type
     * mask to retrieve the signal type
     */
    public static final int SIGNAL_TYPE_MASK = 0xf00000;
    public static final int SIGNAL_TYPE_5BIT_MASK = 0xf80000;
    /** mask used to get all of the content of a signal/flag */
    public static final int SIGNAL_MASK = 0x0fffff;
    public static final int CORNER_LOC_X = 0x100000;
    public static final int CORNER_LOC_Y = 0x200000;
    public static final int MAP_OFFSET_X_AND_WIDTH = 0x300000;
    public static final int MAP_OFFSET_Y_AND_HEIGHT = 0x400000;
    /** bits 4-5 for unit type, rest for id */
    public static final int BUILT_UNIT = 0x500000;
    public static final int POLI_SACRIFICE = 0x600000;
    // TODO: add scouting on EC influence each turn...
    public static final int FOUND_EC = 0x700000;
    // shout details about self
    public static final int UNIT_DETAILS = 0x800000;
    // cram more single message signals that don't require much space
    public static final int SMALL_SIGNAL = 0x900000;
    // small signals:
    public static final int IMASLANDERERR = 0x900001;
    public static final int GO_SCOUT = 0x900002;


    public static final int ATTACK_EC = 0xa00000;

    // some long hash map locs in event we don't have map dims
    // this thing below is for checking if the signal shoudl be proccessed as a found ec signal

    // takes 14 bits of space
    public static int encodeMapLocation(MapLocation loc) {
        return (loc.x % 128) * 128 + loc.y % 128;
    }
    public static MapLocation decodeMapLocation(int hash, RobotController rc) {
        int y = hash % 128;
        int x = (hash / 128) % 128;
        // figure out which is correct
        MapLocation curr = rc.getLocation();
        int offsetX128 = curr.x / 128;
        int offsetY128 = curr.y / 128;;

        MapLocation actual = new MapLocation(offsetX128 * 128 + x, offsetY128 * 128 + y);
        MapLocation alt = actual.translate(-128, 0);
        if (curr.distanceSquaredTo(alt) <= actual.distanceSquaredTo(actual)) {
            actual = alt;
        }
        alt = actual.translate(128, 0);
        if (curr.distanceSquaredTo(alt) <= actual.distanceSquaredTo(actual)) {
            actual = alt;
        }
        alt = actual.translate(0, -128);
        if (curr.distanceSquaredTo(alt) <= actual.distanceSquaredTo(actual)) {
            actual = alt;
        }
        alt = actual.translate(0, 128);
        if (curr.distanceSquaredTo(alt) <= actual.distanceSquaredTo(actual)) {
            actual = alt;
        }
        return actual;
    }
    
    // raw x and y range 10k to 30k, taking up minimum 29 bits to encode, even if offsetting by 10k
    // just one takes 15 bits. 4 reserved for signal type, so shift left 5 bits as 4+5+15 = 20
    public static int getCornerLocSignalX(int x) {
        // bits 0-3 type, 4-18 x
        return CORNER_LOC_X | (x << 5);
    }
    public static int readCornerLocSignalX(int signal) {
        return (SIGNAL_MASK & signal) >> 5;
    }
    public static int getCornerLocSignalY(int y) {
        return CORNER_LOC_Y | (y << 5);
    }
    public static int readCornerLocSignalY(int signal) {
        return (SIGNAL_MASK & signal) >> 5;
    }

    public static int getMapOffsetSignalXWidth(int offsetx, int mapWidth) {
        return (MAP_OFFSET_X_AND_WIDTH | (offsetx << 5)) | (mapWidth - 32);
    }
    /**
     * 
     * @param signal
     * @return [offsetx, width]
     */
    public static int[] readMapOffsetSignalXWidth(int signal) {

        int offsetx = (SIGNAL_MASK & signal) >> 5;
        // take rightmost 5 bits
        int width = 32 + (signal & 0x1f);
        return new int[]{offsetx, width};
    }
    public static int getMapOffsetSignalYHeight(int offsety, int mapHeight) {
        // 32 offset so we can pack offsety and height in one signal
        return (MAP_OFFSET_Y_AND_HEIGHT | (offsety << 5)) | (mapHeight - 32);
    }
    /**
     * 
     * @param signal
     * @return [height, height]
     */
    public static int[] readMapOffsetSignalYHeight(int signal) {
        int offsety = (SIGNAL_MASK & signal) >> 5;
        // take rightmost 5 bits
        int height = 32 + (signal & 0x1f);
        return new int[]{offsety, height};
    }

    // unit id range is 10000 to 32000
    public static int getBuiltUnitSignal(int unitID, RobotType type) {
        // default is 2 = muckraker
        int typeind = 2;
        // find index of type in our Constants.SPAWNABLE_ROBOTS Array
        switch (type) {
            case POLITICIAN:
                typeind = TYPE_POLITICIAN;
                break;
            case SLANDERER:
                typeind = TYPE_SLANDERER;
                break;
            default:
                typeind = TYPE_MUCKRAKER;
				break;
        }   
        return BUILT_UNIT | (typeind << 18) | unitID;
    }
    /**
     * 
     * @param signal
     * @return [unitid, type]
     */
    public static int[] readBuiltUnitSignal(int signal) {
        int typeind = (SIGNAL_MASK & signal) >> 18;
        int id = (SIGNAL_MASK & signal) & 0x00ffff;
        return new int[]{id, typeind};
    }

    public static int getPoliSacrificeSignal() {
        return POLI_SACRIFICE;
    }

    public static int getFoundECSignal(MapLocation ECLoc, int teamind) {
        // we send the type, friend, enemy, or neutral 2 bits (0, 1, 2)
        // location: 14 bits, team ind is 2 bits, 4 bits for ec inf

        return FOUND_EC | (teamind << 18) | encodeMapLocation(ECLoc);
    }

    /**
     * 
     * @param signal
     * @return [team, location hash]
     */
    public static int[] readFoundECSignal(int signal) {
        int team = (SIGNAL_MASK & signal) >> 18;
        int lochash = signal & 0x03ffff;
        return new int[]{team, lochash};
    }

    public static int getUnitDetailsSignal(int unittype) {
        return UNIT_DETAILS | (unittype << 18);
    }
    /**
     * 
     * @param signal
     * @return [unittype]
     */
    public static int[] readUnitDetails(int signal) {
        int type = (SIGNAL_MASK & signal) >> 18;
        return new int[]{type};
    }

    public static int getAttackECSignal(MapLocation ECLoc) {
        return ATTACK_EC | encodeMapLocation(ECLoc);
    }
    public static MapLocation readAttackECSignal(int signal, RobotController rc) {
        int lochash = SIGNAL_MASK & signal;
        return decodeMapLocation(lochash, rc);
    }
}

