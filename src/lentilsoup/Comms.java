package lentilsoup;

import battlecode.common.*;

public class Comms {
    // reserve first few bits for signal type, here we reserve first 4
    // Optimization: use less bits and use of type of bot bearing that flag as a signal discriminator as well
    /**
     * reserved 4 bits for differentiating signal type
     * mask to retrieve the signal type
     */
    public static final int SIGNAL_TYPE_MASK = 0xf00000;
    /** mask used to get all of the content of a signal/flag */
    public static final int SIGNAL_MASK = 0x0fffff;
    public static final int CORNER_LOC_X = 0x100000;
    public static final int CORNER_LOC_Y = 0x200000;
    public static final int MAP_OFFSET_X_AND_WIDTH = 0x300000;
    public static final int MAP_OFFSET_Y_AND_HEIGHT = 0x400000;
    /** bits 4-5 for unit type, rest for id */
    public static final int BUILT_UNIT = 0x500000;
    public static final int POLI_SACRIFICE = 0x600000;
    public static final int FOUND_EC = 0x700000;

    // takes 12 bits of space
    public static int encodeMapLocation(MapLocation loc, int offsetx, int offsety) {
        int x = loc.x - offsetx;
        int y = loc.y - offsety;
        return x * 64 + y;
    }
    public static MapLocation decodeMapLocation(int hash, int offsetx, int offsety) {
        int y = hash % 64 + offsety;
        int x = hash / 64 + offsetx;
        return new MapLocation(x, y);
    }

    public static int encodeMapLocationWithoutOffsets(MapLocation loc) {
        return loc.x * 40000 + loc.y;
    }
    public static MapLocation decodeMapLocationWithoutOffsets(int hash) {
        int y = hash % 40000;
        int x = hash / 40000;
        return new MapLocation(x, y);
    }
    
    // raw x and y range 10k to 30k, taking up minimum 29 bits to encode, even if offsetting by 10k
    // just one takes 15 bits. 4 reserved for signal type, so shift left 5 bits as 4+5+15 = 20
    public static int getCornerLocSignalX(MapLocation loc) {
        // bits 0-3 type, 4-18 x
        return CORNER_LOC_X | (loc.x << 5);
    }
    public static int readCornerLocSignalX(int signal) {
        return (SIGNAL_MASK & signal) >> 5;
    }
    public static int getCornerLocSignalY(MapLocation loc) {
        return CORNER_LOC_Y | (loc.y << 5);
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
                typeind = 0;
                break;
            case SLANDERER:
                typeind = 1;
                break;
            default:
                typeind = 2;
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

    public static int getFoundECSignal(MapLocation ECLoc, int teamind, int offsetx, int offsety) {
        // we send the type, friend, enemy, or neutral 2 bits (0, 1, 2)
        // location: usually 12 bits

        return FOUND_EC | (teamind << 18) | encodeMapLocation(ECLoc, offsetx, offsety);
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
}
