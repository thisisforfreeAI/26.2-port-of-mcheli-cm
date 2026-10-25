package io.github.mcheliceport.client.model;

import com.mojang.logging.LogUtils;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.content.aircraft.VehicleInfo;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The parsed models of every vehicle: a body model per vehicle plus the separate part models it
 * needs. Ported from upstream's {@code MCH_ModelManager}, {@code MCH_Models},
 * {@code MCH_ClientProxy#registerModels*} and {@code MCH_OnDemandModels}.
 *
 * <p>With {@link ClientConfig#ON_DEMAND_MODELS} (the default, as upstream), a vehicle's models are
 * parsed on a virtual thread the first time it is drawn, nothing is drawn until they are ready,
 * and models not drawn for {@link ClientConfig#ON_DEMAND_MODEL_LIFETIME_SECONDS} are dropped.
 * Otherwise every helicopter's models are parsed in parallel while resources load.
 */
public final class AircraftModels {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ExecutorService LOADER = Executors.newVirtualThreadPerTaskExecutor();
    /** Upstream {@code SWEEP_INTERVAL_TICKS}. */
    private static final int SWEEP_INTERVAL_TICKS = 200;
    private static final long SWEEP_INTERVAL_MS = SWEEP_INTERVAL_TICKS * 50L;

    /** A vehicle's models. */
    public record VehicleModels(McheliModel body, Map<String, McheliModel> partModels) {

        /**
         * Upstream {@code MCH_RenderAircraft#renderPart}: the separate part model if there is one,
         * else the body's {@code $part} group (merged with the objects after it); empty if neither.
         */
        public List<McheliModel.Group> part(String partName) {
            McheliModel separate = this.partModels.get(partName.toLowerCase(Locale.ROOT));
            if (separate != null) {
                return separate.groups;
            }
            return this.body.part("$" + partName);
        }
    }

    private enum State {
        UNLOADED,
        LOADING,
        LOADED
    }

    private static final class Entry {
        final String dir;
        final AircraftInfo info;
        volatile State state = State.UNLOADED;
        volatile @Nullable VehicleModels models;
        volatile long lastUsedMs;
        int idleSweeps;
        /** Bumped by every reload so a load started before it is thrown away. */
        final int generation;

        Entry(String dir, AircraftInfo info, int generation) {
            this.dir = dir;
            this.info = info;
            this.generation = generation;
        }
    }

    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static volatile int generation;
    private static int tickCounter;

    private AircraftModels() {
    }

    /** Read already resident visual bounds without starting a load or extending residency. */
    public static @Nullable VehicleModels peek(AircraftInfo info) {
        var entry=ENTRIES.get(McheliModel.key(info.getDirectoryName(),info.name));
        return entry!=null&&entry.info==info?entry.models:null;
    }

    /**
     * The models of a vehicle, or null while they load (or if its body model is missing). Marks
     * the vehicle as used, as upstream's {@code notifyRendered}.
     */
    public static @Nullable VehicleModels get(String dir, AircraftInfo info) {
        String key = McheliModel.key(dir, info.name);
        Entry e = ENTRIES.get(key);
        if (e == null || e.info != info) {
            e = new Entry(dir, info, generation);
            ENTRIES.put(key, e);
        }
        e.lastUsedMs = System.nanoTime() / 1_000_000L;
        e.idleSweeps = 0;
        if (e.state == State.UNLOADED) {
            e.state = State.LOADING;
            Entry entry = e;
            ResourceManager resources = Minecraft.getInstance().getResourceManager();
            LOADER.execute(() -> {
                VehicleModels models = null;
                try {
                    models = load(resources, entry.dir, entry.info);
                } catch (RuntimeException ex) {
                    LOGGER.error("Loading the models of {} failed", key, ex);
                }
                if (entry.generation == generation) {
                    entry.models = models;
                    entry.state = State.LOADED;
                }
            });
        }
        return e.models;
    }

    /** Parses a vehicle's body and the part models it needs, as {@code registerModelsHeli}. */
    public static @Nullable VehicleModels load(ResourceManager resources, String dir, AircraftInfo info) {
        McheliModel body = loadModel(resources, dir + "/" + info.name, false);
        if (body == null) {
            return null;
        }
        Map<String, McheliModel> parts = new HashMap<>();
        for (String part : partNames(info)) {
            String lower = part.toLowerCase(Locale.ROOT);
            if (parts.containsKey(lower) || body.containsPart("$" + part)) {
                continue;
            }
            McheliModel model = loadModel(resources, dir + "/" + info.name + "_" + part, true);
            if (model == null && info instanceof VehicleInfo) { model = loadModel(resources, dir + "/" + part, true); }
            if (model != null) {
                parts.put(lower, model);
            }
        }
        if (CommonConfig.SPEC.isLoaded() && CommonConfig.DEBUG_LOG.get()) {
            LOGGER.info("Loaded {} with {} part models", body, parts.size());
        }
        VehicleSurfaceShade.prepare(body, VehicleDetailShade.zones(info));
        for (McheliModel part : parts.values()) VehicleSurfaceShade.prepare(part);
        return new VehicleModels(body, Map.copyOf(parts));
    }

    /**
     * The part names whose models upstream's {@code registerCommonPart} and
     * {@code registerModelsHeli} load. Light hatches are not among them: upstream draws them only
     * from the body's groups.
     */
    public static List<String> partNames(AircraftInfo info) {
        List<String> names = new ArrayList<>();
        if (info instanceof HeliInfo heli) {
            heli.rotorList.forEach(r -> names.add(r.modelName));
        }
        if (info instanceof VehicleInfo vehicle) {
            addVehicleParts(vehicle.partList, names);
        }
        if (info instanceof io.github.mcheliceport.content.aircraft.WingedAircraftInfo plane) {
            plane.nozzles.forEach(p -> names.add(p.modelName));
            plane.rotorList.forEach(r -> {
                names.add(r.modelName);
                r.blades.forEach(b -> names.add(b.modelName));
            });
            plane.wingList.forEach(w -> {
                names.add(w.modelName);
                if (w.pylonList != null) { w.pylonList.forEach(p -> names.add(p.modelName)); }
            });
        }
        info.hatchList.forEach(p -> names.add(p.modelName));
        info.cameraList.forEach(p -> names.add(p.modelName));
        info.partThrottle.forEach(p -> names.add(p.modelName));
        info.partRotPart.forEach(p -> names.add(p.modelName));
        info.partWeapon.forEach(p -> {
            names.add(p.modelName);
            p.child.forEach(c -> names.add(c.modelName));
        });
        info.canopyList.forEach(p -> names.add(p.modelName));
        info.landingGear.forEach(p -> names.add(p.modelName));
        info.partWeaponBay.forEach(p -> names.add(p.modelName));
        info.partCrawlerTrack.forEach(p -> names.add(p.modelName));
        info.partTrackRoller.forEach(p -> names.add(p.modelName));
        info.partWheel.forEach(p -> names.add(p.modelName));
        info.partSteeringWheel.forEach(p -> names.add(p.modelName));
        return names;
    }

    private static void addVehicleParts(List<VehicleInfo.VPart> parts, List<String> names) {
        for (VehicleInfo.VPart part : parts) {
            names.add(part.modelName);
            if (part.child != null) { addVehicleParts(part.child, names); }
        }
    }

    /**
     * Upstream {@code MCH_Models#loadModel}: {@code mcheli:models/<path>.obj}, else {@code .mqo}.
     * Null if neither exists (logged unless {@code quiet}) or the file cannot be parsed (logged).
     */
    public static @Nullable McheliModel loadModel(ResourceManager resources, String path, boolean quiet) {
        for (String ext : new String[] {".obj", ".mqo"}) {
            Identifier id = Identifier.fromNamespaceAndPath("mcheli", "models/" + path.toLowerCase(Locale.ROOT) + ext);
            Optional<Resource> resource = resources.getResource(id);
            if (resource.isEmpty()) {
                continue;
            }
            try (InputStream in = resource.get().open()) {
                McheliModel model=ext.equals(".obj") ? ObjParser.parse(id.toString(), in) : MqoParser.parse(id.toString(), in);
                VehicleImpactSurface.prepare(model);
                return model;
            } catch (IOException | ModelFormatException e) {
                LOGGER.error("load model error '{}'", id, e);
                return null;
            }
        }
        if (!quiet) {
            LOGGER.error("Model mcheli:models/{} not found", path);
        }
        return null;
    }

    /** Upstream {@code MCH_OnDemandModels#onClientTick}: every 200 ticks, drop models unused for too long. */
    public static void tick() {
        if (++tickCounter < SWEEP_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        if (!ClientConfig.get(ClientConfig.ON_DEMAND_MODELS)) {
            return;
        }
        int seconds = ClientConfig.get(ClientConfig.ON_DEMAND_MODEL_LIFETIME_SECONDS);
        if (seconds < 0) {
            return;
        }
        long life = seconds * 1000L;
        int maxIdleSweeps = (int) Math.max(1, Math.ceil((double) life / SWEEP_INTERVAL_MS));
        long now = System.nanoTime() / 1_000_000L;
        for (Entry e : ENTRIES.values()) {
            if (e.state != State.LOADED) {
                continue;
            }
            if (now - e.lastUsedMs < SWEEP_INTERVAL_MS) {
                e.idleSweeps = 0;
            } else if (++e.idleSweeps >= maxIdleSweeps) {
                e.state = State.UNLOADED;
                e.models = null;
                e.idleSweeps = 0;
                LOGGER.debug("[OnDemandModels] evicted {}/{}", e.dir, e.info.name);
            }
        }
    }

    /** Drops every model; the next frame loads them again. */
    public static void clear() {
        generation++;
        ENTRIES.clear();
    }

    /** Number of vehicles whose models are loaded, for tests and the debug screen. */
    public static int loadedCount() {
        return (int) ENTRIES.values().stream().filter(e -> e.models != null).count();
    }

    /**
     * The resource reload listener: drops every model and, when models are not loaded on demand,
     * parses every helicopter's models in parallel while resources load.
     */
    public static final class ReloadListener extends SimplePreparableReloadListener<Map<String, Entry>> {

        @Override
        protected Map<String, Entry> prepare(ResourceManager manager, ProfilerFiller profiler) {
            VehicleImpactSurface.clear();
            if (ClientConfig.get(ClientConfig.ON_DEMAND_MODELS)) {
                return Map.of();
            }
            int gen = generation + 1;
            Map<String, Entry> loaded = new ConcurrentHashMap<>();
            long start = System.nanoTime();
            ContentRegistries.heli().values().parallelStream().forEach(info -> {
                Entry e = new Entry("helicopters", info, gen);
                e.models = load(manager, "helicopters", info);
                e.state = State.LOADED;
                loaded.put(McheliModel.key("helicopters", info.name), e);
            });
            ContentRegistries.get().plane().values().parallelStream().forEach(info -> {
                Entry e = new Entry("planes", info, gen);
                e.models = load(manager, "planes", info);
                e.state = State.LOADED;
                loaded.put(McheliModel.key("planes", info.name), e);
            });
            ContentRegistries.get().ship().values().parallelStream().forEach(info -> {
                Entry e = new Entry("ships", info, gen);
                e.models = load(manager, "ships", info);
                e.state = State.LOADED;
                loaded.put(McheliModel.key("ships", info.name), e);
            });
            for (var source : List.of(ContentRegistries.get().tank(), ContentRegistries.get().vehicle())) {
                source.values().parallelStream().forEach(info -> {
                    String dir = info.getDirectoryName();
                    Entry e = new Entry(dir, info, gen);
                    e.models = load(manager, dir, info); e.state = State.LOADED;
                    loaded.put(McheliModel.key(dir, info.name), e);
                });
            }
            LOGGER.info("Loaded the models of {} vehicles in {} ms", loaded.size(),
                    (System.nanoTime() - start) / 1_000_000L);
            return loaded;
        }

        @Override
        protected void apply(Map<String, Entry> loaded, ResourceManager manager, ProfilerFiller profiler) {
            clear();
            ENTRIES.putAll(loaded);
        }
    }
}
