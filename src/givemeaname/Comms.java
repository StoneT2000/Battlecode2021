package givemeaname;

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
        return (MAP_OFFSET_X_AND_WIDTH | (offsetx << 5)) | (mapWidth);
    }
    /**
     * 
     * @param signal
     * @return [offsetx, width]
     */
    public static int[] readMapOffsetSignalXWidth(int signal) {

        int offsetx = (SIGNAL_MASK & signal) >> 5;
        // take rightmost 5 bits
        int width = signal & 0x1f;
        return new int[]{offsetx, width};
    }
    public static int getMapOffsetSignalYHeight(int offsety, int mapHeight) {
        return (MAP_OFFSET_Y_AND_HEIGHT | (offsety << 5)) | (mapHeight);
    }
    /**
     * 
     * @param signal
     * @return [height, height]
     */
    public static int[] readMapOffsetSignalYHeight(int signal) {
        int offsety = (SIGNAL_MASK & signal) >> 5;
        // take rightmost 5 bits
        int height = signal & 0x1f;
        return new int[]{offsety, height};
    }
}
