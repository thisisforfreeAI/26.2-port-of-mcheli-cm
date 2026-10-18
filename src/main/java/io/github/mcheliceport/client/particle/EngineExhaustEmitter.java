package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.util.McheliMath;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** At most two outlets per eligible vehicle tick, plus optional tiny jet accents. */
public final class EngineExhaustEmitter {
    private static WeakReference<ClientLevel> world = new WeakReference<>(null);
    private static final LinkedHashMap<Integer, Long> stamps = new LinkedHashMap<>();
    private static long tick = Long.MIN_VALUE;
    private static int emitted;
    private EngineExhaustEmitter() {}

    public static boolean emit(AircraftEntity aircraft) {
        if (!(aircraft.level() instanceof ClientLevel level) || !(aircraft instanceof HeliEntity || aircraft instanceof PlaneEntity)) return false;
        var info = aircraft.getAcInfo();
        if (info == null) return false;
        float nozzle = aircraft instanceof PlaneEntity plane ? plane.getNozzleRotation() : 0;
        boolean legacyNozzle = info.engineOutlets.isEmpty() && info instanceof io.github.mcheliceport.content.aircraft.PlaneInfo p && !p.nozzles.isEmpty();
        boolean supported = !info.engineOutlets.isEmpty() || info instanceof io.github.mcheliceport.content.aircraft.PlaneInfo p
                && (!p.nozzles.isEmpty() || !p.exhaustFlames.isEmpty() && Math.abs(nozzle) < 1);
        if (!supported) return false;
        // A supported enhanced plane owns its nozzle path even when distance/load/off rejects emission.
        float strength = EngineExhaustLook.strength(aircraft.getCurrentThrottle());
        if (strength <= 0 || aircraft.isDestroyed() || !aircraft.canUseFuel()
                || !Float.isFinite(info.particlesScale) || info.particlesScale <= 0) return true;
        var quality = ClientConfig.get(ClientConfig.EFFECT_BUDGET);
        int period = EngineExhaustLook.period(quality, EffectPerformance.pressure());
        long now = level.getGameTime();
        if (Math.floorMod(now + aircraft.getId(), period) != 0) return true;
        double range = quality == ClientConfig.EffectBudget.LOW ? 32 : quality == ClientConfig.EffectBudget.BALANCED ? 64 : 96;
        double distance = Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(aircraft.position());
        if (!Double.isFinite(distance) || distance > range * range || Minecraft.getInstance().level != level) return true;
        if (world.get() != level) { clear(); world = new WeakReference<>(level); }
        if (now != tick) { tick = now; emitted = 0; }
        if (stamps.getOrDefault(aircraft.getId(), Long.MIN_VALUE) == now) return true;
        stamps.put(aircraft.getId(), now);
        if (stamps.size() > 128) stamps.remove(stamps.keySet().iterator().next());
        int cap = EngineExhaustLook.cap(quality);
        if (emitted >= cap) return true;
        var outlets = EngineExhaustLook.outlets(info, nozzle);
        int count = Math.min(2, outlets.size());
        float distanceFade = (float) Math.clamp((range - Math.sqrt(distance)) / (range * 0.4), 0, 1);
        Vec3 motion = aircraft.getDeltaMovement().scale(0.8);
        for (int i = 0; i < count && emitted < cap; i++) {
            var outlet = outlets.get(Math.floorMod((int) (now / period) * 2 + i, outlets.size()));
            Vec3 origin = McheliMath.rotVec3(outlet.position(), -aircraft.getYaw(), -aircraft.getPitch(), -aircraft.getRoll())
                    .add(aircraft.position()).add(0, io.github.mcheliceport.content.aircraft.AircraftInfo.GLOBAL_Y_OFFSET, 0);
            Vec3 direction = legacyNozzle ? McheliMath.rotVec3(0, 0, 1, -aircraft.getYaw() - 180, aircraft.getPitch() - nozzle, aircraft.getRoll())
                    : McheliMath.rotVec3(outlet.direction(), -aircraft.getYaw(), -aircraft.getPitch(), -aircraft.getRoll());
            Vec3 position = origin.add(direction.scale(legacyNozzle ? 1.15 : 0.15));
            BlockPos block = BlockPos.containing(position);
            if (!level.hasChunkAt(block) || !level.getFluidState(block).isEmpty()) continue;
            float radius = EngineExhaustLook.radius(outlet.size() * Math.min(2, info.particlesScale), strength);
            if (!EffectAdmission.admit(level, position.x, position.y, position.z, false, radius)) continue;
            Vec3 velocity = direction.scale((outlet.jet() ? 0.18 : 0.07) + strength * (outlet.jet() ? 0.25 : 0.1)).add(motion);
            float angle = (float) ((aircraft.getId() * 0.73 + i * 2.1 + now * 0.15) % (Math.PI * 2));
            Minecraft.getInstance().particleEngine.add(new EnginePlumeParticle(level, position.x, position.y, position.z,
                    velocity.x, velocity.y, velocity.z, radius, (0.12F + strength * 0.12F) * distanceFade,
                    outlet.jet() ? 12 : 18, angle));
            emitted++;
            if (outlet.jet() && strength > 0.75F && EffectPerformance.optionalGlows() && emitted < cap
                    && EffectAdmission.admit(level, position.x, position.y, position.z, false, radius)) {
                Minecraft.getInstance().particleEngine.add(new EngineGlowParticle(level, position,
                        velocity, radius * 0.7F, 0.3F * strength * distanceFade));
                emitted++;
            }
        }
        return true;
    }

    public static int emitted() { return emitted; }
    public static int cacheSize() { return stamps.size(); }
    public static void clear() { world.clear(); stamps.clear(); tick = Long.MIN_VALUE; emitted = 0; }
}
