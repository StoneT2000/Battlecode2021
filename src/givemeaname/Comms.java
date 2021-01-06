package givemeaname;

import battlecode.common.*;

public class Comms {
    // reserve first few bits for signal type, here we reserve first 4
    // Optimization: use less bits and use of type of bot bearing that flag as a signal discriminator as well
    public static final int SIGNAL_TYPE_MASK = 0xf00000;
    public static final int CORNER_LOC_X = 0x000000;
    public static final int CORNER_LOC_Y = 0x800000;
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
        return CORNER_LOC_X | (loc.x << 5);
    }
    // public static int readCornerLocSignal(int signal) {
    //     if ((SIGNAL_TYPE_MASK & signal) == CORNER_LOC_X) {

    //     }
    // }
    public static int getCornerLocSignalY(MapLocation loc) {
        return CORNER_LOC_Y | (loc.y << 5);
    }
}
