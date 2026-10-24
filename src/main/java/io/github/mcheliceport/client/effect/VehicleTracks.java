package io.github.mcheliceport.client.effect;

import io.github.mcheliceport.client.particle.EffectPerformance;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.VehicleInfo;
import io.github.mcheliceport.entity.ground.GroundVehicleEntity;
import io.github.mcheliceport.entity.tank.TankEntity;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Samples existing local collision contacts after ground vehicles tick; never scans the world entity list. */
@EventBusSubscriber(modid = "mcheli_ce_port", value = Dist.CLIENT)
public final class VehicleTracks {
    private static WeakReference<ClientLevel> world = new WeakReference<>(null);
    private static final LinkedHashMap<Integer, State> states = new LinkedHashMap<>(64, 0.75F, true);
    private static long tick = Long.MIN_VALUE;
    private static int requests, vehicles;
    private VehicleTracks() {}
    public static void clear() { world.clear(); states.clear(); tick = Long.MIN_VALUE; requests = vehicles = 0; }
    public static int requests() { return requests; }
    public static int vehicles() { return vehicles; }
    public static int stateCount() { return states.size(); }

    @SubscribeEvent public static void entityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof GroundVehicleEntity vehicle && vehicle.level() instanceof ClientLevel) sample(vehicle);
    }

    public static void sample(GroundVehicleEntity vehicle) {
        if (!(vehicle.level() instanceof ClientLevel level) || Minecraft.getInstance().level != level) return;
        if (!EffectQuality.enhanced() || !ClientConfig.get(ClientConfig.VEHICLE_TRACKS)) { clear(); return; }
        if (world.get() != level) { clear(); world = new WeakReference<>(level); }
        long now = level.getGameTime();
        if (tick != now) {
            tick = now; requests = vehicles = 0;
            states.values().removeIf(s -> now < s.seen || now - s.seen > 120);
        }
        AircraftInfo info = vehicle.getAcInfo();
        if (info == null || !(vehicle instanceof TankEntity)
                && !(info instanceof VehicleInfo movable && movable.isEnableMove && !info.wheels.isEmpty())) return;
        if (vehicle.isDestroyed() || vehicle.isInWater()) {
            State old = states.get(vehicle.getId()); if (old != null) old.clearPaths();
            return;
        }
        var q = ClientConfig.get(ClientConfig.EFFECT_BUDGET);
        int pressure = EffectPerformance.pressure();
        int period = q == ClientConfig.EffectBudget.LOW ? 3 : pressure >= 2 ? 2 : 1;
        int cap = Math.max(4, (q == ClientConfig.EffectBudget.LOW ? 8 : q == ClientConfig.EffectBudget.HIGH ? 24 : 16) / (1 + pressure));
        int range = TrackTuning.range(q);
        if (Math.floorMod(now + vehicle.getId(), period) != 0 || vehicles >= 8 || requests >= cap
                || Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(vehicle.position()) > range * range) return;
        State state = states.get(vehicle.getId());
        if (state == null || !state.uuid.equals(vehicle.getUUID()) || state.info != info) {
            if (states.size() == 64) states.remove(states.keySet().iterator().next());
            state = new State(vehicle.getUUID(), info); states.put(vehicle.getId(), state);
        }
        if (state.seen == now) return;
        state.seen = now; vehicles++;
        if (vehicle instanceof TankEntity tank && tank.terrain() != null && tank.getTankInfo() != null) {
            var terrain = tank.terrain();
            boolean tread = tank.getTankInfo().weightType != 1;
            if (tread) {
                for (int side = 0; side < 2; side++) {
                    int rear = -1, front = -1;
                    for (int i = side; i < Math.min(terrain.wheelCount(), 32); i += 2) {
                        if (!terrain.contactGrounded(i)) continue;
                        double z = terrain.contactLocal(i).z;
                        if (z <= 0 && (rear < 0 || z > terrain.contactLocal(rear).z)) rear = i;
                        if (z >= 0 && (front < 0 || z < terrain.contactLocal(front).z)) front = i;
                    }
                    int chosen = rear < 0 ? front : front < 0 ? rear
                            : Math.abs(terrain.contactLocal(rear).z) < terrain.contactLocal(front).z ? rear : front;
                    Vec3 contact = chosen < 0 ? null : terrain.contactPosition(chosen);
                    boolean bracket = rear >= 0 && front >= 0;
                    if (bracket && rear != front) {
                        Vec3 blended = TreadContact.center(terrain.contactPosition(rear), terrain.contactLocal(rear).z,
                                terrain.contactPosition(front), terrain.contactLocal(front).z);
                        if (blended != null) contact = blended;
                        else bracket = false; // Unusual addon spans retain the original nearest-contact fallback.
                    }
                    if (contact == null) { state.lane(side).clear(); state.heading[side] = null; state.anchors[side] = -1; continue; }
                    // Bracket pairs may change without moving the stamp origin. Single-wheel support
                    // still resets on an anchor change, avoiding bridges across airborne gaps.
                    int anchor = bracket ? -2 : chosen;
                    if (state.anchors[side] != anchor) { state.lane(side).clear(); state.heading[side] = null; state.anchors[side] = anchor; }
                    float width = info.wheels.get(chosen / 2).size() * 0.7F;
                    emit(level, state, side, contact, true, width, TrackMarks.Kind.TREAD, now, cap);
                }
            } else {
                int count = Math.min(8, terrain.wheelCount());
                for (int i = 0; i < Math.min(4, count); i++) {
                    int index = (state.cursor + i) % count;
                    emit(level, state, index, terrain.contactPosition(index), terrain.contactGrounded(index),
                            info.wheels.get(index / 2).size() * 0.28F, TrackMarks.Kind.TIRE, now, cap);
                }
                if (count > 0) state.cursor = (state.cursor + 4) % count;
            }
        } else if (info instanceof VehicleInfo movable && movable.isEnableMove && !info.wheels.isEmpty()) {
            // Addon vehicles opt in with wheel contact positions. Placed weapons without wheels leave no tracks.
            int count = Math.min(8, info.wheels.size() * 2);
            for (int i = 0; i < Math.min(4, count); i++) {
                int index = (state.cursor + i) % count;
                var wheel = info.wheels.get(index / 2); Vec3 p = wheel.pos();
                Vec3 contact = vehicle.getTransformedPosition(new Vec3(index % 2 == 0 ? p.x : -p.x, p.y, p.z));
                emit(level, state, index, contact, vehicle.onGround(), wheel.size() * 0.28F, TrackMarks.Kind.TIRE, now, cap);
            }
            state.cursor = (state.cursor + 4) % count;
        }
    }

    private static void emit(ClientLevel level, State state, int lane, Vec3 contact, boolean grounded, float width,
            TrackMarks.Kind kind, long now, int cap) {
        TrackPath path = state.lane(lane);
        for (var sample : path.update(contact, grounded, now, kind == TrackMarks.Kind.TREAD ? 0.32 : 0.35)) {
            if (requests >= cap) break;
            Vec3 previous = state.heading[lane];
            double turn = previous == null ? 0 : 1 - Math.abs(previous.dot(sample.direction()));
            float strength = (float) Math.clamp(0.6 + turn * 3, 0.6, 1);
            TrackMarks.add(level, sample.position(), sample.direction(), width, kind, strength);
            state.heading[lane] = sample.direction(); requests++;
        }
    }

    private static final class State {
        final UUID uuid;
        final AircraftInfo info;
        final int[] anchors = {-1, -1};
        final TrackPath[] paths = new TrackPath[8];
        final Vec3[] heading = new Vec3[8];
        long seen = Long.MIN_VALUE;
        int cursor;
        State(UUID uuid, AircraftInfo info) { this.uuid = uuid; this.info = info; }
        void clearPaths() { for (var path : paths) if (path != null) path.clear(); java.util.Arrays.fill(heading, null); }
        TrackPath lane(int index) { if (paths[index] == null) paths[index] = new TrackPath(); return paths[index]; }
    }
}
