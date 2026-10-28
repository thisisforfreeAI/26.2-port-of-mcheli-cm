package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.util.McheliMath;
import io.github.mcheliceport.util.McheliSounds;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Client presentation only. States and startup sound voices have hard limits and world ownership. */
@EventBusSubscriber(modid = "mcheli_ce_port", value = Dist.CLIENT)
public final class VehicleAtmosphere {
    private static final LinkedHashMap<AircraftEntity, State> STATES = new LinkedHashMap<>();
    private static ClientLevel world;
    private static long lastTick = Long.MIN_VALUE;
    private static int emitted;
    private static long admissionTick=Long.MIN_VALUE;
    private static java.lang.ref.WeakReference<ClientLevel> admissionWorld=new java.lang.ref.WeakReference<>(null);
    private static final class State {
        final VehicleAtmosphereLook engine = new VehicleAtmosphereLook();
        final WreckCoolingLook cooling = new WreckCoolingLook();
        Whine whine;
        void stop() { if (whine != null) { whine.finish(); whine = null; } }
    }
    private VehicleAtmosphere() {}

    public static float engineHeat(AircraftEntity aircraft) {
        if(aircraft instanceof PlaneEntity p && io.github.mcheliceport.client.render.PlaneAirframeParts.tailMissing(p))return 0;
        State state = STATES.get(aircraft);
        return EffectQuality.enhanced() && ClientConfig.get(ClientConfig.ENGINE_STARTUP_EFFECTS) && state != null
                ? state.engine.heat() : EngineExhaustLook.strength(aircraft.getCurrentThrottle());
    }

    public static void clear() {
        STATES.values().forEach(State::stop); STATES.clear(); world = null;
        lastTick = Long.MIN_VALUE; CookoffEffects.clear(); WreckSequenceEffects.clear();
    }
    public static int stateCount() { return STATES.size(); }
    public static int emitted() { return world!=null&&admissionWorld.get()==world&&admissionTick==world.getGameTime()?emitted:0; }
    public static int wreckAge(AircraftEntity ac) { var state=STATES.get(ac);return state==null?-1:state.cooling.age(); }
    public static long startupVoiceCount() {
        return STATES.values().stream().filter(s -> s.whine != null
                && Minecraft.getInstance().getSoundManager().isActive(s.whine)).count();
    }
    public static boolean handlesDamage(AircraftEntity aircraft) {
        return EffectQuality.enhanced() && ClientConfig.get(ClientConfig.VEHICLE_DAMAGE_DETAILS)
                && STATES.containsKey(aircraft);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != world || !EffectQuality.enhanced()) { clear(); world = level; }
        if (level == null || !EffectQuality.enhanced() || mc.player == null || mc.isPaused()) return;
        long now = level.getGameTime();
        if (now == lastTick) return;
        lastTick = now; prepareAdmission(level);
        STATES.entrySet().removeIf(entry -> {
            AircraftEntity aircraft = entry.getKey();
            if (aircraft.isRemoved() || aircraft.level() != level
                    || mc.gameRenderer.mainCamera().position().distanceToSqr(aircraft.position()) > 80 * 80) {
                entry.getValue().stop(); return true;
            }
            return false;
        });
        for (var entity : level.entitiesForRendering()) {
            if (!(entity instanceof AircraftEntity aircraft) || aircraft.getAcInfo() == null || aircraft.isRemoved()
                    || mc.gameRenderer.mainCamera().position().distanceToSqr(aircraft.position()) > 80 * 80) continue;
            State state = STATES.get(aircraft);
            if (state == null) {
                if (STATES.size() >= 128) continue;
                state = new State(); STATES.put(aircraft, state);
            }
            state.cooling.tick(aircraft.isDestroyed());
            WreckSequenceEffects.tick(level,aircraft,state.cooling.age());
            boolean powered = !aircraft.isDestroyed() && aircraft.canUseFuel()
                    && !(aircraft instanceof PlaneEntity p && io.github.mcheliceport.client.render.PlaneAirframeParts.tailMissing(p));
            state.engine.tick(aircraft.getCurrentThrottle(), powered);
            if (!ClientConfig.get(ClientConfig.ENGINE_STARTUP_EFFECTS)) state.stop();
            else {
                if (state.engine.justStarted() && (aircraft instanceof HeliEntity || aircraft instanceof PlaneEntity)
                        && STATES.values().stream().filter(s -> s.whine != null).count() < 8) {
                    state.whine = new Whine(aircraft, state.engine);
                    mc.getSoundManager().play(state.whine);
                }
                if (state.engine.startup() == 0) state.stop();
            }
            if (emitted >= EngineExhaustLook.cap(ClientConfig.get(ClientConfig.EFFECT_BUDGET))) continue;
            List<Vec3> origins = origins(aircraft);
            if (ClientConfig.get(ClientConfig.ENGINE_STARTUP_EFFECTS) && powered && state.engine.heat() > 0
                    && Math.floorMod(now + aircraft.getId(), 2) == 0) {
                var outlets = EngineExhaustLook.outlets(aircraft.getAcInfo(), aircraft instanceof PlaneEntity p ? p.getNozzleRotation() : 0);
                for (int i = 0; i < origins.size(); i++) {
                    Vec3 direction = outlets.isEmpty() ? new Vec3(0, 0.1, -1) : outlets.get(i).direction();
                    direction = McheliMath.rotVec3(direction, -aircraft.getYaw(), -aircraft.getPitch(), -aircraft.getRoll());
                    BlockPos block = BlockPos.containing(origins.get(i));
                    if (!level.hasChunkAt(block) || !level.getFluidState(block).isEmpty()) continue;
                    io.github.mcheliceport.client.render.EngineHeat.add(level, ((long) aircraft.getId() << 3) ^ i,
                            origins.get(i).add(direction.scale(0.3)), direction, aircraft.getDeltaMovement(),
                            outlets.isEmpty() ? 0.7F : outlets.get(i).size(), state.engine.heat(),
                            !outlets.isEmpty() && outlets.get(i).jet());
                }
            }
            if (ClientConfig.get(ClientConfig.ENGINE_STARTUP_EFFECTS) && powered && state.engine.startup() > 0
                    && Math.floorMod(now + aircraft.getId(), 6 * (1 + EffectPerformance.pressure())) == 0) {
                Vec3 motion = aircraft.getDeltaMovement().scale(0.7);
                for (Vec3 pos : origins) {
                    plume(level, pos, motion.add(0, 0.025, 0), 0.25F + state.engine.startup() * 0.3F,
                            state.engine.startup() * 0.3F, 22, 0.76F, 0.79F, 0.82F);
                }
            }
            if (ClientConfig.get(ClientConfig.VEHICLE_DAMAGE_DETAILS)) damage(level, aircraft, origins, now, state.cooling);
            if (ClientConfig.get(ClientConfig.VEHICLE_DAMAGE_DETAILS)&&aircraft instanceof PlaneEntity plane)
                FuelSprayEffects.emit(level,plane,origins,now);
        }
    }

    /** Prefer authored engine anchors. A short startup puff may use the machinery below a lift rotor. */
    public static List<Vec3> origins(AircraftEntity aircraft) {
        if(aircraft instanceof PlaneEntity p && io.github.mcheliceport.client.render.PlaneAirframeParts.tailMissing(p)) {
            var sequence=WreckSequenceEffects.get(aircraft);
            if(sequence!=null&&sequence.opening!=null)return List.of(sequence.opening);
        }
        var info = aircraft.getAcInfo();
        List<Vec3> result = new ArrayList<>(2);
        var outlets = EngineExhaustLook.outlets(info, aircraft instanceof PlaneEntity p ? p.getNozzleRotation() : 0);
        for (var outlet : outlets) {
            result.add(aircraft.position().add(McheliMath.rotVec3(outlet.position().add(outlet.direction().scale(0.45)),
                    -aircraft.getYaw(), -aircraft.getPitch(), -aircraft.getRoll())).add(0,
                    io.github.mcheliceport.content.aircraft.AircraftInfo.GLOBAL_Y_OFFSET, 0));
            if (result.size() == 2) return result;
        }
        if (result.isEmpty() && aircraft instanceof HeliEntity heli && heli.getHeliInfo() != null) {
            for (var rotor : heli.getHeliInfo().rotorList) {
                if (rotor.rot.y * rotor.rot.y < rotor.rot.lengthSqr() * 0.5) continue;
                Vec3 pos = aircraft.position().add(McheliMath.rotVec3(rotor.pos.add(0, -0.9, -0.5),
                        -aircraft.getYaw(), -aircraft.getPitch(), -aircraft.getRoll())).add(0,
                        io.github.mcheliceport.content.aircraft.AircraftInfo.GLOBAL_Y_OFFSET, 0);
                if (result.stream().noneMatch(p -> p.distanceToSqr(pos) < 2)) result.add(pos);
                if (result.size() == 2) break;
            }
        }
        if (result.isEmpty()) result.add(aircraft.position().add(McheliMath.rotVec3(0,
                Math.min(1.2, aircraft.getBbHeight() * 0.6), -1, -aircraft.getYaw(), -aircraft.getPitch(), -aircraft.getRoll())));
        return result;
    }

    /** Rear-deck fallback for tanks; aircraft retain their authored engine/nozzle anchors. */
    public static List<Vec3> wreckOrigins(AircraftEntity aircraft) {
        if(aircraft instanceof io.github.mcheliceport.entity.tank.TankEntity&&aircraft.getAcInfo().engineOutlets.isEmpty()){
            var model=io.github.mcheliceport.client.model.AircraftModels.peek(aircraft.getAcInfo());
            double top=model==null?1.8:Math.clamp(model.body().maxY*.4,1.2,2.1);
            double rear=model==null?-1.7:Math.clamp(model.body().minZ*.82,-5,-.7);
            return List.of(aircraft.position().add(McheliMath.rotVec3(.85,top+.35,rear,
                    -aircraft.getYaw(),-aircraft.getPitch(),-aircraft.getRoll())));
        }
        return origins(aircraft);
    }

    private static void damage(ClientLevel level, AircraftEntity aircraft, List<Vec3> origins, long now, WreckCoolingLook cooling) {
        if (aircraft.isDestroyed()) { WreckEffects.emit(level,wreckOrigins(aircraft),cooling,aircraft,now); return; }
        float severity = VehicleAtmosphereLook.damage(aircraft.getHP(), aircraft.getMaxHP());
        if (severity == 0) return;
        if (aircraft.isSearchLightOn() && severity > .4F && EffectPerformance.optionalGlows()
                && Math.floorMod(now + aircraft.getId(), 13) == 0) {
            var lights = aircraft.getAcInfo().searchLights;
            if (!lights.isEmpty()) {
                int lamp = Math.floorMod((int) (now / 13 + aircraft.getId()), lights.size());
                var light = lights.get(lamp);
                if (io.github.mcheliceport.entity.aircraft.SearchLightBeam.usable(light)
                        && io.github.mcheliceport.client.render.VehicleLightDamage.strength(aircraft, lamp) < .4F) {
                    Vec3 pos = aircraft.position().add(McheliMath.rotVec3(light.pos, -aircraft.getYaw(),
                            -aircraft.getPitch(), -aircraft.getRoll())).add(0,
                            io.github.mcheliceport.content.aircraft.AircraftInfo.GLOBAL_Y_OFFSET, 0);
                    if (admit(level, pos, .05F)) {
                        var spark = new SparkParticle(level, pos.x, pos.y, pos.z, .03, -.01, .02, 5, .045F);
                        spark.setColor(.65F, .82F, 1); Minecraft.getInstance().particleEngine.add(spark);
                    }
                }
            }
        }
        Vec3 panel=io.github.mcheliceport.client.render.VehicleImpactMarks.damageOrigin(aircraft);
        if (panel!=null&&Math.floorMod(now+aircraft.getId(),9*(1+EffectPerformance.pressure()))==0&&admit(level,panel,.55F)) {
            var smoke=new SmokeParticle(level,panel.x,panel.y,panel.z,0,.018,0);
            smoke.enhanced=true;smoke.coherentDrift=true;smoke.disableCollision();
            smoke.setColor(.35F,.34F,.32F);smoke.setAlphaF(.36F);smoke.setParticleMaxAge(25);
            smoke.motionYUpAge=.6F;smoke.diffusible=true;smoke.particleScale=1.5F;smoke.particleMaxScale=5.5F;
            Minecraft.getInstance().particleEngine.add(smoke);
        }
        int engineIndex=Math.floorMod((int) (now / 11 + aircraft.getId()), origins.size());
        Vec3 position = origins.get(engineIndex);
        AirflowFireEffects.damage(level,aircraft,position,engineIndex,severity,now);
        Vec3 shed=io.github.mcheliceport.damage.AirframeDamage.fragmentMotion(aircraft.getDeltaMovement(),
                aircraft.position().subtract(aircraft.xo,aircraft.yo,aircraft.zo));
        double distance=Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(aircraft.position());
        int period=DamageTrailLook.period(shed.length(),distance,EffectPerformance.pressure(),
                ClientConfig.get(ClientConfig.EFFECT_BUDGET)==ClientConfig.EffectBudget.LOW);
        if(DamageTrailLook.emit(now,aircraft.getId(),severity,period)) {
            boolean surge=VehicleAtmosphereLook.smokePulse(now,aircraft.getId(),severity);
            boolean wisp=severity<.4F&&!surge||DamageTrailLook.wisp(now,aircraft.getId());
            float variation=.94F+.06F*(1+(float)Math.sin(now*.37+aircraft.getId()*.7));
            float radius=DamageTrailLook.radius(severity,shed.length())*variation;
            if(admit(level,position,radius*1.1F))Minecraft.getInstance().particleEngine.add(
                    new DamageSmokeParticle(level,position,shed,severity,radius,wisp,surge?1:.72F,aircraft.getId()));
        }
        FluidLeaks.emit(level,aircraft,position,severity,now);
        if (severity > 0.4F && EffectPerformance.optionalGlows()
                && Math.floorMod(now + (long) aircraft.getId() * 3, 23) < 2 && admit(level, position, 0.07F)) {
            SparkParticle spark = new SparkParticle(level, position.x, position.y, position.z,
                    Math.sin(now * 1.7) * 0.15, 0.06, Math.cos(now * 1.7) * 0.15, 6, 0.065F);
            spark.setColor(0.65F, 0.82F, 1);
            Minecraft.getInstance().particleEngine.add(spark);
        }
    }

    private static void prepareAdmission(ClientLevel level){
        if(admissionWorld.get()!=level||admissionTick!=level.getGameTime()){
            admissionWorld=new java.lang.ref.WeakReference<>(level);admissionTick=level.getGameTime();emitted=0;
        }
    }
    static boolean admit(ClientLevel level, Vec3 position, float radius) {
        prepareAdmission(level);
        if (emitted >= EngineExhaustLook.cap(ClientConfig.get(ClientConfig.EFFECT_BUDGET))) return false;
        BlockPos block = BlockPos.containing(position);
        if (!level.hasChunkAt(block) || !level.getFluidState(block).isEmpty()
                || !EffectAdmission.admit(level, position.x, position.y, position.z, false, radius)) return false;
        emitted++; return true;
    }

    private static void plume(ClientLevel level, Vec3 position, Vec3 motion, float radius, float opacity,
            int life, float r, float g, float b) {
        if (!admit(level, position, radius)) return;
        EnginePlumeParticle particle = new EnginePlumeParticle(level, position.x, position.y, position.z,
                motion.x, motion.y, motion.z, radius, opacity, life, (float) (level.getGameTime() % 6));
        particle.setColor(r, g, b);
        Minecraft.getInstance().particleEngine.add(particle);
    }

    private static final class Whine extends AbstractTickableSoundInstance {
        private final AircraftEntity aircraft;
        private final VehicleAtmosphereLook engine;
        Whine(AircraftEntity aircraft, VehicleAtmosphereLook engine) {
            super(McheliSounds.event("turbine_spool").value(), SoundSource.MASTER, RandomSource.create());
            this.aircraft = aircraft; this.engine = engine; looping = true;
            tick();
        }
        @Override public void tick() {
            if (aircraft.isRemoved() || aircraft.level() != Minecraft.getInstance().level || aircraft.isDestroyed()
                    || !EffectQuality.enhanced() || !ClientConfig.get(ClientConfig.ENGINE_STARTUP_EFFECTS) || engine.startup() == 0) {
                finish(); return;
            }
            x = aircraft.getX(); y = aircraft.getY(); z = aircraft.getZ();
            volume = engine.whineVolume(); pitch = engine.whinePitch();
        }
        void finish() { stop(); Minecraft.getInstance().getSoundManager().stop(this); }
    }
}
