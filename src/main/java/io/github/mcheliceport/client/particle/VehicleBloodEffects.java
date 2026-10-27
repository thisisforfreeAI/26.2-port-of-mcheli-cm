package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.client.effect.BloodStains;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.effect.VehicleBloodRules;
import io.github.mcheliceport.net.VehicleBloodPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Tiny lit droplets, not glowing mist. Shared particle admission bounds simultaneous kills. */
@EventBusSubscriber(modid="mcheli_ce_port", value=Dist.CLIENT)
public final class VehicleBloodEffects {
    private static ClientLevel world;
    private static VehicleBloodRules.Gate gate = new VehicleBloodRules.Gate(4);
    private static int droplets;
    private VehicleBloodEffects() {}
    public static boolean enabled() { return EffectQuality.enhanced() && ClientConfig.get(ClientConfig.VEHICLE_BLOOD); }
    public static void clear() { world = null; gate = new VehicleBloodRules.Gate(4); droplets = 0; BloodStains.clear(); }
    public static int droplets() { return droplets; }

    public static void receive(VehicleBloodPayload p) {
        var mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (!enabled() || !p.valid() || level == null || mc.player == null || mc.isPaused()) return;
        Vec3 feet = new Vec3(p.x(), p.y(), p.z());
        if (!level.hasChunkAt(BlockPos.containing(feet))
                || mc.gameRenderer.mainCamera().position().distanceToSqr(feet) > 48*48
                || !level.getFluidState(BlockPos.containing(feet.add(0,p.height()*.5,0))).isEmpty()) return;
        if (world != level) { clear(); world = level; }
        if (!gate.admit(p.victim(), level.getGameTime())) return;
        RandomSource random = RandomSource.create(p.seed());
        Vec3 direction = new Vec3(p.dx(), p.dy(), p.dz());
        int count = (p.cause() == VehicleBloodRules.BLAST ? 14 : 10) / (1 + EffectPerformance.pressure());
        for (int i=0; i<count; i++) {
            Vec3 at = feet.add((random.nextDouble()-.5)*p.width()*.6,
                    p.height()*(.3+random.nextDouble()*.35), (random.nextDouble()-.5)*p.width()*.6);
            float radius = .025F + random.nextFloat()*.03F;
            if (!VehicleAtmosphere.admit(level, at, radius)) continue;
            Vec3 velocity = direction.scale(.08 + random.nextDouble()*.12)
                    .add((random.nextDouble()-.5)*.18, .05+random.nextDouble()*.14, (random.nextDouble()-.5)*.18);
            mc.particleEngine.add(new BloodParticle(level, at, velocity, radius, 14+random.nextInt(10), random.nextFloat()));
            droplets++;
        }
        BloodStains.queue(level, feet, p.width(), p.seed());
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level != world || !enabled()) clear();
    }
}
