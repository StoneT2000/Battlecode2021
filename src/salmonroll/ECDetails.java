package salmonroll;

import battlecode.common.*;

public class ECDetails {
    public MapLocation location;
    /** may be -1, signifying we dont have info on this */
    public int lastKnownConviction = -1;
    ECDetails(MapLocation loc, int lastKnownConviction) {
        this.location = loc;
        this.lastKnownConviction = lastKnownConviction;
    }
}
