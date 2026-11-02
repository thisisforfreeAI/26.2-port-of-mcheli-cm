package io.github.mcheliceport.wingman.ai;

import java.util.Locale;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A port addition: an AI pilot's fire control, on plain values. It sorts weapon types into roles
 * and scores them against a target, flies a shot's ballistics as the projectile entities do
 * (gravity, horizontal drag, the speed cap and step factor of fast rounds, a bomb's inherited
 * velocity) and solves where to point a gun or rocket so the round meets a moving target, and
 * where a bomb would land. See {@code docs/M10_AI_FIRE_CONTROL.md}.
 */
public final class FireControl {

    /** The ticks a shot is flown at most when it has no time fuse. */
    static final int MAX_TICKS = 160;
    /** A bomb falls at most this long. */
    static final int MAX_BOMB_TICKS = 400;
    /** Corrections of the aim point before the solver gives up improving it. */
    static final int ITERATIONS = 6;
    /** A miss under this many blocks needs no further correction. */
    static final double CONVERGED = 0.1;
    /** How long a target's acceleration is extrapolated, in ticks; beyond that it is flown straight. */
    static final double ACCEL_HORIZON = 30.0;

    /** What a weapon type is for. */
    public enum Role {
        GUN, ROCKET, AA_MISSILE, AG_MISSILE, BOMB, TORPEDO, NONE;

        /** A round flying where the weapon points: aimed with lead and drop. */
        public boolean ballistic() {
            return this == GUN || this == ROCKET;
        }

        public boolean guided() {
            return this == AA_MISSILE || this == AG_MISSILE;
        }
    }

    private FireControl() {
    }

    public static Role role(@Nullable String type) {
        if (type == null) {
            return Role.NONE;
        }
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "machinegun1", "machinegun2", "gun", "cannon", "buckshot", "railgun", "laser", "cas" -> Role.GUN;
            case "rocket" -> Role.ROCKET;
            case "aamissile" -> Role.AA_MISSILE;
            case "missile", "atmissile", "asmissile", "tvmissile" -> Role.AG_MISSILE;
            case "bomb" -> Role.BOMB;
            case "torpedo" -> Role.TORPEDO;
            // Marker rockets, smoke, dispensers, targeting pods and dummies hurt nothing.
            default -> Role.NONE;
        };
    }

    /**
     * How well a role suits a target, 0 for not at all: missiles for what they were made for,
     * guns against aircraft and soft targets, rockets and bombs against the ground.
     *
     * @param air     the target flies
     * @param armored the target is a vehicle
     * @param water   the target is in water
     * @param inRange the target is within the weapon's range now
     */
    public static double score(Role role, boolean air, boolean armored, boolean water, boolean inRange) {
        double s;
        if (air) {
            s = switch (role) {
                case AA_MISSILE -> 10;
                case GUN -> 8;
                case ROCKET -> 2;
                default -> 0;
            };
        } else if (armored) {
            s = switch (role) {
                case AG_MISSILE -> 10;
                case ROCKET -> 8;
                case BOMB -> 7;
                case GUN -> 5;
                case TORPEDO -> water ? 9 : 0;
                default -> 0;
            };
        } else {
            s = switch (role) {
                case GUN -> 9;
                case ROCKET -> 8;
                case BOMB -> 6;
                case AG_MISSILE -> 4;
                case TORPEDO -> water ? 5 : 0;
                default -> 0;
            };
        }
        return inRange ? s : s * 0.75;
    }

    /**
     * A shot's flight as {@code BaseBulletEntity} moves it: each tick gravity is added to its
     * velocity, drag taken off its horizontal speed, and it moves its velocity times {@code scale}.
     *
     * @param speed            blocks a tick at launch, before the step factor
     * @param scale            the step factor of rounds faster than 4 blocks a tick
     * @param inherits         the shot leaves with the shooter's velocity (a bomb)
     * @param addsShooterSpeed the shooter's speed is added to the launch speed
     * @param halfStep         the shot starts half a tick out (the guns' muzzle offset)
     * @param damp             a bomb's horizontal slowing each tick
     * @param life             ticks it flies at most
     */
    public record Ballistics(double speed, double gravity, double drag, double scale, boolean inherits,
            boolean addsShooterSpeed, boolean halfStep, double damp, int life) {

        /** The flight of a weapon of {@code type} with these definition values. */
        public static Ballistics of(@Nullable String type, float acceleration, float gravity, double dragInAir,
                boolean speedDependsAircraft, int timeFuse) {
            String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
            if (t.equals("bomb")) {
                return new Ballistics(0.0, gravity, 0.0, 1.0, true, false, false, 0.999, MAX_BOMB_TICKS);
            }
            int life = timeFuse > 0 ? Math.min(timeFuse, MAX_TICKS) : MAX_TICKS;
            boolean bullet = t.equals("machinegun1") || t.equals("machinegun2") || t.equals("buckshot");
            double scale = (bullet || t.equals("rocket")) && acceleration > 4.0F ? acceleration / 4.0 : 1.0;
            return new Ballistics(Math.min(acceleration, 3.9), gravity, dragInAir, scale, false, speedDependsAircraft,
                    t.startsWith("machinegun"), 1.0, life);
        }

        /** Blocks a tick the shot first covers. */
        public double muzzleSpeed() {
            return this.speed * this.scale;
        }
    }

    /**
     * A firing solution.
     *
     * @param dir   the unit direction to fire along
     * @param ticks the ticks the shot takes to its closest point to the target
     * @param miss  how far it passes from the predicted target there, in blocks
     */
    public record Solution(Vec3 dir, double ticks, double miss) {
    }

    /** Where a target is {@code t} ticks on, from its velocity and (for a short while) its acceleration. */
    public static Vec3 predict(Vec3 pos, Vec3 vel, Vec3 acc, double t) {
        double ta = Math.min(t, ACCEL_HORIZON);
        double accel = 0.5 * ta * ta + ta * (t - ta);
        return new Vec3(pos.x + vel.x * t + acc.x * accel, pos.y + vel.y * t + acc.y * accel,
                pos.z + vel.z * t + acc.z * accel);
    }

    /**
     * The direction to fire a ballistic shot from {@code muzzle} so it meets a target at
     * {@code target} moving with {@code targetVel} and {@code targetAcc}: it flies the shot, moves
     * the aim point by what it missed by at its closest pass, and flies again until the miss is
     * under a tenth of a block. Returns the best it found, or null when the target is on the muzzle.
     */
    public static @Nullable Solution intercept(Vec3 muzzle, Vec3 shooterVel, Vec3 target, Vec3 targetVel,
            Vec3 targetAcc, Ballistics b) {
        Vec3 aim = target;
        Solution best = null;
        for (int i = 0; i < ITERATIONS; i++) {
            Vec3 d = aim.subtract(muzzle);
            if (d.lengthSqr() < 1.0E-6) {
                return best;
            }
            Vec3 dir = d.normalize();
            double[] pass = closestPass(muzzle, dir, shooterVel, target, targetVel, targetAcc, b);
            Vec3 miss = new Vec3(pass[1], pass[2], pass[3]);
            double len = miss.length();
            if (best == null || len < best.miss()) {
                best = new Solution(dir, pass[0], len);
            }
            if (len < CONVERGED) {
                break;
            }
            aim = aim.add(miss);
        }
        return best;
    }

    /**
     * Flies a shot along {@code dir} and returns {ticks, dx, dy, dz} at its closest pass: the
     * predicted target less the shot, interpolated within the tick.
     */
    static double[] closestPass(Vec3 muzzle, Vec3 dir, Vec3 shooterVel, Vec3 target, Vec3 targetVel, Vec3 targetAcc,
            Ballistics b) {
        double speed = b.speed() + (b.addsShooterSpeed() ? shooterVel.length() : 0.0);
        double mx = b.inherits() ? shooterVel.x : dir.x * speed;
        double my = b.inherits() ? shooterVel.y : dir.y * speed;
        double mz = b.inherits() ? shooterVel.z : dir.z * speed;
        double px = muzzle.x;
        double py = muzzle.y;
        double pz = muzzle.z;
        if (b.halfStep()) {
            px += mx * 0.5;
            py += my * 0.5;
            pz += mz * 0.5;
        }
        double rx = target.x - px;
        double ry = target.y - py;
        double rz = target.z - pz;
        double bestSq = rx * rx + ry * ry + rz * rz;
        double[] best = {0.0, rx, ry, rz};
        for (int k = 1; k <= b.life(); k++) {
            my += b.gravity();
            if (b.drag() != 0.0) {
                double s = Math.sqrt(mx * mx + my * my + mz * mz);
                if (s > 0.0) {
                    mx -= mx / s * b.drag();
                    mz -= mz / s * b.drag();
                }
            }
            px += mx * b.scale();
            py += my * b.scale();
            pz += mz * b.scale();
            mx *= b.damp();
            mz *= b.damp();
            Vec3 t = predict(target, targetVel, targetAcc, k);
            double nx = t.x - px;
            double ny = t.y - py;
            double nz = t.z - pz;
            // The closest point of the relative path between the last tick and this one.
            double ex = nx - rx;
            double ey = ny - ry;
            double ez = nz - rz;
            double el = ex * ex + ey * ey + ez * ez;
            double u = el > 1.0E-12 ? Mth.clamp(-(rx * ex + ry * ey + rz * ez) / el, 0.0, 1.0) : 1.0;
            double cx = rx + ex * u;
            double cy = ry + ey * u;
            double cz = rz + ez * u;
            double cSq = cx * cx + cy * cy + cz * cz;
            if (cSq < bestSq) {
                bestSq = cSq;
                best = new double[] {k - 1 + u, cx, cy, cz};
            } else if (k - best[0] > 3.0) {
                break;
            }
            rx = nx;
            ry = ny;
            rz = nz;
        }
        return best;
    }

    /**
     * Where a bomb released now would land, at the target's height.
     *
     * @param ticks its fall
     * @param along how far the predicted target lies beyond the impact along the track: positive
     *              while the bomb would still fall short
     * @param cross how far the target lies to the side of the track
     */
    public record Drop(Vec3 impact, double ticks, double along, double cross) {
    }

    /** The bomb's fall from {@code release} with the shooter's {@code vel}, or null if it never gets down. */
    public static @Nullable Drop drop(Vec3 release, Vec3 vel, Vec3 target, Vec3 targetVel, Ballistics b) {
        double hs = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        if (hs < 0.05 || release.y <= target.y) {
            return null;
        }
        double mx = vel.x;
        double my = vel.y;
        double mz = vel.z;
        double px = release.x;
        double py = release.y;
        double pz = release.z;
        for (int k = 1; k <= b.life(); k++) {
            my += b.gravity();
            double nx = px + mx;
            double ny = py + my;
            double nz = pz + mz;
            if (ny <= target.y) {
                double f = (py - target.y) / Math.max(py - ny, 1.0E-9);
                Vec3 impact = new Vec3(px + (nx - px) * f, target.y, pz + (nz - pz) * f);
                double t = k - 1 + f;
                double ox = target.x + targetVel.x * t - impact.x;
                double oz = target.z + targetVel.z * t - impact.z;
                double hx = vel.x / hs;
                double hz = vel.z / hs;
                double along = ox * hx + oz * hz;
                double cross = Math.abs(ox * hz - oz * hx);
                return new Drop(impact, t, along, cross);
            }
            px = nx;
            py = ny;
            pz = nz;
            mx *= b.damp();
            mz *= b.damp();
        }
        return null;
    }

    /**
     * A weapon's mount: its fixed direction off the nose and how far it can turn from it, in
     * degrees (pitch positive down, as Minecraft's).
     */
    public record Mount(float yaw, float pitch, float minYaw, float maxYaw, float minPitch, float maxPitch) {

        /** A weapon fixed to the nose that the pilot can lay {@code gimbal} degrees each way. */
        public static Mount fixed(float yaw, float pitch, float gimbal) {
            return new Mount(yaw, pitch, -gimbal, gimbal, -gimbal, gimbal);
        }

        /** The mount's centre, absolute, for a nose at {@code noseYaw}/{@code nosePitch}. */
        public float[] centre(float noseYaw, float nosePitch) {
            return new float[] {Mth.wrapDegrees(noseYaw + this.yaw), nosePitch + this.pitch};
        }

        /** The direction nearest {@code wantYaw}/{@code wantPitch} the mount can fire, absolute: {yaw, pitch}. */
        public float[] clamp(float noseYaw, float nosePitch, float wantYaw, float wantPitch) {
            float relYaw = Mth.clamp(Mth.wrapDegrees(wantYaw - noseYaw - this.yaw), this.minYaw, this.maxYaw);
            float relPitch = Mth.clamp(wantPitch - nosePitch - this.pitch, this.minPitch, this.maxPitch);
            return new float[] {Mth.wrapDegrees(noseYaw + this.yaw + relYaw), nosePitch + this.pitch + relPitch};
        }
    }

    /** Minecraft's look vector for a yaw and pitch in degrees. */
    public static Vec3 look(float yaw, float pitch) {
        double y = Math.toRadians(yaw);
        double p = Math.toRadians(pitch);
        return new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
    }

    public static float yaw(Vec3 d) {
        return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
    }

    public static float pitch(Vec3 d) {
        return (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
    }

    /** The angle between two directions, in degrees. */
    public static double angle(Vec3 a, Vec3 b) {
        double la = a.length();
        double lb = b.length();
        if (la < 1.0E-9 || lb < 1.0E-9) {
            return 180.0;
        }
        return Math.toDegrees(Math.acos(Mth.clamp(a.dot(b) / (la * lb), -1.0, 1.0)));
    }

    /** The half angle a target of {@code radius} blocks fills at {@code dist}, in degrees. */
    public static double tolerance(double radius, double dist) {
        return Math.toDegrees(Math.atan2(radius, Math.max(dist, 1.0)));
    }
}
