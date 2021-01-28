package superecosushi;

import battlecode.common.*;

public class Constants {
        public static final Direction[] DIRECTIONS = { Direction.NORTH, Direction.NORTHEAST, Direction.EAST,
                        Direction.SOUTHEAST, Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST,
                        Direction.NORTHWEST, };
        public static final Direction[] DIAG_DIRECTIONS = { Direction.NORTHEAST, Direction.SOUTHEAST,
                        Direction.SOUTHWEST, Direction.NORTHWEST, };
        public static final Direction[] CARD_DIRECTIONS = { Direction.NORTH, Direction.EAST, Direction.SOUTH,
                        Direction.WEST, };
        public static final RobotType[] SPAWNABLE_ROBOTS = { RobotType.POLITICIAN, RobotType.SLANDERER,
                        RobotType.MUCKRAKER, };

        public static final int STANDARD_DEFEND_POLI_CONVICTION = 20;
        public static final int BUFF_MUCK_THRESHOLD = 15;
        public static final int MUCKRAKER_SENSE_RADIUS = 30;
        public static final int POLI_SENSE_RADIUS = 25;
        public static final int MUCKRAKER_ACTION_RADIUS = 12;
        public static final int SLANDERER_SENSE_RADIUS = 20;
        public static final int POLI_ACTION_RADIUS = 9;
        public static final int TEAM_FRIEND = 0;
        public static final int TEAM_ENEMY = 1;
        public static final int TEAM_NEUTRAL = 2;
        public static final int TYPE_POLITICIAN = 0;
        public static final int TYPE_SLANDERER = 1;
        public static final int TYPE_MUCKRAKER = 2;
        public static final int TYPE_ENLIGHTMENT_CENTER = 3;
        public static final int MAX_INF_PER_ROBOT = 100000000;
}
