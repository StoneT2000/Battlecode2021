package fishysushi;

import battlecode.common.*;

public class ECDetails {
    public MapLocation location;
    /** may be -1, signifying we dont have info on this */
    public int lastKnownConviction = -1;
    public int teamind = -1;
    ECDetails(MapLocation loc, int lastKnownConviction, int teamind) {
        this.location = loc;
        this.lastKnownConviction = lastKnownConviction;
        this.teamind = teamind;
    }
}
