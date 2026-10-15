package io.github.mcheliceport.entity.plane;

import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.WingedAircraftInfo;
import io.github.mcheliceport.effect.ParticleParam;
import io.github.mcheliceport.util.McheliMath;
import java.util.function.Consumer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Client particle formulas from MCH_EntityPlane, with world queries and emission supplied by the caller. */
public final class PlaneParticles {
    public interface World {
        boolean isAir(int x, int y, int z);
        boolean isWater(int x, int y, int z);
    }

    public interface Sink extends Consumer<ParticleParam> {
        void tileCrack(int blockX, int blockY, int blockZ, double x, double y, double z,
                double mx, double my, double mz);
    }

    public record Frame(Vec3 position, Vec3 previousPosition, Vec3 motion, float yaw, float pitch,
            float roll, float nozzle, double throttle, int hp, int maxHp, float width, double minY,
            @Nullable Vec3 pilotSeat) {
    }

    private Vec3[] previousSmoke = new Vec3[0];
    private boolean firstSmoke = true;

    public void reset() {
        this.previousSmoke = new Vec3[0];
        this.firstSmoke = true;
    }

    public void tick(WingedAircraftInfo info, Frame frame, RandomSource random, World world, Sink sink) {
        landingGear(frame, random, sink);
        nozzle(info, frame, random, world, sink);
        this.damage(info, frame, random, sink);
        splash(info, frame, random, world, sink);
    }

    /** Upstream onUpdate_ParticleLandingGear; the sink checks whether the sampled block has dust. */
    public static void landingGear(Frame f, RandomSource random, Sink sink) {
        if (f.motion.x * f.motion.x + f.motion.z * f.motion.z <= 0.01) {
            return;
        }
        sink.tileCrack(Mth.floor(f.position.x + 0.5), Mth.floor(f.position.y - 0.5), Mth.floor(f.position.z + 0.5),
                f.position.x + (random.nextFloat() - 0.5) * f.width, f.minY + 0.1,
                f.position.z + (random.nextFloat() - 0.5) * f.width,
                -f.motion.x * 4.0, 1.5, -f.motion.z * 4.0);
    }

    /** Upstream onUpdate_ParticleNozzle, including the broader cloud near obstructing blocks. */
    public static void nozzle(WingedAircraftInfo info, Frame f, RandomSource random, World world, Consumer<ParticleParam> sink) {
        if (!info.haveNozzle() || f.throttle <= 0.1F) {
            return;
        }
        Vec3 direction = McheliMath.rotVec3(0.0, 0.0, 1.0, -f.yaw - 180.0F, f.pitch - f.nozzle, f.roll);
        for (AircraftInfo.DrawnPart part : info.nozzles) {
            if (random.nextFloat() > f.throttle * 1.5) {
                continue;
            }
            Vec3 position = McheliMath.rotVec3(part.pos, -f.yaw, -f.pitch, -f.roll).add(f.position).add(direction);
            float spread = world.isAir((int) (position.x + direction.x * 3.0),
                    (int) (position.y + direction.y * 3.0), (int) (position.z + direction.z * 3.0)) ? 0.7F : 2.0F;
            sink.accept(new ParticleParam("smoke", position.x, position.y, position.z,
                    direction.x + (random.nextFloat() - 0.5F) * spread, direction.y,
                    direction.z + (random.nextFloat() - 0.5F) * spread, 5.0F * info.particlesScale));
        }
    }

    /** Upstream onUpdate_Particle2. Rotor histories belong to this vehicle, never to the shared definition. */
    public void damage(WingedAircraftInfo info, Frame f, RandomSource random, Consumer<ParticleParam> sink) {
        if (f.hp >= f.maxHp * 0.5) {
            return;
        }
        int rotors = info.rotorList.size();
        if (this.previousSmoke.length != rotors + 1) {
            this.previousSmoke = new Vec3[rotors + 1];
            this.firstSmoke = true;
        }
        for (int i = 0; i < rotors; i++) {
            consumeSmokeGate(f, random);
            Vec3 position = McheliMath.rotVec3(info.rotorList.get(i).pos, -f.yaw, -f.pitch, -f.roll).add(f.position);
            this.damageTrail(i, position, 1.0F, random, sink);
        }
        consumeSmokeGate(f, random);
        Vec3 position = f.position;
        if (f.pilotSeat != null) {
            position = position.add(McheliMath.rotVec3(0.0, f.pilotSeat.y, -2.0, -f.yaw, -f.pitch, -f.roll));
        }
        this.damageTrail(rotors, position, rotors == 0 ? 2.0F : 1.0F, random, sink);
        this.firstSmoke = false;
    }

    private static void consumeSmokeGate(Frame f, RandomSource random) {
        // The pinned upstream computes spawnSmoke but never reads it in its emitter. Preserve
        // both that behaviour and its random calls; revising smoke density belongs to visual polish.
        if (f.hp >= f.maxHp * 0.2 && f.maxHp > 0) {
            int delay = (int) (((double) f.hp / f.maxHp - 0.2) / 0.3 * 15.0);
            if (delay > 0) {
                random.nextInt(delay);
            }
        }
    }

    private void damageTrail(int index, Vec3 position, float size, RandomSource random, Consumer<ParticleParam> sink) {
        if (this.firstSmoke || this.previousSmoke[index] == null) {
            this.previousSmoke[index] = position;
        }
        Vec3 previous = this.previousSmoke[index];
        double dx = position.x - previous.x;
        double dy = position.y - previous.y;
        double dz = position.z - previous.z;
        // 1.12 MathHelper.sqrt(double) rounded the result to float, not its input.
        int count = (int) ((float) Math.sqrt(dx * dx + dy * dy + dz * dz) / 0.3) + 1;
        for (int i = 0; i < count; i++) {
            float color = 0.2F + random.nextFloat() * 0.3F;
            // /3.0 is upstream's interpolation, even when count differs from three.
            ParticleParam p = new ParticleParam("smoke", previous.x + dx * i / 3.0,
                    previous.y + dy * i / 3.0, previous.z + dz * i / 3.0);
            p.motionX = size * (random.nextDouble() - 0.5) * 0.3;
            p.motionY = size * random.nextDouble() * 0.1;
            p.motionZ = size * (random.nextDouble() - 0.5) * 0.3;
            p.size = size * (random.nextInt(5) + 5.0F);
            p.setColor(0.7F + random.nextFloat() * 0.1F, color, color, color);
            sink.accept(p);
        }
        this.previousSmoke[index] = position;
    }

    /** Upstream onUpdate_ParticleSplash and setParticleSplash. */
    public static void splash(AircraftInfo info, Frame f, RandomSource random, World world, Consumer<ParticleParam> sink) {
        double mx = f.position.x - f.previousPosition.x;
        double mz = f.position.z - f.previousPosition.z;
        double distance = Math.min(1.0, mx * mx + mz * mz);
        for (AircraftInfo.ParticleSplash part : info.particleSplashs) {
            for (int i = 0; i < part.num(); i++) {
                if (distance <= 0.03 + random.nextFloat() * 0.1) {
                    continue;
                }
                Vec3 position = McheliMath.rotVec3(part.pos(), -f.yaw, -f.pitch, -f.roll).add(f.position);
                position = position.add(random.nextDouble() - 0.5, (random.nextDouble() - 0.5) * 0.5,
                        random.nextDouble() - 0.5);
                if (!world.isWater((int) (position.x + 0.5), (int) position.y, (int) (position.z + 0.5))) {
                    continue;
                }
                float color = random.nextFloat() * 0.3F + 0.7F;
                ParticleParam p = new ParticleParam("smoke", position.x, position.y, position.z);
                p.motionX = -mx * part.acceleration() + (random.nextFloat() - 0.5) * 0.7;
                p.motionY = part.motionY();
                p.motionZ = -mz * part.acceleration() + (random.nextFloat() - 0.5) * 0.7;
                p.size = (float) (part.size() * (0.5 + distance * 0.5)) * (random.nextFloat() * 0.2F + 0.8F);
                p.setColor(0.9F, color, color, color);
                p.age = part.age() + (int) (random.nextFloat() * 0.5 * part.age());
                p.gravity = part.gravity();
                sink.accept(p);
            }
        }
    }
}
