package io.github.mcheliceport.net;

/**
 * Upstream's {@code PlayerControlSwitches}: fourteen control flags (and the radar switch) packed into a short, bit for
 * bit as CE sends them. M3 uses the throttle, move, open-GUI and gunner-status bits; the others
 * keep their places for M5 and M6.
 */
public record PlayerControlSwitches(short mask) {

    public static final int EJECT_SEAT = 0;
    public static final int USE_WEAPON = 1;
    public static final int SWITCH_SEARCH_LIGHT = 2;
    public static final int USE_BRAKE = 3;
    public static final int SWITCH_GUNNER_STATUS = 4;
    public static final int THROTTLE_UP = 5;
    public static final int THROTTLE_DOWN = 6;
    public static final int MOVE_LEFT = 7;
    public static final int MOVE_RIGHT = 8;
    public static final int OPEN_GUI = 9;
    public static final int RELOAD = 10;
    public static final int USE_CHAFF = 11;
    public static final int USE_APS = 12;
    public static final int USE_ECM_JAMMER = 13;
    /** Upstream sent the radar switch as its own byte (1 on, 2 off); here it takes the two spare bits. */
    public static final int RADAR_ON = 14;
    public static final int RADAR_OFF = 15;
    /** Number of flags. */
    public static final int COUNT = 16;

    public static final PlayerControlSwitches NONE = new PlayerControlSwitches((short) 0);

    public boolean get(int bit) {
        return (this.mask & (1 << bit)) != 0;
    }

    public PlayerControlSwitches with(int bit, boolean value) {
        int m = value ? this.mask | (1 << bit) : this.mask & ~(1 << bit);
        return new PlayerControlSwitches((short) m);
    }

    public boolean throttleUp() {
        return this.get(THROTTLE_UP);
    }

    public boolean throttleDown() {
        return this.get(THROTTLE_DOWN);
    }

    public boolean moveLeft() {
        return this.get(MOVE_LEFT);
    }

    public boolean moveRight() {
        return this.get(MOVE_RIGHT);
    }

    public boolean openGui() {
        return this.get(OPEN_GUI);
    }

    public boolean switchGunnerStatus() {
        return this.get(SWITCH_GUNNER_STATUS);
    }
}
