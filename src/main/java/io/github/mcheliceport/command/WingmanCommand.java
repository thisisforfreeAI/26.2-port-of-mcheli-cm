package io.github.mcheliceport.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.net.DispatchOrderPayload;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.registry.ModItems;
import io.github.mcheliceport.wingman.MissionDispatch;
import io.github.mcheliceport.wingman.MissionPlanner;
import io.github.mcheliceport.wingman.WingmanPanel;
import io.github.mcheliceport.wingman.block.MarkerType;
import io.github.mcheliceport.wingman.block.WingmanMarkerBlock;
import io.github.mcheliceport.wingman.block.WingmanMarkerBlockEntity;
import io.github.mcheliceport.wingman.config.WingmanConfig;
import io.github.mcheliceport.wingman.handler.AutonomousFlightHandler;
import io.github.mcheliceport.wingman.mission.AutonomousState;
import io.github.mcheliceport.wingman.mission.MissionNode;
import io.github.mcheliceport.wingman.mission.MissionOrder;
import io.github.mcheliceport.wingman.mission.MissionPlan;
import io.github.mcheliceport.wingman.mission.MissionType;
import io.github.mcheliceport.wingman.registry.MarkerRegistry;
import io.github.mcheliceport.wingman.wingman.WingmanEntry;
import io.github.mcheliceport.wingman.wingman.WingmanRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jspecify.annotations.Nullable;

/**
 * Upstream's {@code /wingman} command, for players: formation and attack orders for the wingmen
 * of the aircraft the player pilots, the formation values, wingman markers, the mission planner's
 * routes and missions, base orders, and {@code /wingman gui} for the planner screen.
 *
 * <p>As upstream any player can use it, except {@code spawnuav}, which spawns aircraft and needs
 * operator permission (level 2) in the port. Upstream ordered any aircraft UUID anywhere; the port
 * applies the panel's, the planner's and the base screen's rules: {@code follow} takes an aircraft
 * within 512 blocks that nobody flies, {@code mission assign} one in the player's dimension that no
 * player flies, {@code order dispatch} one within 512 blocks of the base, and the marker
 * subcommands edit the marker the player looks at within reach, as the marker screen does
 * (KNOWN_ISSUES 100).
 */
@EventBusSubscriber(modid = McheliCePort.MOD_ID)
public final class WingmanCommand {

    /** Upstream's weapon suggestions: McHeli weapon types and {@code clear}. */
    static final List<String> WEAPON_TYPES = List.of("gun", "cannon", "missile", "rocket", "bomb", "torpedo",
            "machinegun1", "machinegun2", "cas", "asmissile", "aamissile", "atmissile", "tvmissile", "mkrocket",
            "targetingpod", "clear");
    private static final int MAX_SUGGESTED_AIRCRAFT = 16;

    private static final SuggestionProvider<CommandSourceStack> AIRCRAFT = (context, builder) -> {
        Entity self = context.getSource().getEntity();
        if (self != null) {
            List<AircraftEntity> list = self.level().getEntitiesOfClass(AircraftEntity.class,
                    self.getBoundingBox().inflate(WingmanPanel.NEARBY_RANGE), ac -> !ac.isRemoved());
            list.sort(java.util.Comparator.comparingDouble(self::distanceToSqr));
            for (AircraftEntity ac : list.subList(0, Math.min(MAX_SUGGESTED_AIRCRAFT, list.size()))) {
                builder.suggest(ac.getStringUUID(), Component.literal(WingmanPanel.aircraftName(ac)));
            }
        }
        return builder.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> ROUTES = (context, builder) ->
            SharedSuggestionProvider.suggest(MissionPlan.names(context.getSource().getLevel()), builder);
    private static final SuggestionProvider<CommandSourceStack> BASES = (context, builder) ->
            SharedSuggestionProvider.suggest(markerIds(context.getSource().getLevel(), MarkerType.BASE), builder);
    private static final SuggestionProvider<CommandSourceStack> PARKINGS = (context, builder) -> {
        List<String> ids = markerIds(context.getSource().getLevel(), MarkerType.PARKING);
        ids.addAll(markerIds(context.getSource().getLevel(), MarkerType.HELIPAD));
        return SharedSuggestionProvider.suggest(ids, builder);
    };
    private static final SuggestionProvider<CommandSourceStack> MARKER_TYPES = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(MarkerType.values()).map(MarkerType::getSerializedName),
                    builder);
    private static final SuggestionProvider<CommandSourceStack> MISSION_TYPES = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(MissionType.values()).map(Enum::name), builder);
    private static final SuggestionProvider<CommandSourceStack> WEAPONS = (context, builder) ->
            SharedSuggestionProvider.suggest(WEAPON_TYPES, builder);
    private static final SuggestionProvider<CommandSourceStack> UAVS = (context, builder) ->
            SharedSuggestionProvider.suggest(uavNames(), builder);

    private WingmanCommand() {
    }

    @SubscribeEvent
    static void registerCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wingman")
                .executes(c -> usage(c, "/wingman <follow|stop|status|dist|maxwings|engage|auto|hold|weapon|minalt"
                        + "|maxalt|alt|spawnuav|marker|route|mission|order|gui> [args]"))
                .then(Commands.literal("follow")
                        .executes(c -> follow(c, null))
                        .then(Commands.argument("aircraft", StringArgumentType.word()).suggests(AIRCRAFT)
                                .executes(c -> follow(c, StringArgumentType.getString(c, "aircraft")))))
                .then(Commands.literal("stop").executes(WingmanCommand::stop))
                .then(Commands.literal("status").executes(WingmanCommand::status))
                .then(formationCommands())
                .then(Commands.literal("maxwings")
                        .executes(c -> info(c, "§7Current max: " + WingmanConfig.maxWingmen))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                .executes(WingmanCommand::maxWings)))
                .then(Commands.literal("engage")
                        .executes(c -> usage(c, "/wingman engage <target uuid>"))
                        .then(Commands.argument("target", StringArgumentType.word())
                                .executes(WingmanCommand::engage)))
                .then(Commands.literal("auto").executes(c -> attack(c, WingmanEntry.ATK_AUTO)))
                .then(Commands.literal("hold").executes(c -> attack(c, WingmanEntry.ATK_NONE)))
                .then(Commands.literal("weapon")
                        .executes(c -> weapon(c, null))
                        .then(Commands.argument("type", StringArgumentType.word()).suggests(WEAPONS)
                                .executes(c -> weapon(c, StringArgumentType.getString(c, "type")))))
                .then(minAlt())
                .then(maxAlt())
                .then(altClear())
                .then(Commands.literal("spawnuav")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(WingmanCommand::listUavs)
                        .then(Commands.argument("type", StringArgumentType.word()).suggests(UAVS)
                                .executes(c -> spawnUav(c, StringArgumentType.getString(c, "type")))))
                .then(markerCommands())
                .then(routeCommands())
                .then(missionCommands())
                .then(orderCommands())
                .then(Commands.literal("gui").executes(WingmanCommand::gui)));
    }

    // ---- follow, stop, status ----

    private static int follow(CommandContext<CommandSourceStack> c, @Nullable String uuid) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        AircraftEntity leader = WingmanPanel.leaderOf(player);
        if (leader == null) {
            return fail(c, "You must be piloting a McHeli aircraft.");
        }
        if (WingmanRegistry.countForLeader(leader) >= WingmanConfig.maxWingmen) {
            return fail(c, "Maximum " + WingmanConfig.maxWingmen + " wingmen per aircraft.");
        }
        AircraftEntity wingman;
        if (uuid != null) {
            UUID uid = parseUuid(c, uuid);
            if (uid == null) {
                return 0;
            }
            if (!(player.level().getEntity(uid) instanceof AircraftEntity ac)) {
                return fail(c, "No McHeli aircraft found: " + uuid);
            }
            if (ac == leader) {
                return fail(c, "Cannot follow your own aircraft.");
            }
            if (!WingmanPanel.canFollow(leader, ac)) {
                return fail(c, "That aircraft is flown, already has orders or is over "
                        + (int) WingmanPanel.NEARBY_RANGE + " blocks away.");
            }
            wingman = ac;
        } else {
            List<AircraftEntity> candidates = WingmanPanel.candidates(leader);
            if (candidates.isEmpty()) {
                return fail(c, "No unassigned aircraft found nearby.");
            }
            wingman = candidates.getFirst();
        }
        int slot = WingmanPanel.freeSlot(leader);
        WingmanRegistry.put(wingman.getUUID(), new WingmanEntry(leader, slot));
        return ok(c, "§aWingman assigned (slot " + slot + "): " + shortId(wingman.getUUID()));
    }

    private static int stop(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        AircraftEntity leader = WingmanPanel.leaderOf(c.getSource().getPlayerOrException());
        if (leader == null) {
            return fail(c, "You must be piloting a McHeli aircraft.");
        }
        int before = WingmanRegistry.countForLeader(leader);
        WingmanRegistry.removeForLeader(leader);
        return ok(c, "§aStopped " + before + " wingman(s).");
    }

    private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        c.getSource().getPlayerOrException();
        Map<UUID, WingmanEntry> all = WingmanRegistry.snapshot();
        if (all.isEmpty()) {
            return info(c, "§7No active wingmen.");
        }
        info(c, "§e=== Wingman Status (side=" + WingmanConfig.formationSideDist + " alt="
                + WingmanConfig.formationAltOffset + " rear=" + WingmanConfig.formationRearDist + ") ===");
        for (Map.Entry<UUID, WingmanEntry> e : all.entrySet()) {
            WingmanEntry entry = e.getValue();
            String leader = entry.leader != null ? WingmanPanel.aircraftName(entry.leader) : "none";
            String atk = entry.attackMode != WingmanEntry.ATK_NONE
                    ? " atk=" + entry.attackMode
                            + (entry.manualTargetId != null ? ":" + entry.manualTargetId.toString().substring(0, 8) : "")
                    : "";
            info(c, "§7[" + shortId(e.getKey()) + "] §fslot=" + entry.formationSlot + " state=" + entry.state
                    + " leader=" + leader + atk);
        }
        return all.size();
    }

    // ---- the formation values ----

    private static LiteralArgumentBuilder<CommandSourceStack> formationCommands() {
        return Commands.literal("dist")
                .executes(c -> usage(c, "/wingman dist <side> <altitude> <rear>  (current: side="
                        + WingmanConfig.formationSideDist + " alt=" + WingmanConfig.formationAltOffset + " rear="
                        + WingmanConfig.formationRearDist + ")"))
                .then(Commands.argument("side", DoubleArgumentType.doubleArg(0, 500))
                        .then(Commands.argument("altitude", DoubleArgumentType.doubleArg(-500, 500))
                                .then(Commands.argument("rear", DoubleArgumentType.doubleArg(0, 500))
                                        .executes(WingmanCommand::dist))));
    }

    private static int dist(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        c.getSource().getPlayerOrException();
        double side = DoubleArgumentType.getDouble(c, "side");
        double alt = DoubleArgumentType.getDouble(c, "altitude");
        double rear = DoubleArgumentType.getDouble(c, "rear");
        WingmanConfig.formationSideDist = side;
        WingmanConfig.formationAltOffset = alt;
        WingmanConfig.formationRearDist = rear;
        return ok(c, "§aFormation: side=" + side + " alt=" + alt + " rear=" + rear);
    }

    private static int maxWings(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        c.getSource().getPlayerOrException();
        int n = IntegerArgumentType.getInteger(c, "count");
        WingmanConfig.maxWingmen = n;
        return ok(c, "§aMax wingmen set to " + n);
    }

    static LiteralArgumentBuilder<CommandSourceStack> minAlt() {
        return Commands.literal("minalt")
                .executes(c -> info(c, "§7Current min attack altitude: §e" + WingmanConfig.minAttackAltitude
                        + "§7  (0 = no floor). Usage: /wingman minalt <y>"))
                .then(Commands.argument("y", DoubleArgumentType.doubleArg(0, 10000)).executes(c -> {
                    c.getSource().getPlayerOrException();
                    double v = DoubleArgumentType.getDouble(c, "y");
                    WingmanConfig.minAttackAltitude = v;
                    return ok(c, v == 0 ? "§aMin attack altitude cleared (no floor)."
                            : "§aMin attack altitude set to §eY=" + v + "§a.");
                }));
    }

    static LiteralArgumentBuilder<CommandSourceStack> maxAlt() {
        return Commands.literal("maxalt")
                .executes(c -> info(c, "§7Current max attack altitude: §e" + WingmanConfig.maxAttackAltitude
                        + "§7  (0 = no ceiling). Usage: /wingman maxalt <y>"))
                .then(Commands.argument("y", DoubleArgumentType.doubleArg(0, 10000)).executes(c -> {
                    c.getSource().getPlayerOrException();
                    double v = DoubleArgumentType.getDouble(c, "y");
                    WingmanConfig.maxAttackAltitude = v;
                    return ok(c, v == 0 ? "§aMax attack altitude cleared (no ceiling)."
                            : "§aMax attack altitude set to §eY=" + v + "§a.");
                }));
    }

    static LiteralArgumentBuilder<CommandSourceStack> altClear() {
        return Commands.literal("alt")
                .executes(c -> info(c, "§7Current: min=§e" + WingmanConfig.minAttackAltitude + "§7  max=§e"
                        + WingmanConfig.maxAttackAltitude + "§7. Usage: /wingman alt clear"))
                .then(Commands.literal("clear").executes(c -> {
                    c.getSource().getPlayerOrException();
                    WingmanConfig.minAttackAltitude = 0.0;
                    WingmanConfig.maxAttackAltitude = 0.0;
                    return ok(c, "§aAttack altitude limits cleared (min=0, max=0).");
                }));
    }

    // ---- attack orders for the player's wingmen ----

    private static @Nullable List<Map.Entry<UUID, WingmanEntry>> wingmen(CommandContext<CommandSourceStack> c)
            throws CommandSyntaxException {
        AircraftEntity leader = WingmanPanel.leaderOf(c.getSource().getPlayerOrException());
        List<Map.Entry<UUID, WingmanEntry>> list = leader != null ? WingmanRegistry.snapshotForLeader(leader) : List.of();
        if (list.isEmpty()) {
            fail(c, "No wingmen assigned to your aircraft.");
            return null;
        }
        return list;
    }

    /**
     * Upstream {@code engage}. Without a UUID upstream set a manual attack on no target, which left
     * the wingmen out of formation attacking nothing; the port asks for the target.
     */
    private static int engage(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        var list = wingmen(c);
        if (list == null) {
            return 0;
        }
        UUID target = parseUuid(c, StringArgumentType.getString(c, "target"));
        if (target == null) {
            return 0;
        }
        for (Map.Entry<UUID, WingmanEntry> e : list) {
            e.getValue().attackMode = WingmanEntry.ATK_MANUAL;
            e.getValue().manualTargetId = target;
        }
        return ok(c, "§aEngaging: " + list.size() + " wingman(s) → target=" + shortId(target));
    }

    private static int attack(CommandContext<CommandSourceStack> c, int mode) throws CommandSyntaxException {
        var list = wingmen(c);
        if (list == null) {
            return 0;
        }
        for (Map.Entry<UUID, WingmanEntry> e : list) {
            e.getValue().attackMode = mode;
            e.getValue().manualTargetId = null;
        }
        return ok(c, mode == WingmanEntry.ATK_AUTO
                ? "§aAuto-attack enabled for " + list.size() + " wingman(s)."
                : "§aHold — attack stopped for " + list.size() + " wingman(s).");
    }

    private static int weapon(CommandContext<CommandSourceStack> c, @Nullable String arg) throws CommandSyntaxException {
        var list = wingmen(c);
        if (list == null) {
            return 0;
        }
        if (arg == null) {
            String cur = list.getFirst().getValue().weaponType;
            return info(c, "§7Current weapon type: " + (cur != null ? "§e" + cur : "§7(any — first available)")
                    + "§7. Usage: /wingman weapon <type>  or  /wingman weapon clear");
        }
        String type = arg.equalsIgnoreCase("clear") ? null : arg.toLowerCase(Locale.ROOT);
        for (Map.Entry<UUID, WingmanEntry> e : list) {
            e.getValue().weaponType = type;
        }
        return ok(c, type == null ? "§aWeapon type cleared (any weapon)."
                : "§aWeapon type set to §e" + type + "§a for " + list.size() + " wingman(s).");
    }

    // ---- spawnuav ----

    /** The UAV types, planes first, as upstream lists them. */
    static List<String> uavNames() {
        List<String> names = new ArrayList<>();
        for (AircraftInfo info : ContentRegistries.plane().values()) {
            if (info.isUAV) {
                names.add(info.name);
            }
        }
        for (AircraftInfo info : ContentRegistries.heli().values()) {
            if (info.isUAV) {
                names.add(info.name);
            }
        }
        return names;
    }

    private static int listUavs(CommandContext<CommandSourceStack> c) {
        List<String> planes = new ArrayList<>();
        for (AircraftInfo info : ContentRegistries.plane().values()) {
            if (info.isUAV) {
                planes.add(info.name);
            }
        }
        List<String> helis = new ArrayList<>();
        for (AircraftInfo info : ContentRegistries.heli().values()) {
            if (info.isUAV) {
                helis.add(info.name);
            }
        }
        if (planes.isEmpty() && helis.isEmpty()) {
            return info(c, "§7No UAV types found.");
        }
        info(c, "§eAvailable UAV types (/wingman spawnuav <type>):");
        planes.forEach(n -> info(c, "§7  [plane] " + n));
        helis.forEach(n -> info(c, "§7  [heli]  " + n));
        return planes.size() + helis.size();
    }

    /** Upstream {@code spawnUav}: the plane or helicopter of that type 8 blocks in front of the player. */
    private static int spawnUav(CommandContext<CommandSourceStack> c, String typeName) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        double yawRad = Math.toRadians(player.getYRot());
        double x = player.getX() - Math.sin(yawRad) * 8;
        double y = player.getY() + 1;
        double z = player.getZ() + Math.cos(yawRad) * 8;
        for (ModItems.ContentItem item : ModItems.contentItems()) {
            if ((item.type() == ContentType.PLANE || item.type() == ContentType.HELICOPTER)
                    && item.contentName().equalsIgnoreCase(typeName) && item.item().get() instanceof AircraftItem ai) {
                AircraftEntity aircraft = ai.createAircraft(player.level(), x, y, z);
                if (aircraft == null) {
                    break;
                }
                aircraft.initRotationYaw(player.getYRot());
                player.level().addFreshEntity(aircraft);
                return ok(c, "§aSpawned: " + item.type().typeName + " (" + shortId(aircraft.getUUID()) + ")");
            }
        }
        return fail(c, "Could not spawn \"" + typeName + "\". Use /wingman spawnuav for valid types.");
    }

    // ---- marker ----

    private static LiteralArgumentBuilder<CommandSourceStack> markerCommands() {
        return Commands.literal("marker")
                .executes(c -> usage(c, "/wingman marker <list|id <id>|type <type>>"))
                .then(Commands.literal("list").executes(WingmanCommand::markerList))
                .then(Commands.literal("id")
                        .executes(c -> usage(c, "/wingman marker id <id>"))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(c -> markerId(c, StringArgumentType.getString(c, "id")))))
                .then(Commands.literal("type")
                        .executes(c -> usage(c, "/wingman marker type <"
                                + String.join("|", Arrays.stream(MarkerType.values()).map(MarkerType::getSerializedName)
                                        .toList()) + ">"))
                        .then(Commands.argument("type", StringArgumentType.word()).suggests(MARKER_TYPES)
                                .executes(c -> markerType(c, StringArgumentType.getString(c, "type")))));
    }

    private static int markerList(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        List<MarkerRegistry.MarkerInfo> markers = MarkerRegistry.snapshot(player.level());
        if (markers.isEmpty()) {
            return info(c, "§7No markers registered.");
        }
        info(c, "§e=== Wingman Markers ===");
        for (MarkerRegistry.MarkerInfo m : markers) {
            info(c, "§7[" + m.type().name() + "] §fid=" + (m.id().isEmpty() ? "(none)" : m.id()) + " §7@ "
                    + m.pos().getX() + "," + m.pos().getY() + "," + m.pos().getZ());
        }
        return markers.size();
    }

    /**
     * The marker the player looks at, when they can reach and edit it as the marker screen requires.
     * Upstream looked 8 blocks ahead and let anyone anywhere edit the marker.
     */
    private static @Nullable WingmanMarkerBlockEntity lookedAtMarker(CommandContext<CommandSourceStack> c,
            ServerPlayer player) {
        HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK && !player.isSpectator()
                && player.level().mayInteract(player, bhr.getBlockPos())
                && player.level().getBlockEntity(bhr.getBlockPos()) instanceof WingmanMarkerBlockEntity marker) {
            return marker;
        }
        fail(c, "Look at a Wingman Marker block within reach.");
        return null;
    }

    private static int markerId(CommandContext<CommandSourceStack> c, String id) throws CommandSyntaxException {
        if (id.length() > 64) {
            return fail(c, "A marker ID takes at most 64 characters.");
        }
        WingmanMarkerBlockEntity marker = lookedAtMarker(c, c.getSource().getPlayerOrException());
        if (marker == null) {
            return 0;
        }
        marker.apply(marker.getMarkerType(), id, marker.getBaseId(), marker.getParkingHeading());
        return ok(c, "§aMarker id set to §e" + id);
    }

    private static int markerType(CommandContext<CommandSourceStack> c, String name) throws CommandSyntaxException {
        MarkerType type;
        try {
            type = MarkerType.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fail(c, "Unknown type: " + name);
        }
        ServerPlayer player = c.getSource().getPlayerOrException();
        WingmanMarkerBlockEntity marker = lookedAtMarker(c, player);
        if (marker == null) {
            return 0;
        }
        String id = WingmanMarkerBlock.idForType(player.level(), marker.getBlockPos(), marker.getMarkerId(), type);
        marker.apply(type, id, marker.getBaseId(), marker.getParkingHeading());
        return ok(c, "§aMarker type set to " + type.displayName() + " §7id=§e" + id);
    }

    static List<String> markerIds(ServerLevel level, MarkerType type) {
        List<String> ids = new ArrayList<>();
        for (MarkerRegistry.MarkerInfo m : MarkerRegistry.findByType(level, type)) {
            if (!m.id().isEmpty()) {
                ids.add(m.id());
            }
        }
        return ids;
    }

    // ---- route ----

    private static LiteralArgumentBuilder<CommandSourceStack> routeCommands() {
        return Commands.literal("route")
                .executes(c -> usage(c, "/wingman route <create <name> <node...>|list|delete <name>|show <name>>"))
                .then(Commands.literal("create")
                        .executes(c -> usage(c, "/wingman route create <name> <node> [node...]. Nodes: flyto:x,y,z  "
                                + "takeoff:id  land:id  attack:radius  loiter:ticks  park:id"))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("nodes", StringArgumentType.greedyString())
                                        .executes(WingmanCommand::routeCreate))))
                .then(Commands.literal("list").executes(WingmanCommand::routeList))
                .then(Commands.literal("delete")
                        .then(Commands.argument("name", StringArgumentType.word()).suggests(ROUTES)
                                .executes(c -> {
                                    ServerPlayer player = c.getSource().getPlayerOrException();
                                    return send(c, MissionPlanner.deleteRoute(player.level(),
                                            StringArgumentType.getString(c, "name")));
                                })))
                .then(Commands.literal("show")
                        .then(Commands.argument("name", StringArgumentType.word()).suggests(ROUTES)
                                .executes(WingmanCommand::routeShow)));
    }

    private static int routeCreate(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        List<String> tokens = Arrays.stream(StringArgumentType.getString(c, "nodes").trim().split("\\s+"))
                .filter(s -> !s.isEmpty()).toList();
        return send(c, MissionPlanner.createRoute(player.level(), StringArgumentType.getString(c, "name"), tokens));
    }

    private static int routeList(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        List<String> names = MissionPlan.names(c.getSource().getPlayerOrException().level());
        if (names.isEmpty()) {
            return info(c, "§7No routes defined.");
        }
        info(c, "§e=== Routes ===");
        names.forEach(n -> info(c, "§7  " + n));
        return names.size();
    }

    private static int routeShow(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String name = StringArgumentType.getString(c, "name");
        List<MissionNode> nodes = MissionPlan.get(c.getSource().getPlayerOrException().level(), name);
        if (nodes == null) {
            return fail(c, "Route not found: " + name);
        }
        info(c, "§e=== Route: " + name + " ===");
        for (int i = 0; i < nodes.size(); i++) {
            info(c, "§7  [" + i + "] " + nodes.get(i));
        }
        return Math.max(1, nodes.size());
    }

    // ---- mission ----

    private static LiteralArgumentBuilder<CommandSourceStack> missionCommands() {
        return Commands.literal("mission")
                .executes(c -> usage(c, "/wingman mission <assign <uuid> <route>|abort [uuid]|status>"))
                .then(Commands.literal("assign")
                        .then(Commands.argument("aircraft", StringArgumentType.word()).suggests(AIRCRAFT)
                                .then(Commands.argument("route", StringArgumentType.word()).suggests(ROUTES)
                                        .executes(WingmanCommand::missionAssign))))
                .then(Commands.literal("abort")
                        .executes(c -> {
                            c.getSource().getPlayerOrException();
                            return ok(c, "§aAborted " + MissionPlanner.abortAll() + " autonomous mission(s).");
                        })
                        .then(Commands.argument("aircraft", StringArgumentType.word()).suggests(AIRCRAFT)
                                .executes(c -> {
                                    c.getSource().getPlayerOrException();
                                    String arg = StringArgumentType.getString(c, "aircraft");
                                    UUID uid = parseUuid(c, arg);
                                    if (uid == null) {
                                        return 0;
                                    }
                                    MissionPlanner.abort(uid);
                                    return ok(c, "§aMission aborted for " + shortId(uid));
                                })))
                .then(Commands.literal("status").executes(WingmanCommand::missionStatus));
    }

    private static int missionAssign(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        UUID uid = parseUuid(c, StringArgumentType.getString(c, "aircraft"));
        if (uid == null) {
            return 0;
        }
        return send(c, MissionPlanner.assign(player.level(), uid, StringArgumentType.getString(c, "route"),
                io.github.mcheliceport.entity.NpcBattles.teamName(player)));
    }

    /** Upstream {@code mission status}, for the aircraft flying a planner route. */
    private static int missionStatus(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        c.getSource().getPlayerOrException();
        int count = 0;
        for (Map.Entry<UUID, WingmanEntry> e : WingmanRegistry.snapshot().entrySet()) {
            WingmanEntry entry = e.getValue();
            List<MissionNode> mission = entry.mission;
            if (mission == null || !entry.isAutonomous()) {
                continue;
            }
            count++;
            MissionNode cur = entry.currentNode();
            info(c, "§7[" + e.getKey().toString().substring(0, 8) + "] §fstate=" + entry.autoState + " node="
                    + entry.missionIndex + "/" + mission.size() + " §7(" + (cur != null ? cur : "done") + ")");
        }
        return count > 0 ? count : info(c, "§7No active autonomous missions.");
    }

    // ---- order ----

    private static LiteralArgumentBuilder<CommandSourceStack> orderCommands() {
        return Commands.literal("order")
                .executes(c -> usage(c, "/wingman order <dispatch|abort|rtb|status|park> [args]"))
                .then(Commands.literal("dispatch")
                        .executes(c -> usage(c, "/wingman order dispatch <uuid> <baseId> <types> <targetX> <targetZ>"
                                + " [orbitR] [alt] [passes] [seconds]. Types: cap, cas, strike, escort, recon, ferry;"
                                + " several as cap+recon"))
                        .then(Commands.argument("aircraft", StringArgumentType.word()).suggests(AIRCRAFT)
                                .then(Commands.argument("base", StringArgumentType.word()).suggests(BASES)
                                        .then(Commands.argument("types", StringArgumentType.string())
                                                .suggests(MISSION_TYPES)
                                                .then(Commands.argument("targetX", DoubleArgumentType.doubleArg(-3.0E7, 3.0E7))
                                                        .then(dispatchTail()))))))
                .then(Commands.literal("abort")
                        .executes(c -> orderAbort(c, null))
                        .then(Commands.argument("aircraft", StringArgumentType.word()).suggests(AIRCRAFT)
                                .executes(c -> orderAbort(c, StringArgumentType.getString(c, "aircraft")))))
                .then(Commands.literal("rtb")
                        .executes(c -> orderRtb(c, null))
                        .then(Commands.argument("aircraft", StringArgumentType.word()).suggests(AIRCRAFT)
                                .executes(c -> orderRtb(c, StringArgumentType.getString(c, "aircraft")))))
                .then(Commands.literal("status").executes(WingmanCommand::orderStatus))
                .then(Commands.literal("park")
                        .executes(c -> usage(c, "/wingman order park <uuid> <parkingId>"))
                        .then(Commands.argument("aircraft", StringArgumentType.word()).suggests(AIRCRAFT)
                                .then(Commands.argument("parking", StringArgumentType.word()).suggests(PARKINGS)
                                        .executes(WingmanCommand::orderPark))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Double> dispatchTail() {
        var seconds = Commands.argument("seconds", IntegerArgumentType.integer(0, 86400))
                .executes(c -> dispatch(c, 4));
        var passes = Commands.argument("passes", IntegerArgumentType.integer(0, 100))
                .executes(c -> dispatch(c, 3)).then(seconds);
        var alt = Commands.argument("alt", DoubleArgumentType.doubleArg(-2048, 4096))
                .executes(c -> dispatch(c, 2)).then(passes);
        var orbit = Commands.argument("orbitR", DoubleArgumentType.doubleArg(0, 10000))
                .executes(c -> dispatch(c, 1)).then(alt);
        return Commands.argument("targetZ", DoubleArgumentType.doubleArg(-3.0E7, 3.0E7))
                .executes(c -> dispatch(c, 0)).then(orbit);
    }

    /**
     * Upstream {@code order dispatch}: the order, with the mission types' default weapons, from the
     * base marker of that ID in the player's dimension. {@code extras} is how many of the optional
     * orbit radius, altitude, strike passes and time limit were given; the others keep the order's
     * defaults. Upstream's usage named the time limit in minutes but read seconds, as the base
     * screen does; the port reads seconds and says so. Brigadier ends a bare word at a comma, so
     * the port takes upstream's {@code cap,recon} in quotes, or as {@code cap+recon}.
     */
    private static int dispatch(CommandContext<CommandSourceStack> c, int extras) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        ServerLevel level = player.level();
        UUID uid = parseUuid(c, StringArgumentType.getString(c, "aircraft"));
        if (uid == null) {
            return 0;
        }
        String baseId = StringArgumentType.getString(c, "base");
        MarkerRegistry.MarkerInfo base = MarkerRegistry.findById(level, MarkerType.BASE, baseId);
        if (base == null) {
            return fail(c, "Base not found: " + baseId);
        }
        if (!level.mayInteract(player, base.pos())) {
            return fail(c, "You cannot order aircraft from that base.");
        }
        if (!(level.getEntity(uid) instanceof AircraftEntity wingman) || !MissionDispatch.canOrder(wingman, base.pos())) {
            return fail(c, "Entity not found, flown by a player or over " + (int) MissionDispatch.BASE_RANGE
                    + " blocks from the base.");
        }
        String typesArg = StringArgumentType.getString(c, "types");
        List<String> types = Arrays.asList(typesArg.split("[,+]"));
        Set<String> weapons = new LinkedHashSet<>();
        for (String t : types) {
            try {
                weapons.addAll(MissionType.valueOf(t.trim().toUpperCase(Locale.ROOT)).defaultWeapons());
            } catch (IllegalArgumentException ignored) {
                // Upstream skipped unknown types.
            }
        }
        MissionOrder defaults = new MissionOrder();
        DispatchOrderPayload p = new DispatchOrderPayload(base.pos(), uid, "", types, List.copyOf(weapons),
                DoubleArgumentType.getDouble(c, "targetX"), DoubleArgumentType.getDouble(c, "targetZ"),
                extras >= 1 ? DoubleArgumentType.getDouble(c, "orbitR") : defaults.orbitRadius,
                extras >= 2 ? DoubleArgumentType.getDouble(c, "alt") : defaults.cruiseAlt,
                extras >= 3 ? IntegerArgumentType.getInteger(c, "passes") : defaults.strikePasses,
                extras >= 4 ? IntegerArgumentType.getInteger(c, "seconds") : defaults.timeLimitSeconds,
                false, false, "");
        MissionOrder order = MissionDispatch.order(p, baseId, level);
        if (order == null) {
            return fail(c, "No valid mission types in: " + typesArg);
        }
        MissionDispatch.startOrder(wingman, order).teamName = io.github.mcheliceport.entity.NpcBattles.teamName(player);
        return ok(c, "§aOrder dispatched: §e" + typesArg + "§a to " + shortId(uid) + " §7(base=" + order.baseId
                + " target=" + (int) order.targetX + "," + (int) order.targetZ + ")");
    }

    /** Upstream {@code order abort}: ends the orders, and the parking holds of {@code order park}. */
    private static int orderAbort(CommandContext<CommandSourceStack> c, @Nullable String arg) throws CommandSyntaxException {
        c.getSource().getPlayerOrException();
        if (arg != null) {
            UUID uid = parseUuid(c, arg);
            if (uid == null) {
                return 0;
            }
            WingmanEntry entry = WingmanRegistry.get(uid);
            if (entry != null && (entry.hasOrder() || entry.parkingHold)) {
                abortOrder(entry);
            }
            return ok(c, "§aOrder aborted for " + shortId(uid));
        }
        int count = 0;
        for (WingmanEntry e : WingmanRegistry.snapshot().values()) {
            if (e.hasOrder() || e.parkingHold) {
                abortOrder(e);
                count++;
            }
        }
        return ok(c, "§aAborted " + count + " order(s).");
    }

    /**
     * A port addition: sends the order or planner mission home to land and park, every one in the
     * dimension without an aircraft.
     */
    private static int orderRtb(CommandContext<CommandSourceStack> c, @Nullable String arg) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        ServerLevel level = player.level();
        if (arg != null) {
            UUID uid = parseUuid(c, arg);
            if (uid == null) {
                return 0;
            }
            Component result = MissionPlanner.recall(level, uid);
            c.getSource().sendSuccess(() -> result, false);
            return 1;
        }
        return ok(c, "§aRecalled " + MissionPlanner.recallAll(level, null) + " aircraft.");
    }

    private static void abortOrder(WingmanEntry entry) {
        AutonomousFlightHandler.cancelOrder(entry);
        entry.parkingHold = false;
    }

    private static int orderStatus(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        c.getSource().getPlayerOrException();
        int count = 0;
        for (Map.Entry<UUID, WingmanEntry> e : WingmanRegistry.snapshot().entrySet()) {
            WingmanEntry entry = e.getValue();
            MissionOrder o = entry.order;
            if (o == null) {
                continue;
            }
            count++;
            info(c, "§7[" + e.getKey().toString().substring(0, 8) + "] §fstate=" + entry.autoState + " §7types="
                    + o.missionTypes + " timer=" + entry.orderTimer / 20 + "s rtb="
                    + (entry.rtbReason.isEmpty() ? "-" : entry.rtbReason));
        }
        return count > 0 ? count : info(c, "§7No active MissionOrders.");
    }

    /**
     * Upstream {@code order park}: the parking spot or helipad the aircraft leaves from on its next
     * order. Upstream's entry was dropped on the next tick unless the aircraft was a wingman, so
     * the spot was lost; the port keeps it until the order or {@code order abort}. An aircraft
     * flying an order returns to that spot.
     */
    private static int orderPark(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        ServerLevel level = player.level();
        String arg = StringArgumentType.getString(c, "aircraft");
        UUID uid = parseUuid(c, arg);
        if (uid == null) {
            return 0;
        }
        if (!MissionPlanner.canAssign(level.getEntity(uid))) {
            return fail(c, "Entity not found.");
        }
        String parkingId = StringArgumentType.getString(c, "parking");
        if (MarkerRegistry.findById(level, MarkerType.PARKING, parkingId) == null
                && MarkerRegistry.findById(level, MarkerType.HELIPAD, parkingId) == null) {
            return fail(c, "No parking spot or helipad: " + parkingId);
        }
        WingmanEntry entry = WingmanRegistry.get(uid);
        if (entry == null) {
            entry = new WingmanEntry();
            WingmanRegistry.put(uid, entry);
        }
        entry.assignedParkingId = parkingId;
        if (!entry.isAutonomous()) {
            entry.autoState = AutonomousState.PARKED;
            entry.parkingHold = true;
        }
        return ok(c, "§aParking assigned: §e" + parkingId + "§a for " + shortId(uid));
    }

    // ---- gui ----

    private static int gui(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        ModNetwork.sendToPlayer(player, MissionPlanner.buildData(player));
        return 1;
    }

    // ---- helpers ----

    private static @Nullable UUID parseUuid(CommandContext<CommandSourceStack> c, String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            fail(c, "Invalid UUID: " + s);
            return null;
        }
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8) + "...";
    }

    private static int usage(CommandContext<CommandSourceStack> c, String usage) {
        return info(c, "§7Usage: " + usage);
    }

    private static int info(CommandContext<CommandSourceStack> c, String text) {
        c.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int ok(CommandContext<CommandSourceStack> c, String text) {
        return info(c, text);
    }

    private static int fail(CommandContext<CommandSourceStack> c, String text) {
        c.getSource().sendFailure(Component.literal(text));
        return 0;
    }

    /** A message from the planner, which starts with red for a failure. */
    private static int send(CommandContext<CommandSourceStack> c, Component message) {
        String text = message.getString();
        if (text.startsWith("§c")) {
            return fail(c, text.substring(2));
        }
        return info(c, text);
    }
}
