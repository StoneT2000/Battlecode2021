package betterecosushi;

import battlecode.common.*;
import static betterecosushi.Constants.*;

public class Comms {

    public static final int LARGE_INFLUENCE = -10;
    // reserve first few bits for signal type, here we reserve first 4
    // Optimization: use less bits and use of type of bot bearing that flag as a
    // signal discriminator as well
    /**
     * reserved 4 bits for differentiating signal type mask to retrieve the signal
     * type
     */
    public static final int SIGNAL_TYPE_MASK = 0xf00000;
    public static final int SIGNAL_TYPE_5BIT_MASK = 0xf80000;
    /** mask used to get all of the content of a signal/flag */
    public static final int SIGNAL_MASK = 0x0fffff;
    public static final int SIGNAL_5BIT_MASK = 0x07ffff;
    public static final int CORNER_LOC_X = 0x100000;
    public static final int CORNER_LOC_Y = 0x200000;
    public static final int MAP_OFFSET_X_AND_WIDTH = 0x300000;
    public static final int MAP_OFFSET_Y_AND_HEIGHT = 0x400000;
    /** bits 4-5 for unit type, rest for id */
    public static final int BUILT_UNIT = 0x500000;

    public static final int FOUND_ENEMY_SLANDERER = 0x600000;

    // TODO: add scouting on EC influence each turn...
    public static final int FOUND_EC = 0x700000;

    
    // cram more single message signals that don't require much space
    public static final int SMALL_SIGNAL = 0x900000;
    // small signals:
    public static final int IMASLANDERERR = 0x900001;
    public static final int GO_SCOUT = 0x900002;
    public static final int GO_SCOUT_NORTH = 0x900003;
    public static final int GO_SCOUT_EAST = 0x900004;
    public static final int GO_SCOUT_SOUTH = 0x900005;
    public static final int GO_SCOUT_WEST = 0x900006;
    public static final int GO_SCOUT_NORTHEAST = 0x900007;
    public static final int GO_SCOUT_SOUTHEAST = 0x900008;
    public static final int GO_SCOUT_SOUTHWEST = 0x900009;
    public static final int GO_SCOUT_NORTHWEST = 0x90000a;


    public static final int UNIT_DETAILS = 0x90000b;

    public static final int IM_ATTACKING_NEUTRAL_EC = 0x90000b;
    public static final int IM_STOPPING_BUFF_MUCK = 0x90000c;

    public static final int ATTACK_EC = 0xa00000;
    public static final int ATTACK_NEUTRAL_EC = 0xa80000;
    
    // bottom 2 can be combined
    public static final int TARGETED_MUCK = 0xb00000;
    public static final int TARGETED_EC = 0xc00000;

    public static final int SPOTTED_MUCK = 0xd00000;

    public static final int SLAND_SPOTTED_MUCK = 0xe00000;

    // some long hash map locs in event we don't have map dims
    // this thing below is for checking if the signal shoudl be proccessed as a
    // found ec signal

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
        int offsetY128 = curr.y / 128;

        MapLocation original = new MapLocation(offsetX128 * 128 + x, offsetY128 * 128 + y);
        MapLocation proposed = original;
        MapLocation alt = original.translate(-128, 0);
        if (curr.distanceSquaredTo(alt) <= curr.distanceSquaredTo(proposed)) {
            proposed = alt;
        }
        alt = original.translate(128, 0);
        if (curr.distanceSquaredTo(alt) <= curr.distanceSquaredTo(proposed)) {
            proposed = alt;
        }
        alt = original.translate(0, -128);
        if (curr.distanceSquaredTo(alt) <= curr.distanceSquaredTo(proposed)) {
            proposed = alt;
        }
        alt = original.translate(0, 128);
        if (curr.distanceSquaredTo(alt) <= curr.distanceSquaredTo(proposed)) {
            proposed = alt;
        }
        return proposed;
    }

    // raw x and y range 10k to 30k, taking up minimum 29 bits to encode, even if
    // offsetting by 10k
    // just one takes 15 bits. 4 reserved for signal type, so shift left 5 bits as
    // 4+5+15 = 20
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
        return new int[] { offsetx, width };
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
        return new int[] { offsety, height };
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
        return new int[] { id, typeind };
    }
    public static int getFoundECSignal(MapLocation ECLoc, int teamind, int ecInfluence) {
        // we send the type, friend, enemy, or neutral 2 bits (0, 1, 2)
        // location: 14 bits, team ind is 2 bits, 4 bits for ec inf

        int sendInf = (int) Math.ceil((double)ecInfluence / 40.0);
        if (ecInfluence > 630) {
            sendInf = 15; // 15 indicates the ec inf is over 630
        }
        return FOUND_EC | (teamind << 18) | (encodeMapLocation(ECLoc) << 4) | sendInf;
    }


    /**
     * 
     * @param signal
     * @return [team, location hash, influence]
     * // if influence is 600, then expect its in rrange [600, max]
     */
    public static int[] readFoundECSignal(int signal) {
        int team = (SIGNAL_MASK & signal) >> 18;
        int lochash = (signal & 0x03ffff) >> 4;
        int ecInf = 40 * (signal & 0x00000f);
        if (ecInf >= 600) {
            ecInf = LARGE_INFLUENCE;
        }
        return new int[] { team, lochash, ecInf };
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
        return new int[] { type };
    }

    public static int getAttackECSignal(MapLocation ECLoc) {
        return ATTACK_EC | encodeMapLocation(ECLoc);
    }

    public static MapLocation readAttackECSignal(int signal, RobotController rc) {
        int lochash = SIGNAL_MASK & signal;
        return decodeMapLocation(lochash, rc);
    }

    public static int getAttackNeutralECSignal(MapLocation ECLoc) {
        return ATTACK_NEUTRAL_EC | encodeMapLocation(ECLoc);
    }

    public static MapLocation readAttackNeutralECSignal(int signal, RobotController rc) {
        int lochash = SIGNAL_5BIT_MASK & signal;
        return decodeMapLocation(lochash, rc);
    }

    public static final int relativePoliPosOffset = 5;
    public static final int MIN_ROBOT_ID = 10000;
    
    // encode the id, and relative direction of muckraker in dx and dy
    // expect dx, dy in [0, 7]
    public static int getTargetedMuckSignal(int id, int dx, int dy) {
        int ox = dx + relativePoliPosOffset;
        int oy = dy + relativePoliPosOffset;
        int encoded = ox * 11 + oy;
        return TARGETED_MUCK | (encoded << 13) | (id - MIN_ROBOT_ID);
    }
    /** returns the hash of the id of targeted muck and relative pos -> [id, dx, dy] */
    public static int[] readTargetedMuckSignal(int signal) {
        
        int encodedxdy = (signal & (0x0fe000)) >> 13;
        int dx = (encodedxdy / 11) - relativePoliPosOffset;
        int dy = (encodedxdy % 11) - relativePoliPosOffset;
        int id = signal & (0x001fff);
        return new int[]{id + MIN_ROBOT_ID, dx, dy};
    }
    public static int getTargetedECSignal(MapLocation ecloc) {
        return TARGETED_EC | encodeMapLocation(ecloc);
    }
    /** returns the hash of the id of targeted muck */
    public static MapLocation readTargetedECSignal(int signal, RobotController rc) {
        int lochash = SIGNAL_MASK & signal;
        return decodeMapLocation(lochash, rc);
    }

    public static int getSpottedMuckSignal(MapLocation muckLoc, int conviction) {
        int sendConv = (int) Math.ceil((double)conviction / 20.0);
        if (conviction > 1240) {
            sendConv = 63; // 63 indicates the ec inf is over 630
        }
        // 4 - 14 - 6
        return SPOTTED_MUCK | (encodeMapLocation(muckLoc) << 6) | sendConv;
    }
    /**
     * 
     * @return [maphash, conviction]
     */
    public static int[] readSpottedMuckSignal(int signal, RobotController rc) {
        
        int hash = (signal & 0x0fffc0) >> 6;
        int conviction = signal & 0x00003f;
        return new int[]{hash, conviction * 20};
    }

    public static int getSlandererSpottedMuckSignal(MapLocation muckLoc) {
        return SLAND_SPOTTED_MUCK | (encodeMapLocation(muckLoc));
    }
    public static MapLocation readSlandererSpottedMuckSignal(int signal, RobotController rc) {
        return decodeMapLocation(signal & SIGNAL_MASK, rc);
    }

    public static int getFoundEnemySlandererSignal(MapLocation muckLoc) {
        return FOUND_ENEMY_SLANDERER | (encodeMapLocation(muckLoc));
    }
    public static MapLocation readFoundEnemySlandererSignal(int signal, RobotController rc) {
        return decodeMapLocation(signal & SIGNAL_MASK, rc);
    }
}
