package io.github.mcheliceport.entity.tank;

import io.github.mcheliceport.content.aircraft.TankInfo;
import io.github.mcheliceport.effect.McheliEffects;
import io.github.mcheliceport.effect.ParticleParam;
import io.github.mcheliceport.entity.aircraft.GroundProbe;
import io.github.mcheliceport.entity.plane.PlaneParticles;
import io.github.mcheliceport.util.McheliMath;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import java.util.function.Consumer;

/** Pinned tank damage smoke, wheel dust and content-defined water splashes. */
public final class TankParticles {
    private TankParticles() { }

    public static void damage(TankInfo info, int hp, int maxHp, Vec3 position, float yaw, float pitch, float roll,
            RandomSource random, Consumer<ParticleParam> sink) {
        if (hp >= maxHp * 0.5) { return; }
        int count = info.extraBoundingBox.size();
        for (var box : info.extraBoundingBox) {
            if (hp >= maxHp * 0.2 && maxHp > 0) {
                int gate = (int) (((double) hp / maxHp - 0.2) / 0.3 * 15);
                if (gate > 0) { random.nextInt(gate); }
            } else {
                smoke(McheliMath.rotVec3(box.offsetX, box.offsetY, box.offsetZ, -yaw, -pitch, -roll).add(position), 1, hp, maxHp, random, sink);
            }
        }
        boolean emit = true;
        if (hp >= maxHp * 0.2 && maxHp > 0) {
            int gate = (int) (((double) hp / maxHp - 0.2) / 0.3 * 15);
            if (gate > 0 && random.nextInt(gate) > 0) { emit = false; }
        }
        if (emit) {
            Vec3 p = position;
            if (!info.seatList.isEmpty()) { p = p.add(McheliMath.rotVec3(0, info.seatList.getFirst().pos.y, -2, -yaw, -pitch, -roll)); }
            smoke(p, count == 0 ? 2 : 1, hp, maxHp, random, sink);
        }
    }

    private static void smoke(Vec3 p, float size, int hp, int maxHp, RandomSource random, Consumer<ParticleParam> sink) {
        float color = 0.2F + random.nextFloat() * 0.3F;
        ParticleParam particle = new ParticleParam("smoke", p.x, p.y, p.z);
        particle.motionX = size * (random.nextDouble() - 0.5) * 0.3;
        particle.motionY = size * random.nextDouble() * 0.1;
        particle.motionZ = size * (random.nextDouble() - 0.5) * 0.3;
        particle.size = size * (random.nextInt(5) + 5.0F);
        particle.setColor(0.7F + random.nextFloat() * 0.1F, color, color, color);
        particle.setDamageHeat(hp, maxHp);
        sink.accept(particle);
    }

    /**
     * Upstream's iron curtain haze ({@code onUpdate_Particle2}): dark red puffs at every extra hit
     * box, spreading wider through each 40-tick cycle. Upstream tinted vanilla clouds; McHeli's own
     * smoke takes the tint here.
     */
    public static void ironCurtain(TankEntity tank, TankInfo info, RandomSource random) {
        int tick = tank.getIronCurtainTick();
        if (tick <= 0) {
            return;
        }
        float factor = 0.5F + 0.5F * (float) Math.sin(tick * 0.15F);
        float spread = 0.015F * (tick % 40 + 1);
        for (var box : info.extraBoundingBox) {
            Vec3 pos = tank.getTransformedPosition(new Vec3(box.offsetX, box.offsetY, box.offsetZ));
            int count = 2 + random.nextInt(3);
            for (int i = 0; i < count; i++) {
                ParticleParam p = new ParticleParam("smoke", pos.x + random.nextGaussian() * 0.3,
                        pos.y + random.nextGaussian() * 0.2, pos.z + random.nextGaussian() * 0.3,
                        (random.nextFloat() - 0.5F) * spread, 0.01F + random.nextFloat() * 0.02F,
                        (random.nextFloat() - 0.5F) * spread, 1.0F);
                p.r = 0.5F * factor;
                p.g = 0.1F * factor;
                p.b = 0.1F * factor;
                p.age = 20;
                McheliEffects.spawnParticle(tank.level(), p);
            }
        }
    }

    public static void tick(TankEntity tank, TankInfo info, RandomSource random, float previousYaw) {
        ironCurtain(tank, info, random);
        if (!McheliEffects.damageSmoke(tank)) damage(info, tank.getHP(), tank.getMaxHP(), tank.position(), tank.getYaw(), tank.getPitch(), tank.getRoll(),
                random, p -> McheliEffects.spawnParticle(tank.level(), p));
        if (!info.wheels.isEmpty() && tank.getDeltaMovement().horizontalDistanceSqr() + Math.abs(previousYaw - tank.getYaw()) > 0.001) {
            for (int i = 0; i < 2; i++) {
                int index = random.nextInt(info.wheels.size() * 2);
                Vec3 local = info.wheels.get(index / 2).pos();
                Vec3 p = tank.getTransformedPosition(new Vec3(index % 2 == 0 ? local.x : -local.x, local.y, local.z));
                BlockPos at = BlockPos.containing(p.x + 0.5, p.y - 0.5, p.z + 0.5);
                if (tank.level().getBlockState(at).isAir()) { at = BlockPos.containing(p.x + 0.5, p.y + 0.5, p.z + 0.5); }
                if (!tank.level().getBlockState(at).isAir()) {
                    McheliEffects.spawnTileCrack(tank.level(), at.getX(), at.getY(), at.getZ(),
                            p.x + random.nextFloat() - 0.5, p.y + 0.1, p.z + random.nextFloat() - 0.5,
                            -tank.motionX() * 4 + (random.nextFloat() - 0.5) * 0.1, random.nextFloat() * 0.5,
                            -tank.motionZ() * 4 + (random.nextFloat() - 0.5) * 0.1);
                }
            }
        }
        var frame = new PlaneParticles.Frame(tank.position(), new Vec3(tank.xo, tank.yo, tank.zo), tank.getDeltaMovement(),
                tank.getYaw(), tank.getPitch(), tank.getRoll(), 0, tank.getCurrentThrottle(), tank.getHP(), tank.getMaxHP(),
                tank.getBbWidth(), tank.getBoundingBox().minY, null);
        PlaneParticles.splash(info, frame, random, new PlaneParticles.World() {
            @Override public boolean isAir(int x, int y, int z) { return tank.level().getBlockState(new BlockPos(x, y, z)).isAir(); }
            @Override public boolean isWater(int x, int y, int z) { return GroundProbe.isWater(tank.level().getBlockState(new BlockPos(x, y, z))); }
        }, p -> McheliEffects.spawnParticle(tank.level(), p));
    }
}
