package io.github.mcheliceport.entity.aircraft;

/**
 * A moving part with an on/off state stored in one bit of the vehicle's synced part status
 * (canopy bit 0, landing gear bit 2, hatch bit 4): upstream's {@code MCH_Parts}.
 *
 * <p>The server sets the state; clients read it from the synced value each tick and animate
 * {@link #rotation} towards {@link #rotationMax} or zero by {@link #rotationInv} per tick.
 */
public final class AircraftPart {

    /** What a part needs from its vehicle. */
    public interface Host {
        int partStatus();

        void setPartStatus(int status);

        boolean isClientSide();

        /** Plays a sound of the {@code mcheli} namespace at the vehicle (server side only). */
        void playPartSound(String name, float volume, float pitch);
    }

    /** A sound of a state change; an empty name plays nothing. */
    public record Sound(String name, float volume, float pitch) {
        public static final Sound NONE = new Sound("", 1.0F, 1.0F);
    }

    public static final int CANOPY_BIT = 0;
    public static final int LANDING_GEAR_BIT = 2;
    public static final int HATCH_BIT = 4;

    private final Host host;
    private final int shift;
    public final String partName;
    public float prevRotation = 0.0F;
    public float rotation = 0.0F;
    public float rotationMax = 90.0F;
    public float rotationInv = 1.0F;
    public Sound soundStartSwitchOn = Sound.NONE;
    public Sound soundEndSwitchOn = Sound.NONE;
    public Sound soundSwitching = Sound.NONE;
    public Sound soundStartSwitchOff = Sound.NONE;
    public Sound soundEndSwitchOff = Sound.NONE;
    private boolean status;

    public AircraftPart(Host host, int shiftBit, String name) {
        this.host = host;
        this.shift = shiftBit;
        this.partName = name;
        this.status = (host.partStatus() & 1 << shiftBit) != 0;
    }

    /** Upstream {@code createCanopy}. */
    public static AircraftPart canopy(Host host) {
        AircraftPart p = new AircraftPart(host, CANOPY_BIT, "Canopy");
        p.rotationMax = 90.0F;
        p.rotationInv = 3.5F;
        p.soundEndSwitchOn = new Sound("plane_cc", 1.0F, 1.0F);
        p.soundEndSwitchOff = new Sound("plane_cc", 1.0F, 1.0F);
        return p;
    }

    /** Upstream {@code createLandingGear}. */
    public static AircraftPart landingGear(Host host) {
        AircraftPart p = new AircraftPart(host, LANDING_GEAR_BIT, "LandingGear");
        p.rotationMax = 90.0F;
        p.rotationInv = 2.5F;
        p.soundStartSwitchOn = new Sound("plane_cc", 1.0F, 0.5F);
        p.soundEndSwitchOn = new Sound("plane_cc", 1.0F, 0.5F);
        p.soundStartSwitchOff = new Sound("plane_cc", 1.0F, 0.5F);
        p.soundEndSwitchOff = new Sound("plane_cc", 1.0F, 0.5F);
        p.soundSwitching = new Sound("plane_cv", 1.0F, 0.75F);
        return p;
    }

    /** Upstream {@code createHatch}. */
    public static AircraftPart hatch(Host host) {
        AircraftPart p = new AircraftPart(host, HATCH_BIT, "Hatch");
        p.rotationMax = 90.0F;
        p.rotationInv = 1.5F;
        p.soundEndSwitchOn = new Sound("plane_cc", 1.0F, 1.0F);
        p.soundEndSwitchOff = new Sound("plane_cc", 1.0F, 1.0F);
        p.soundSwitching = new Sound("plane_cv", 1.0F, 0.5F);
        return p;
    }

    public void setStatusServer(boolean stat) {
        this.setStatusServer(stat, true);
    }

    /** Upstream {@code setStatusServer}; {@code playSound} is unused upstream as well. */
    public void setStatusServer(boolean stat, boolean playSound) {
        if (!this.host.isClientSide() && this.status != stat) {
            this.updateStatus(stat);
            this.playSound(this.soundSwitching);
            this.playSound(stat ? this.soundStartSwitchOn : this.soundStartSwitchOff);
            this.update();
        }
    }

    private void updateStatus(boolean stat) {
        int current = this.host.partStatus();
        int mask = 1 << this.shift;
        this.host.setPartStatus(stat ? current | mask : current & ~mask);
        this.status = stat;
    }

    public boolean getStatus() {
        return this.status;
    }

    public boolean isOFF() {
        return !this.status && this.rotation <= 0.02F;
    }

    public boolean isON() {
        return this.status && this.rotation >= this.rotationMax - 0.02F;
    }

    /** Clients take the state from the synced value. */
    public void updateStatusClient(int synced) {
        if (this.host.isClientSide()) {
            this.status = (synced & 1 << this.shift) != 0;
        }
    }

    public void update() {
        this.prevRotation = this.rotation;
        if (this.status) {
            if (this.rotation < this.rotationMax) {
                this.rotation = this.rotation + this.rotationInv;
                if (this.rotation >= this.rotationMax) {
                    this.playSound(this.soundEndSwitchOn);
                }
            }
        } else if (this.rotation > 0.0F) {
            this.rotation = this.rotation - this.rotationInv;
            if (this.rotation <= 0.0F) {
                this.playSound(this.soundEndSwitchOff);
            }
        }
    }

    /** Upstream {@code forceSwitch}: jumps to the end of the animation. */
    public void forceSwitch(boolean onOff) {
        this.updateStatus(onOff);
        this.rotation = this.prevRotation = this.rotationMax;
    }

    public float getFactor() {
        return this.rotationMax > 0.0F ? this.rotation / this.rotationMax : 0.0F;
    }

    private void playSound(Sound sound) {
        if (!sound.name().isEmpty() && !this.host.isClientSide()) {
            this.host.playPartSound(sound.name(), sound.volume(), sound.pitch());
        }
    }
}
