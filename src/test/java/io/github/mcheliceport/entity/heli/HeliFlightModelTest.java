package io.github.mcheliceport.entity.heli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mcheliceport.entity.aircraft.AircraftAttitude;
import java.util.function.IntFunction;
import net.minecraft.util.RandomSource;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Exit criterion E2 (docs/M3_PLAN.md section 8.1): {@link HeliFlightModel}, composed the way
 * {@link HeliEntity} composes it, follows upstream's helicopter tick for tick. Each scenario runs
 * the port and {@link UpstreamHeliOracle} side by side in the same flat world (ground at y = 0)
 * and compares the whole flight state after every tick.
 */
class HeliFlightModelTest {

    private static final double TOLERANCE = 1.0E-9;
    private static final long SEED = 1234L;

    // ---- the shared world: flat ground at y = 0 ----

    /** {@code GroundProbe}'s question for a flat floor: is there a block within {@code -lenY} below? */
    static boolean blockWithin(double posY, int lenY) {
        return Math.floor(posY + lenY) < 0.0;
    }

    /** Moves by dy and lands on y = 0; returns {new y, 1 if on ground, 1 if dy was cut}. */
    static double[] fall(double posY, double dy) {
        double y = posY + dy;
        if (y <= 0.0 && dy <= 0.0) {
            return new double[] {0.0, 1.0, y < 0.0 ? 1.0 : 0.0};
        }
        return new double[] {y, 0.0, 0.0};
    }

    // ---- the port's side ----

    static final class PortBody implements HeliFlightModel.Body {
        double x;
        double y;
        double z;
        double motionX;
        double motionY;
        double motionZ;
        float yaw;
        float pitch;
        float roll;
        double throttle;
        double currentSpeed = HeliFlightModel.MIN_CURRENT_SPEED;
        boolean onGround;
        boolean inWater;
        double waterDepth;
        final RandomSource random = RandomSource.create(SEED);

        @Override
        public double motionX() {
            return this.motionX;
        }

        @Override
        public double motionY() {
            return this.motionY;
        }

        @Override
        public double motionZ() {
            return this.motionZ;
        }

        @Override
        public void setMotion(double x, double y, double z) {
            this.motionX = x;
            this.motionY = y;
            this.motionZ = z;
        }

        @Override
        public double currentThrottle() {
            return this.throttle;
        }

        @Override
        public void setCurrentThrottle(double throttle) {
            this.throttle = throttle;
        }

        @Override
        public double currentSpeed() {
            return this.currentSpeed;
        }

        @Override
        public void setCurrentSpeed(double speed) {
            this.currentSpeed = speed;
        }

        @Override
        public boolean onGround() {
            return this.onGround;
        }

        @Override
        public boolean inWater() {
            return this.inWater;
        }

        @Override
        public double waterDepth() {
            return this.waterDepth;
        }

        @Override
        public boolean blockWithin(int size, int lenY) {
            return HeliFlightModelTest.blockWithin(this.y, lenY);
        }

        @Override
        public void move(double dx, double dy, double dz) {
            double[] r = fall(this.y, dy);
            this.x += dx;
            this.y = r[0];
            this.z += dz;
            this.onGround = r[1] != 0.0;
            if (r[2] != 0.0) {
                this.motionY = 0.0;
            }
        }

        @Override
        public RandomSource random() {
            return this.random;
        }

        @Override
        public float getYaw() {
            return this.yaw;
        }

        @Override
        public void setYaw(float yaw) {
            this.yaw = yaw;
        }

        @Override
        public float getPitch() {
            return this.pitch;
        }

        @Override
        public void setPitch(float pitch) {
            this.pitch = pitch;
        }

        @Override
        public float getRoll() {
            return this.roll;
        }

        @Override
        public void setRoll(float roll) {
            this.roll = roll;
        }

        @Override
        public @Nullable Quaternionf getOrientation() {
            return null;
        }

        @Override
        public void setOrientation(@Nullable Quaternionf orientation) {
        }
    }

    /** The helicopter state a scenario sets; both sides start from it. */
    static final class Setup {
        float speed = 0.6F;
        float gravity = -0.04F;
        float gravityInWater = -0.04F;
        float onGroundPitch;
        float throttleUpDown = 1.0F;
        float rotorSpeed = 79.99F;
        boolean enableFoldBlade;
        double startY;
        float yaw;
        float pitch;
        float roll;
        double throttle;
        boolean onGround = true;
        boolean inWater;
        double waterDepth;
        boolean isFloat;
        boolean hoveringMode;
        boolean gunnerMode;
        boolean destroyed;
        boolean exploded;
        boolean canUseFuel = true;
        boolean foldedBlades;
        boolean pilot = true;
        boolean autoThrottleDown = true;
        HeliFlightModel.RotationLimits limits = HeliFlightModel.RotationLimits.NONE;
    }

    /** One tick of the port, in {@code HeliEntity#onUpdateAircraft}'s order (server side). */
    static double portTick(PortBody b, Setup s, HeliFlightModel.Input in, double rotor) {
        HeliFlightModel.Params p = new HeliFlightModel.Params(s.speed, s.gravity, s.gravityInWater, s.onGroundPitch,
                s.throttleUpDown, s.rotorSpeed, s.enableFoldBlade);
        boolean hovering = s.hoveringMode || s.gunnerMode;
        boolean canUseBlades = !s.foldedBlades;
        if (!s.destroyed && (s.pilot || s.hoveringMode) && canUseBlades && s.canUseFuel) {
            if (!hovering) {
                HeliFlightModel.controlNotHovering(b, p, in, s.autoThrottleDown, true);
            } else {
                HeliFlightModel.controlHovering(b, p, in, true);
            }
        } else {
            HeliFlightModel.idleThrottle(b);
            if (s.enableFoldBlade && s.foldedBlades && b.onGround() && !s.destroyed) {
                HeliFlightModel.controlFoldBladeOnGround(b, in, true);
            }
        }
        if (b.currentThrottle() < 0.0) {
            b.setCurrentThrottle(0.0);
        }
        rotor = HeliFlightModel.spinRotor(rotor, b.currentThrottle(), s.rotorSpeed);

        HeliFlightModel.hoverPitchDamping(b, s.destroyed, hovering);
        if (s.destroyed && b.currentThrottle() > 0.0) {
            if (b.blockWithin(3, -2)) {
                b.setCurrentThrottle(b.currentThrottle() * 0.8);
            }
            if (s.exploded) {
                b.setCurrentThrottle(b.currentThrottle() * 0.98);
            }
        }
        HeliFlightModel.physics(b, p, hovering, s.destroyed, s.isFloat && !s.destroyed);
        HeliFlightModel.onUpdateAngles(b, p, 0.05F, s.destroyed, hovering, s.limits, in, s.foldedBlades);
        return rotor;
    }

    static UpstreamHeliOracle oracle(Setup s) {
        UpstreamHeliOracle o = new UpstreamHeliOracle(new UpstreamHeliOracle.World() {
            @Override
            public boolean blockIdY(UpstreamHeliOracle heli, int size, int lenY) {
                return blockWithin(heli.posY, lenY);
            }

            @Override
            public void move(UpstreamHeliOracle heli, double x, double y, double z) {
                double[] r = fall(heli.posY, y);
                heli.posX += x;
                heli.posY = r[0];
                heli.posZ += z;
                heli.onGround = r[1] != 0.0;
                if (r[2] != 0.0) {
                    heli.motionY = 0.0;
                }
            }
        }, SEED);
        o.speed = s.speed;
        o.gravity = s.gravity;
        o.gravityInWater = s.gravityInWater;
        o.onGroundPitch = s.onGroundPitch;
        o.throttleUpDown = s.throttleUpDown;
        o.rotorSpeed = s.rotorSpeed;
        o.isEnableFoldBlade = s.enableFoldBlade;
        o.posY = s.startY;
        o.rotationYaw = s.yaw;
        o.rotationPitch = s.pitch;
        o.rotationRoll = s.roll;
        o.currentThrottle = s.throttle;
        o.onGround = s.onGround;
        o.inWater = s.inWater;
        o.waterDepth = s.waterDepth;
        o.isFloat = s.isFloat;
        o.hoveringMode = s.hoveringMode;
        o.isGunnerMode = s.gunnerMode;
        o.destroyed = s.destroyed;
        o.exploded = s.exploded;
        o.canUseFuel = s.canUseFuel;
        o.canUseBlades = !s.foldedBlades;
        o.foldBladeStat = (byte) (s.foldedBlades ? 0 : 2);
        o.riddenByEntity = s.pilot;
        o.cs_heliAutoThrottleDown = s.autoThrottleDown;
        o.enableRotationLimit = s.limits.enabled();
        o.pitchLimitMax = s.limits.pitchMax();
        o.pitchLimitMin = s.limits.pitchMin();
        o.rollLimit = s.limits.rollLimit();
        return o;
    }

    static PortBody port(Setup s) {
        PortBody b = new PortBody();
        b.y = s.startY;
        b.yaw = s.yaw;
        b.pitch = s.pitch;
        b.roll = s.roll;
        b.throttle = s.throttle;
        b.onGround = s.onGround;
        b.inWater = s.inWater;
        b.waterDepth = s.waterDepth;
        return b;
    }

    /** Runs a scenario for {@code ticks} ticks with the given input per tick; returns the port body. */
    static PortBody run(String name, Setup s, int ticks, IntFunction<HeliFlightModel.Input> input) {
        PortBody b = port(s);
        UpstreamHeliOracle o = oracle(s);
        double rotor = 0.0;
        for (int t = 0; t < ticks; t++) {
            HeliFlightModel.Input in = input.apply(t);
            o.throttleUp = in.throttleUp();
            o.throttleDown = in.throttleDown();
            o.moveLeft = in.moveLeft();
            o.moveRight = in.moveRight();
            o.onUpdate_Control();
            o.onUpdateAircraftMiddle();
            o.onUpdate_Server();
            o.onUpdateAngles(0.05F);
            rotor = portTick(b, s, in, rotor);
            String at = name + " tick " + t + ": ";
            assertEquals(o.posX, b.x, TOLERANCE, at + "x");
            assertEquals(o.posY, b.y, TOLERANCE, at + "y");
            assertEquals(o.posZ, b.z, TOLERANCE, at + "z");
            assertEquals(o.motionX, b.motionX, TOLERANCE, at + "motionX");
            assertEquals(o.motionY, b.motionY, TOLERANCE, at + "motionY");
            assertEquals(o.motionZ, b.motionZ, TOLERANCE, at + "motionZ");
            assertEquals(o.rotationYaw, b.yaw, TOLERANCE, at + "yaw");
            assertEquals(o.rotationPitch, b.pitch, TOLERANCE, at + "pitch");
            assertEquals(o.rotationRoll, b.roll, TOLERANCE, at + "roll");
            assertEquals(o.currentThrottle, b.throttle, TOLERANCE, at + "throttle");
            assertEquals(o.currentSpeed, b.currentSpeed, TOLERANCE, at + "currentSpeed");
            assertEquals(o.onGround, b.onGround, at + "onGround");
            assertEquals(o.rotationRotor, rotor, TOLERANCE, at + "rotor");
        }
        return b;
    }

    static HeliFlightModel.Input in(boolean up, boolean down, boolean left, boolean right) {
        return new HeliFlightModel.Input(up, down, left, right);
    }

    // ---- scenarios ----

    @Test
    void takeOffAndForwardFlight() {
        Setup s = new Setup();
        s.yaw = 37.0F;
        PortBody b = run("take-off", s, 200, t -> in(true, false, false, false));
        assertTrue(b.y > 5.0, "the helicopter climbed: y = " + b.y);
        run("take-off and settle", s, 400, t -> in(t < 60, false, false, false));

        Setup forward = new Setup();
        forward.onGround = false;
        forward.startY = 40.0;
        forward.pitch = 25.0F;
        forward.throttle = 1.0;
        forward.yaw = -120.0F;
        PortBody f = run("forward", forward, 300, t -> in(true, false, false, false));
        double horizontal = Math.sqrt(f.motionX * f.motionX + f.motionZ * f.motionZ);
        assertTrue(horizontal <= forward.speed + 1.0E-6, "speed stays under the limit: " + horizontal);
        assertTrue(f.currentSpeed > HeliFlightModel.MIN_CURRENT_SPEED, "currentSpeed builds up");
    }

    @Test
    void autoThrottleSettlesInItsBand() {
        Setup s = new Setup();
        s.throttle = 0.9;
        PortBody b = run("auto down", s, 120, t -> HeliFlightModel.Input.NONE);
        assertTrue(b.throttle >= 0.47 && b.throttle <= 0.53, "throttle " + b.throttle);

        Setup up = new Setup();
        up.throttle = 0.1;
        up.throttleUpDown = 1.5F;
        run("auto up", up, 120, t -> HeliFlightModel.Input.NONE);

        Setup off = new Setup();
        off.throttle = 0.9;
        off.autoThrottleDown = false;
        PortBody o = run("auto off", off, 60, t -> HeliFlightModel.Input.NONE);
        assertEquals(0.9, o.throttle, TOLERANCE);

        Setup down = new Setup();
        down.throttle = 0.6;
        run("throttle down", down, 80, t -> in(false, true, false, false));
    }

    @Test
    void hoveringWithStrafing() {
        Setup s = new Setup();
        s.hoveringMode = true;
        s.onGround = false;
        s.startY = 20.0;
        s.pitch = 15.0F;
        s.roll = 10.0F;
        s.yaw = 200.0F;
        run("hovering", s, 400, t -> switch (t / 50 % 6) {
            case 0 -> in(true, false, false, false);
            case 1 -> in(false, true, false, false);
            case 2 -> in(false, false, true, false);
            case 3 -> in(false, false, false, true);
            case 4 -> in(true, false, true, false);
            default -> in(false, false, true, true);
        });

        Setup gunner = new Setup();
        gunner.gunnerMode = true;
        gunner.onGround = false;
        gunner.startY = 20.0;
        run("gunner", gunner, 200, t -> in(t % 3 == 0, false, t % 2 == 0, false));
    }

    @Test
    void strafingInForwardFlightRolls() {
        Setup s = new Setup();
        s.onGround = false;
        s.startY = 30.0;
        s.throttle = 0.5;
        s.yaw = 90.0F;
        PortBody b = run("strafe", s, 200, t -> in(false, false, t < 100, t >= 60));
        assertTrue(b.roll != 0.0F);
    }

    @Test
    void onGroundFrictionAndGroundPitch() {
        Setup s = new Setup();
        s.onGroundPitch = -3.5F;
        s.pitch = 10.0F;
        s.roll = 20.0F;
        s.throttle = 0.3;
        PortBody b = run("ground", s, 150, t -> in(false, false, t < 40, false));
        assertTrue(b.onGround);

        Setup nose = new Setup();
        nose.onGroundPitch = 5.0F;
        nose.pitch = -30.0F;
        run("ground nose", nose, 80, t -> HeliFlightModel.Input.NONE);
    }

    @Test
    void waterAndFloating() {
        Setup floating = new Setup();
        floating.isFloat = true;
        floating.inWater = true;
        floating.waterDepth = 0.5;
        floating.onGround = false;
        floating.startY = 10.0;
        floating.pitch = 20.0F;
        floating.roll = -15.0F;
        floating.throttle = 0.5;
        run("float half", floating, 100, t -> in(t < 30, false, false, false));

        Setup deep = new Setup();
        deep.isFloat = true;
        deep.inWater = true;
        deep.waterDepth = 1.0;
        deep.onGround = false;
        deep.startY = 10.0;
        run("float deep", deep, 100, t -> HeliFlightModel.Input.NONE);

        Setup sinking = new Setup();
        sinking.inWater = true;
        sinking.waterDepth = 1.0;
        sinking.gravityInWater = -0.02F;
        sinking.onGround = false;
        sinking.startY = 10.0;
        run("sinking", sinking, 100, t -> HeliFlightModel.Input.NONE);
    }

    @Test
    void destroyedHelicopterFalls() {
        Setup s = new Setup();
        s.destroyed = true;
        s.onGround = false;
        s.startY = 50.0;
        s.throttle = 1.0;
        s.roll = 30.0F;
        PortBody b = run("destroyed", s, 200, t -> in(true, false, true, false));
        assertTrue(b.onGround);

        Setup exploded = new Setup();
        exploded.destroyed = true;
        exploded.exploded = true;
        exploded.onGround = false;
        exploded.startY = 3.0;
        exploded.throttle = 1.0;
        run("exploded", exploded, 60, t -> HeliFlightModel.Input.NONE);
    }

    @Test
    void outOfFuelAndNoPilotSpinDown() {
        Setup s = new Setup();
        s.canUseFuel = false;
        s.onGround = false;
        s.startY = 30.0;
        s.throttle = 0.8;
        PortBody b = run("no fuel", s, 300, t -> in(true, false, false, false));
        assertTrue(b.throttle < 0.8);

        Setup empty = new Setup();
        empty.pilot = false;
        empty.throttle = 0.01;
        PortBody e = run("no pilot", empty, 40, t -> HeliFlightModel.Input.NONE);
        assertEquals(0.0, e.throttle, TOLERANCE);
    }

    @Test
    void foldedBladesTaxiOnTheGround() {
        Setup s = new Setup();
        s.enableFoldBlade = true;
        s.foldedBlades = true;
        s.yaw = 45.0F;
        PortBody b = run("taxi", s, 100, t -> in(t < 50, t >= 50, t % 20 < 10, t % 20 >= 10));
        assertTrue(b.x != 0.0 || b.z != 0.0);
    }

    @Test
    void allHeliSpeedScalesTheSpeed() {
        for (double factor : new double[] {0.5, 2.0}) {
            Setup s = new Setup();
            s.speed = (float) (0.6F * factor);
            s.onGround = false;
            s.startY = 60.0;
            s.pitch = 35.0F;
            s.throttle = 1.0;
            run("AllHeliSpeed " + factor, s, 300, t -> in(true, false, false, false));
        }
    }

    @Test
    void rotationLimitsPullTheAttitudeBack() {
        Setup s = new Setup();
        s.onGround = false;
        s.startY = 30.0;
        s.pitch = 60.0F;
        s.roll = 80.0F;
        s.throttle = 0.5;
        s.limits = new HeliFlightModel.RotationLimits(true, 10.0F, -10.0F, 35.0F);
        run("limits high", s, 100, t -> HeliFlightModel.Input.NONE);

        Setup low = new Setup();
        low.onGround = false;
        low.startY = 30.0;
        low.pitch = -50.0F;
        low.roll = -70.0F;
        low.throttle = 0.5;
        low.limits = new HeliFlightModel.RotationLimits(true, 20.0F, -15.0F, 25.0F);
        run("limits low", low, 100, t -> HeliFlightModel.Input.NONE);
    }

    @Test
    void anglesAtFrameRates() {
        for (float dt : new float[] {1.0F / 30.0F, 1.0F / 60.0F, 1.0F / 144.0F}) {
            Setup s = new Setup();
            s.onGround = false;
            s.startY = 30.0;
            s.limits = new HeliFlightModel.RotationLimits(true, 10.0F, -10.0F, 35.0F);
            PortBody b = port(s);
            b.pitch = 40.0F;
            b.roll = 50.0F;
            UpstreamHeliOracle o = oracle(s);
            o.rotationPitch = 40.0F;
            o.rotationRoll = 50.0F;
            HeliFlightModel.Params p = new HeliFlightModel.Params(s.speed, s.gravity, s.gravityInWater,
                    s.onGroundPitch, s.throttleUpDown, s.rotorSpeed, s.enableFoldBlade);
            for (int frame = 0; frame < 300; frame++) {
                HeliFlightModel.Input input = in(false, false, frame < 150, frame >= 100);
                o.moveLeft = input.moveLeft();
                o.moveRight = input.moveRight();
                o.onUpdateAngles(dt);
                HeliFlightModel.onUpdateAngles(b, p, dt, false, false, s.limits, input, false);
                assertEquals(o.rotationPitch, b.pitch, TOLERANCE, dt + " frame " + frame + " pitch");
                assertEquals(o.rotationRoll, b.roll, TOLERANCE, dt + " frame " + frame + " roll");
            }
        }
    }

    @Test
    void observerClientChasesThrottleAndAppliesFriction() {
        Setup s = new Setup();
        s.onGroundPitch = -2.0F;
        s.pitch = 12.0F;
        s.roll = -8.0F;
        PortBody b = port(s);
        UpstreamHeliOracle o = oracle(s);
        o.isRemote = true;
        o.syncedThrottle = 0.75;
        b.motionX = o.motionX = 0.3;
        b.motionZ = o.motionZ = -0.2;
        HeliFlightModel.Params p = new HeliFlightModel.Params(s.speed, s.gravity, s.gravityInWater,
                s.onGroundPitch, s.throttleUpDown, s.rotorSpeed, s.enableFoldBlade);
        for (int t = 0; t < 120; t++) {
            o.onUpdate_Control();
            o.onUpdate_ClientFriction();
            HeliFlightModel.controlNotHovering(b, p, HeliFlightModel.Input.NONE, false, false);
            HeliFlightModel.chaseSyncedThrottle(b, 0.75);
            if (b.currentThrottle() < 0.0) {
                b.setCurrentThrottle(0.0);
            }
            HeliFlightModel.clientFriction(b, p, false);
            assertEquals(o.currentThrottle, b.throttle, TOLERANCE, "tick " + t + " throttle");
            assertEquals(o.motionX, b.motionX, TOLERANCE, "tick " + t + " motionX");
            assertEquals(o.motionZ, b.motionZ, TOLERANCE, "tick " + t + " motionZ");
            assertEquals(o.rotationPitch, b.pitch, TOLERANCE, "tick " + t + " pitch");
            assertEquals(o.rotationRoll, b.roll, TOLERANCE, "tick " + t + " roll");
        }
        assertTrue(Math.abs(b.throttle - 0.75) < 0.03);
    }

    @Test
    void attitudeHelperIsUsedThroughTheInterface() {
        // Guards against the body interface drifting from AircraftAttitude.Rotatable.
        AircraftAttitude.Rotatable r = new PortBody();
        r.setPitch(3.0F);
        assertEquals(3.0F, r.getPitch());
    }
}
