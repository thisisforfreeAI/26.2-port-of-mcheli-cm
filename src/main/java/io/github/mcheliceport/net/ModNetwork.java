package io.github.mcheliceport.net;

import com.mojang.logging.LogUtils;
import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.config.ServerSettings;
import io.github.mcheliceport.effect.McheliEffects;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.aircraft.AircraftWeapons;
import io.github.mcheliceport.entity.weapon.TvMissileEntity;
import io.github.mcheliceport.weapon.WeaponClientHooks;
import io.github.mcheliceport.weapon.WeaponParam;
import io.github.mcheliceport.weapon.WeaponSet;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.entity.ship.ShipEntity;
import io.github.mcheliceport.entity.ground.GroundVehicleEntity;
import io.github.mcheliceport.entity.tank.TankEntity;
import io.github.mcheliceport.entity.aircraft.WingedAircraftEntity;
import io.github.mcheliceport.entity.seat.SeatEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The port's payloads (docs/M3_PLAN.md section 5) and the server's handling of them. Every
 * payload a client sends is checked against what that player rides and may do.
 */
public final class ModNetwork {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Bump when payload encoding, menu layout or synchronized entity data changes. */
    // M8: independent, optional explosion audio with distance-delayed client playback.
    public static final String VERSION = "31";

    private ModNetwork() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ModNetwork::registerPayloads);
        modBus.addListener(ModNetwork::onConfigReloaded);
        NeoForge.EVENT_BUS.addListener(ModNetwork::onPlayerLoggedIn);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        event.registrar(VERSION).optional().playToClient(VehicleBloodPayload.TYPE, VehicleBloodPayload.CODEC,
                (p, context) -> { var hooks = WeaponClientHooks.get(); if (hooks != null) hooks.vehicleBlood(p); });
        // Optional addition: older clients keep the existing protocol and simply receive no marks.
        event.registrar(VERSION).optional().playToClient(VehicleImpactPayload.TYPE,VehicleImpactPayload.CODEC,
                (p,context)->{var hooks=WeaponClientHooks.get();if(hooks!=null)hooks.vehicleImpact(p);});
        event.registrar(VERSION).optional().playToClient(VehicleMotionPayload.TYPE, VehicleMotionPayload.CODEC,
                (p, context) -> {
                    if (p.valid() && context.player().level().getEntity(p.entityId())
                            instanceof io.github.mcheliceport.entity.aircraft.AircraftEntity aircraft) {
                        aircraft.setWatchedVelocity(new net.minecraft.world.phys.Vec3(p.vx(), p.vy(), p.vz()));
                    }
                });
        registrar.playToServer(UavRosterActionPayload.TYPE, UavRosterActionPayload.CODEC, (p, context) -> {
            if (context.player().containerMenu instanceof io.github.mcheliceport.menu.UavStationMenu menu) { menu.rosterAction(context.player(), p); }
        });
        registrar.playToClient(UavRosterPayload.TYPE, UavRosterPayload.CODEC, (p, context) -> {
            if (context.player().containerMenu instanceof io.github.mcheliceport.menu.UavStationMenu menu) { menu.applyRoster(p); }
        });
        registrar.playToServer(DraftingTableCreatePayload.TYPE, DraftingTableCreatePayload.CODEC, (p, context) -> {
            if (context.player().containerMenu instanceof io.github.mcheliceport.menu.DraftingTableMenu menu
                    && menu.containerId == p.menuId()) {
                menu.create(p.recipe());
            }
        });
        registrar.playToClient(DraftingTableRecipesPayload.TYPE, DraftingTableRecipesPayload.CODEC, (p, context) -> {
            if (context.player().containerMenu instanceof io.github.mcheliceport.menu.DraftingTableMenu menu) {
                menu.applyRecipes(p);
            }
        });
        registrar.playToServer(UavStationControlPayload.TYPE, UavStationControlPayload.CODEC, (p, context) -> {
            applyUavStationControl(context.player(), p);
        });
        registrar.playToServer(GroundControlPayload.TYPE, GroundControlPayload.CODEC, (p, context) -> {
            if (riddenAircraft(context.player()) instanceof GroundVehicleEntity ground) {
                applyGroundControl(ground, context.player(), p);
            }
        });
        registrar.playToServer(HeliControlPayload.TYPE, HeliControlPayload.CODEC, ModNetwork::handleHeliControl);
        registrar.playToServer(PlaneControlPayload.TYPE, PlaneControlPayload.CODEC, ModNetwork::handlePlaneControl);
        registrar.playToServer(ShipControlPayload.TYPE, ShipControlPayload.CODEC, ModNetwork::handleShipControl);
        registrar.playToServer(AircraftAttitudePayload.TYPE, AircraftAttitudePayload.CODEC,
                ModNetwork::handleAttitude);
        registrar.playToServer(AircraftMotionPayload.TYPE, AircraftMotionPayload.CODEC, ModNetwork::handleMotion);
        registrar.playToServer(SeatControlPayload.TYPE, SeatControlPayload.CODEC, ModNetwork::handleSeatControl);
        registrar.playToServer(RackControlPayload.TYPE, RackControlPayload.CODEC,
                (p, context) -> applyRackControl(context.player(), p));
        registrar.playToServer(AircraftStatusRequestPayload.TYPE, AircraftStatusRequestPayload.CODEC,
                ModNetwork::handleStatusRequest);
        registrar.playToClient(AircraftStatusResponsePayload.TYPE, AircraftStatusResponsePayload.CODEC,
                ModNetwork::handleStatusResponse);
        registrar.playToClient(ExplosionSoundPayload.TYPE, ExplosionSoundPayload.CODEC,
                (p, context) -> { var hooks = WeaponClientHooks.get(); if (hooks != null) hooks.explosionSound(p); });
        registrar.playToClient(ExplosionEffectPayload.TYPE, ExplosionEffectPayload.CODEC,
                (p, context) -> McheliEffects.explosionEffects(context.player().level(), p.x(), p.y(), p.z(),
                        p.size(), p.inWater(), p.affectedBlocks(), p.blastStyle(), p.direction()));
        registrar.playToClient(ServerSettingsPayload.TYPE, ServerSettingsPayload.CODEC,
                (payload, context) -> ServerSettings.setClient(payload.toValues()));
        registrar.playToClient(TitlePayload.TYPE, TitlePayload.CODEC,
                (p, context) -> io.github.mcheliceport.command.CommandClientHooks.showTitle(p));
        registrar.playToClient(CommandRequestPayload.TYPE, CommandRequestPayload.CODEC,
                (p, context) -> io.github.mcheliceport.command.CommandClientHooks.answerRequest(p));
        registrar.playToServer(ScreenshotChunkPayload.TYPE, ScreenshotChunkPayload.CODEC, (p, context) -> {
            if (context.player() instanceof ServerPlayer sp) {
                io.github.mcheliceport.command.ClientRequests.acceptScreenshot(sp, p);
            }
        });
        registrar.playToServer(ModListPayload.TYPE, ModListPayload.CODEC, (p, context) -> {
            if (context.player() instanceof ServerPlayer sp) {
                io.github.mcheliceport.command.ClientRequests.acceptModList(sp, p);
            }
        });
        registrar.playToClient(EntityInfoSyncPayload.TYPE, EntityInfoSyncPayload.CODEC,
                (p, context) -> io.github.mcheliceport.radar.EntityInfoClientTracker.update(p.entities(),
                        p.snapshotSeq()));
        registrar.playToServer(GpsSetPayload.TYPE, GpsSetPayload.CODEC,
                (p, context) -> io.github.mcheliceport.weapon.GpsPosition.setServer(context.player(),
                        new io.github.mcheliceport.weapon.GpsPosition.Point(p.x(), p.y(), p.z(), p.active())));
        registrar.playToServer(BvrLockPayload.TYPE, BvrLockPayload.CODEC,
                (p, context) -> applyBvrLock(context.player(), p.targetId()));
        registrar.playToClient(OpenMarkerScreenPayload.TYPE, OpenMarkerScreenPayload.CODEC,
                (p, context) -> io.github.mcheliceport.wingman.WingmanClientHooks.openMarkerScreen(p));
        registrar.playToServer(MarkerUpdatePayload.TYPE, MarkerUpdatePayload.CODEC,
                (p, context) -> applyMarkerUpdate(context.player(), p));
        registrar.playToClient(OpenBaseScreenPayload.TYPE, OpenBaseScreenPayload.CODEC,
                (p, context) -> io.github.mcheliceport.wingman.WingmanClientHooks.openBaseScreen(p));
        registrar.playToServer(TaxiRouteActionPayload.TYPE, TaxiRouteActionPayload.CODEC,
                (p, context) -> applyTaxiRouteAction(context.player(), p));
        registrar.playToServer(DispatchOrderPayload.TYPE, DispatchOrderPayload.CODEC,
                (p, context) -> applyDispatchOrder(context.player(), p));
        registrar.playToClient(PlannerDataPayload.TYPE, PlannerDataPayload.CODEC,
                (p, context) -> io.github.mcheliceport.wingman.WingmanClientHooks.openPlanner(p));
        registrar.playToServer(MissionActionPayload.TYPE, MissionActionPayload.CODEC,
                (p, context) -> io.github.mcheliceport.wingman.MissionPlanner.apply(context.player(), p));
        registrar.playToServer(MissionRouteActionPayload.TYPE, MissionRouteActionPayload.CODEC,
                (p, context) -> io.github.mcheliceport.wingman.MissionPlanner.apply(context.player(), p));
        registrar.playToServer(WingmanPanelOpenPayload.TYPE, WingmanPanelOpenPayload.CODEC,
                (p, context) -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        sendToPlayer(sp, io.github.mcheliceport.wingman.WingmanPanel.buildData(sp));
                    }
                });
        registrar.playToClient(WingmanPanelDataPayload.TYPE, WingmanPanelDataPayload.CODEC,
                (p, context) -> io.github.mcheliceport.wingman.WingmanClientHooks.openWingmanPanel(p));
        registrar.playToServer(WingmanPanelActionPayload.TYPE, WingmanPanelActionPayload.CODEC,
                (p, context) -> io.github.mcheliceport.wingman.WingmanPanel.apply(context.player(), p));
        registrar.playToServer(WeaponControlPayload.TYPE, WeaponControlPayload.CODEC, ModNetwork::handleWeaponControl);
        registrar.playToServer(AmmoRequestPayload.TYPE, AmmoRequestPayload.CODEC, ModNetwork::handleAmmoRequest);
        registrar.playToServer(ResupplyPayload.TYPE, ResupplyPayload.CODEC, ModNetwork::handleResupply);
        registrar.playToServer(AircraftCommandPayload.TYPE, AircraftCommandPayload.CODEC,
                ModNetwork::handleAircraftCommand);
        registrar.playToServer(MultiplayActionPayload.TYPE, MultiplayActionPayload.CODEC, (p, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                io.github.mcheliceport.multiplay.MultiplayManager.handle(player, p.action(), p.team());
            }
        });
        registrar.playToClient(MultiplayStatePayload.TYPE, MultiplayStatePayload.CODEC,
                (p, context) -> io.github.mcheliceport.multiplay.MultiplayManager.setClientState(p.pvp(), p.chooseTeam()));
        registrar.playToServer(AirburstDistPayload.TYPE, AirburstDistPayload.CODEC, ModNetwork::handleAirburstDist);
        registrar.playToServer(CrewMarkPayload.TYPE, CrewMarkPayload.CODEC, ModNetwork::handleCrewMark);
        registrar.playToClient(WeaponSyncPayload.TYPE, WeaponSyncPayload.CODEC, ModNetwork::handleWeaponSync);
        registrar.playToClient(NotifyAmmoPayload.TYPE, NotifyAmmoPayload.CODEC, (p, context) -> {
            if (context.player().level().getEntity(p.aircraftId()) instanceof AircraftEntity ac) {
                ac.weapons().applyAmmo(p);
            }
        });
        registrar.playToClient(SyncReloadPayload.TYPE, SyncReloadPayload.CODEC, (p, context) -> {
            if (context.player().level().getEntity(p.aircraftId()) instanceof AircraftEntity ac) {
                ac.weapons().manualReloadForPlayer(context.player());
            }
        });
        registrar.playToClient(NotifyHitPayload.TYPE, NotifyHitPayload.CODEC, (p, context) -> {
            if (p.aircraftId() <= 0) {
                WeaponClientHooks.hitMarker();
            } else if (context.player().level().getEntity(p.aircraftId()) instanceof AircraftEntity ac) {
                ac.hitBullet();
            }
        });
        registrar.playToClient(TvMissilePayload.TYPE, TvMissilePayload.CODEC, (p, context) -> {
            Level level = context.player().level();
            if (level.getEntity(p.aircraftId()) instanceof AircraftEntity ac
                    && level.getEntity(p.missileId()) instanceof TvMissileEntity missile) {
                ac.setTVMissile(missile);
            }
        });
        registrar.playToClient(ChaffUsePayload.TYPE, ChaffUsePayload.CODEC, (p, context) -> {
            if (context.player().level().getEntity(p.aircraftId()) instanceof AircraftEntity ac) {
                ac.chaff().start(p.useTick());
            }
        });
        registrar.playToClient(LaserShotPayload.TYPE, LaserShotPayload.CODEC,
                (p, context) -> WeaponClientHooks.laserShot(p));
        registrar.playToClient(ClientSoundPayload.TYPE, ClientSoundPayload.CODEC,
                (p, context) -> WeaponClientHooks.clientSound(p));
        registrar.playToServer(RangeFinderSpotPayload.TYPE, RangeFinderSpotPayload.CODEC, (p, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                io.github.mcheliceport.item.RangeFinderItem.handleSpotRequest(player, p.targetFilter());
            }
        });
        registrar.playToServer(LightWeaponControlPayload.TYPE, LightWeaponControlPayload.CODEC, (p, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                io.github.mcheliceport.item.LightWeaponItem.handleControl(player, p);
            }
        });
        registrar.playToServer(GltdControlPayload.TYPE, GltdControlPayload.CODEC, (p, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                io.github.mcheliceport.entity.gltd.GltdEntity.handleControl(player, p);
            }
        });
        registrar.playToClient(SpotEnemyPayload.TYPE, SpotEnemyPayload.CODEC,
                (p, context) -> io.github.mcheliceport.multiplay.SpotClientHooks.spotted(p.count(), p.entityIds()));
        registrar.playToClient(MarkPosPayload.TYPE, MarkPosPayload.CODEC,
                (p, context) -> io.github.mcheliceport.multiplay.SpotClientHooks.markPoint(p.x(), p.y(), p.z()));
    }

    // ---- server settings ----

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendSettings(player, ServerSettingsPayload.of(ServerSettings.server()));
        }
    }

    /** Sends the settings to a player whose connection knows them (not to fake players or vanilla clients). */
    private static void sendSettings(ServerPlayer player, ServerSettingsPayload payload) {
        sendToPlayer(player, payload);
    }

    /**
     * Sends a payload to a player whose connection knows it. Fake players (GameTest and other mods')
     * and clients without the mod are skipped: NeoForge throws for a payload a connection did not
     * negotiate, which would crash the server tick that sent it.
     */
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload.type())) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /** {@link PacketDistributor#sendToPlayersNear}, through {@link #sendToPlayer}. */
    public static void sendToPlayersNear(ServerLevel level, @Nullable ServerPlayer excluded, double x, double y,
            double z, double radius, CustomPacketPayload payload) {
        for (ServerPlayer player : level.players()) {
            if (player != excluded && player.distanceToSqr(x, y, z) < radius * radius) {
                sendToPlayer(player, payload);
            }
        }
    }

    /** {@link PacketDistributor#sendToAllPlayers}, through {@link #sendToPlayer}. */
    public static void sendToAllPlayers(net.minecraft.server.MinecraftServer server, CustomPacketPayload payload) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendToPlayer(player, payload);
        }
    }

    /** {@link PacketDistributor#sendToPlayersInDimension}, through {@link #sendToPlayer}. */
    public static void sendToPlayersInDimension(ServerLevel level, CustomPacketPayload payload) {
        for (ServerPlayer player : level.players()) {
            sendToPlayer(player, payload);
        }
    }

    private static void onConfigReloaded(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != ServerConfig.SPEC || event.getConfig().getType() != ModConfig.Type.SERVER) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(() -> sendServerSettings(server));
        }
    }

    /** Sends every player the server settings again (a config change, {@code /mcheli reconfig}). */
    public static void sendServerSettings(MinecraftServer server) {
        ServerSettingsPayload payload = ServerSettingsPayload.of(ServerSettings.server());
        server.getPlayerList().getPlayers().forEach(player -> sendSettings(player, payload));
    }

    // ---- client to server ----

    public static void applyUavStationControl(Player player, UavStationControlPayload p) {
        if (!player.level().isClientSide() && player.getVehicle() instanceof io.github.mcheliceport.entity.uav.UavStationEntity station
                && station.getId() == p.stationId() && station.operator() == player) {
            if (p.exit()) { station.exit(player); } else { station.openMenu(player); }
        }
    }

    public static void applyRackControl(Player player, RackControlPayload p) {
        AircraftEntity aircraft = riddenAircraft(player);
        if (aircraft == null || aircraft.getId() != p.aircraftId() || !aircraft.isPilot(player) || aircraft.isDestroyed()) { return; }
        switch (p.action()) {
            case LOAD -> aircraft.racks().load();
            case UNLOAD -> aircraft.racks().unload();
            case RIDE -> aircraft.racks().ride();
            case LEAVE -> aircraft.racks().leave();
            case UNHITCH -> unhitch(player, aircraft);
            default -> { }
        }
    }

    /**
     * Upstream {@code handleChain}: the pilot lets go of what the vehicle tows. Dropping a cargo
     * container with nothing in the 20 blocks below earns the relief supplies advancement.
     */
    public static void unhitch(Player player, AircraftEntity aircraft) {
        var chain = io.github.mcheliceport.entity.chain.ChainEntity.attached(aircraft);
        if (chain == null || chain.tow() != aircraft) {
            return;
        }
        if (chain.towed() instanceof io.github.mcheliceport.entity.container.CargoContainerEntity
                && !aircraft.blockWithin(3, -20)) {
            io.github.mcheliceport.registry.ModCriteriaTriggers.trigger(
                    io.github.mcheliceport.registry.ModCriteriaTriggers.RELIEF_SUPPLIES, player);
        }
        chain.discard();
    }

    /** The vehicle a player rides, in seat 0 or a crew seat (upstream's lookup in {@code onReceive}). */
    /** Upstream {@code PacketLockTargetBVR.onReceive}: the rider's current seeker takes the BVR target. */
    /**
     * Upstream {@code PacketMarkerUpdate}. Upstream accepted it from anyone for any marker; the
     * port requires the player to be able to reach and edit the block, as vanilla's sign editing does.
     */
    public static void applyMarkerUpdate(Player player, MarkerUpdatePayload p) {
        var marker = editableMarker(player, p.pos());
        if (marker != null) {
            marker.apply(p.markerType(), p.id().trim(), p.baseId().trim(), Mth.clamp(p.parkingHeading(), -1, 3));
        }
    }

    /** The marker at the position, when the player can reach and edit it, as vanilla's sign editing. */
    private static io.github.mcheliceport.wingman.block.@Nullable WingmanMarkerBlockEntity editableMarker(
            Player player, net.minecraft.core.BlockPos pos) {
        if (player.isSpectator() || !player.isWithinBlockInteractionRange(pos, 4.0)
                || !player.level().mayInteract(player, pos)
                || !(player.level().getBlockEntity(pos)
                        instanceof io.github.mcheliceport.wingman.block.WingmanMarkerBlockEntity marker)) {
            return null;
        }
        return marker;
    }

    /** Upstream {@code PacketBaseAction}'s {@code DISPATCH_ORDER}, from the base marker the player can edit. */
    public static void applyDispatchOrder(Player player, DispatchOrderPayload p) {
        io.github.mcheliceport.wingman.MissionDispatch.apply(player, p, editableMarker(player, p.pos()));
    }

    /**
     * Upstream {@code PacketBaseAction}'s route actions, from the base screen of the base marker
     * at the position. The base is the marker's, and a delete only removes that base's route;
     * upstream took both from the client.
     */
    public static void applyTaxiRouteAction(Player player, TaxiRouteActionPayload p) {
        var marker = editableMarker(player, p.pos());
        if (marker == null || marker.getMarkerType() != io.github.mcheliceport.wingman.block.MarkerType.BASE
                || marker.getMarkerId().isEmpty() || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        var route = p.route().withBase(marker.getMarkerId());
        if (p.delete()) {
            var saved = io.github.mcheliceport.wingman.registry.TaxiRouteRegistry.findById(level, route.routeId());
            if (saved != null && saved.baseId().equals(route.baseId())) {
                io.github.mcheliceport.wingman.registry.TaxiRouteRegistry.delete(level, route.routeId());
            }
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "\u00a7aRoute \u00a7e" + route.routeId() + "\u00a7a deleted."));
            return;
        }
        if (route.routeId().isEmpty() || route.parkingId().isEmpty() || route.runwayId().isEmpty()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "\u00a7cRoute requires routeId, baseId, parkingId, runwayId."));
            return;
        }
        io.github.mcheliceport.wingman.registry.TaxiRouteRegistry.save(level, route);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "\u00a7aRoute \u00a7e" + route.routeId() + "\u00a7a saved."));
    }

    public static void applyBvrLock(Player player, int targetId) {
        AircraftEntity ac = riddenAircraft(player);
        io.github.mcheliceport.weapon.WeaponSet ws = ac != null ? ac.getCurrentWeapon(player) : null;
        io.github.mcheliceport.weapon.guidance.WeaponGuidanceSystem gs =
                ws != null && ws.getCurrentWeapon() != null ? ws.getCurrentWeapon().getGuidanceSystem() : null;
        if (gs == null) {
            return;
        }
        if (targetId <= 0) {
            gs.setTarget(null);
        } else {
            net.minecraft.world.entity.Entity target = player.level().getEntity(targetId);
            if (target != null) {
                if (!bvrTargetValid(ac, player, target)) {
                    if (player instanceof ServerPlayer sp) {
                        io.github.mcheliceport.combat.AntiCheat.violation(sp,
                                io.github.mcheliceport.combat.AntiCheat.Check.LOCK_TARGET,
                                "BVR lock on " + target.getType().getDescriptionId());
                    }
                    return;
                }
                gs.setTarget(target);
            }
        }
    }

    /**
     * M9: a BVR lock must name a live target within the radar feed's range that is not the shooter,
     * its vehicle or anyone aboard it.
     */
    public static boolean bvrTargetValid(AircraftEntity ac, Player player, Entity target) {
        if (!target.isAlive() || target == player || target == ac || ac.isMountedEntity(target)
                || target.getRootVehicle() == ac) {
            return false;
        }
        double dx = target.getX() - ac.getX();
        double dz = target.getZ() - ac.getZ();
        double range = io.github.mcheliceport.radar.EntityInfoFeed.RANGE;
        return dx * dx + dz * dz <= range * range;
    }

    public static @Nullable AircraftEntity riddenAircraft(Player player) {
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof AircraftEntity aircraft) {
            return aircraft;
        }
        if (vehicle instanceof SeatEntity seat) {
            return seat.getParent();
        }
        if (vehicle instanceof io.github.mcheliceport.entity.uav.UavStationEntity station) {
            var ac = station.controlled();
            return ac != null && station.operator() == player && station.validLink(ac) && ac.uavStation() == station ? ac : null;
        }
        return null;
    }

    static void handleHeliControl(HeliControlPayload p, IPayloadContext context) {
        Player player = context.player();
        if (!(riddenAircraft(player) instanceof HeliEntity heli) || heli.getId() != p.entityId()) {
            return;
        }
        applyHeliControl(heli, player, p);
    }

    /** Upstream {@code PacketPlayerControlHeli#process}, for the M3 fields, with the port's checks. */
    public static void applyHeliControl(HeliEntity heli, Player player, HeliControlPayload p) {
        applyAircraftControl(heli, player, p);
    }

    private static void applyAircraftControl(AircraftEntity aircraft, Player player, HeliControlPayload p) {
        if (riddenAircraft(player) != aircraft || aircraft.getId() != p.entityId()) {
            return;
        }
        boolean pilot = aircraft.isPilot(player);
        if (p.unmount() == HeliControlPayload.UnmountAction.UNMOUNT_SELF) {
            if (player.getVehicle() instanceof io.github.mcheliceport.entity.uav.UavStationEntity station) {
                station.exit(player); return;
            }
            // Upstream always unmounted the pilot; only the pilot's client sent it.
            if (pilot) {
                aircraft.unmountEntity();
            } else if (aircraft.ropes().active()) {
                aircraft.ropes().descend(player);
            } else if (!aircraft.parachuteCrew(player)) {
                player.stopRiding();
            }
            return;
        }
        if (aircraft.isDestroyed()) {
            return;
        }
        if (p.unmount() == HeliControlPayload.UnmountAction.UNMOUNT_CREW) {
            if (pilot) { aircraft.unmountCrew(); }
            return;
        }
        if (pilot && p.unmount() == HeliControlPayload.UnmountAction.UNMOUNT_AIRCRAFT) {
            aircraft.racks().leave(); return;
        }
        if (p.switches().get(PlayerControlSwitches.EJECT_SEAT)) {
            aircraft.ejectSeat(player);
            return;
        }
        if (pilot) {
            if (aircraft instanceof HeliEntity helicopter) {
                switch (p.bladeStatus()) {
                    case UNFOLD -> {
                        if (helicopter.canSwitchFoldBlades() && helicopter.isFoldBlades()) {
                            helicopter.setFoldBladeStat(HeliEntity.BLADES_UNFOLDING);
                        }
                    }
                    case FOLD -> {
                        if (helicopter.canSwitchFoldBlades() && !helicopter.isFoldBlades()) {
                            helicopter.setFoldBladeStat(HeliEntity.BLADES_FOLDING);
                        }
                    }
                    default -> {
                    }
                }
            }
            switch (p.modeSwitch()) {
                case GUNNER_OFF -> aircraft.switchGunnerMode(false);
                case GUNNER_ON -> aircraft.switchGunnerMode(true);
                case HOVERING_OFF -> aircraft.switchHoveringMode(false);
                case HOVERING_ON -> aircraft.switchHoveringMode(true);
                default -> {
                }
            }
            PlayerControlSwitches s = p.switches();
            aircraft.throttleUp = s.throttleUp();
            aircraft.throttleDown = s.throttleDown();
            aircraft.moveLeft = s.moveLeft();
            aircraft.moveRight = s.moveRight();
            if (aircraft.ropes().active()) { aircraft.ropes().clearControls(); }
            if (p.hatch() != HeliControlPayload.HatchSwitch.NONE && aircraft.getAcInfo() != null
                    && aircraft.getAcInfo().haveHatch()) {
                aircraft.foldHatch(p.hatch() == HeliControlPayload.HatchSwitch.UNFOLD);
            }
            if (p.switchFreeLook() > 0) {
                aircraft.switchFreeLookMode(p.switchFreeLook() == 1);
            }
            switch (p.gear()) {
                case FOLD -> aircraft.foldLandingGear();
                case UNFOLD -> aircraft.unfoldLandingGear();
                default -> {
                }
            }
        }
        if (p.switches().openGui()) {
            aircraft.openContainer(player);
        }
        if (p.switches().get(PlayerControlSwitches.SWITCH_SEARCH_LIGHT) && aircraft.canSwitchSearchLight(player)) {
            aircraft.setSearchLight(!aircraft.isSearchLightOn());
        }
        if (p.switches().switchGunnerStatus()) {
            aircraft.setGunnerStatus(!aircraft.getGunnerStatus());
        }
        if (p.switches().get(PlayerControlSwitches.RADAR_ON) || p.switches().get(PlayerControlSwitches.RADAR_OFF)) {
            aircraft.setRadarEnabledRuntime(p.switches().get(PlayerControlSwitches.RADAR_ON), false);
        }
        if (p.switchCameraMode() > 0 && aircraft.canSwitchCameraMode(aircraft.getSeatIdByEntity(player))) {
            aircraft.switchCameraMode(player, p.switchCameraMode() - 1);
        }
    }

    public static void applyGroundControl(GroundVehicleEntity ground, Player player, GroundControlPayload p) {
        if (riddenAircraft(player) != ground || ground.getId() != p.controls().entityId()) { return; }
        applyAircraftControl(ground, player, p.controls());
        if (ground instanceof TankEntity && ground.isPilot(player) && !ground.isDestroyed()) {
            ground.setBrake(p.brake());
            if (p.brake()) {
                ground.throttleUp = ground.throttleDown = false;
                if (ground.getCurrentThrottle() <= 0.03 && ground.getDeltaMovement().horizontalDistanceSqr() < 0.01) {
                    ground.moveLeft = ground.moveRight = false;
                }
            }
        }
    }

    static void handlePlaneControl(PlaneControlPayload p, IPayloadContext context) {
        if (riddenAircraft(context.player()) instanceof PlaneEntity plane) {
            applyPlaneControl(plane, context.player(), p);
        }
    }

    public static void applyPlaneControl(PlaneEntity plane, Player player, PlaneControlPayload p) {
        applyWingedControl(plane, player, p.controls(), p.vtol(), p.wing());
    }

    static void handleShipControl(ShipControlPayload p, IPayloadContext context) {
        if (riddenAircraft(context.player()) instanceof ShipEntity ship) {
            applyShipControl(ship, context.player(), p);
        }
    }

    public static void applyShipControl(ShipEntity ship, Player player, ShipControlPayload p) {
        applyWingedControl(ship, player, p.controls(), p.vtol(), p.wing());
    }

    private static void applyWingedControl(WingedAircraftEntity plane, Player player, HeliControlPayload controls,
            PlaneControlPayload.PartSwitch vtol, PlaneControlPayload.PartSwitch wing) {
        if (riddenAircraft(player) != plane || plane.getId() != controls.entityId()) {
            return;
        }
        applyAircraftControl(plane, player, controls);
        if (!plane.isPilot(player) || plane.isDestroyed()
                || controls.unmount() != HeliControlPayload.UnmountAction.NONE) {
            return;
        }
        if (vtol != PlaneControlPayload.PartSwitch.NONE && plane.canSwitchVtol()) {
            plane.switchVtol(vtol == PlaneControlPayload.PartSwitch.ON);
            plane.setModeSwitchCooldown(20);
        }
        if (wing == PlaneControlPayload.PartSwitch.ON && plane.canFoldWing()) {
            plane.foldWing(true);
        } else if (wing == PlaneControlPayload.PartSwitch.OFF && plane.canUnfoldWing()) {
            plane.foldWing(false);
        }
    }

    static void handleAttitude(AircraftAttitudePayload p, IPayloadContext context) {
        AircraftEntity aircraft = riddenAircraft(context.player());
        if (aircraft != null) { applyPilotAttitude(aircraft, context.player(), p); }
    }

    public static void applyPilotAttitude(AircraftEntity aircraft, Player player, AircraftAttitudePayload p) {
        if (riddenAircraft(player) != aircraft || aircraft.getId() != p.entityId()
                || !aircraft.isPilot(player) || aircraft.isDestroyed() || aircraft.isPassenger() || aircraft.isTowed() || aircraft.ropes().active()) {
            return;
        }
        if (!Float.isFinite(p.yaw()) || !Float.isFinite(p.pitch()) || !Float.isFinite(p.roll())) {
            return;
        }
        if (p.rollReversed()) {
            // Upstream PacketIndRotation: a roll past ±90° turned the riders round.
            player.setYRot(p.yaw());
            player.yRotO = p.yaw();
            for (SeatEntity seat : aircraft.getSeats()) {
                Entity rider = seat != null ? seat.getRider() : null;
                if (rider != null) {
                    rider.setYRot(rider.getYRot() + (rider.getYRot() <= 0.0F ? 180.0F : -180.0F));
                }
            }
        }
        aircraft.setAttitudeFromPilot(p.yaw(), p.pitch(), p.roll());
    }

    static void handleMotion(AircraftMotionPayload p, IPayloadContext context) {
        Player player = context.player();
        if (!(player.getVehicle() instanceof AircraftEntity aircraft) || aircraft.getId() != p.entityId()
                || !aircraft.isPilot(player) || !aircraft.followsPilot()) {
            return;
        }
        applyPilotMotion(aircraft, p);
    }

    /** Takes the pilot's velocity and throttle, limited to what the aircraft can do. */
    public static void applyPilotMotion(AircraftEntity aircraft, AircraftMotionPayload p) {
        if (!aircraft.followsPilot() || aircraft.getId() != p.entityId()
                || !Double.isFinite(p.motionX()) || !Double.isFinite(p.motionY()) || !Double.isFinite(p.motionZ())
                || !Float.isFinite(p.throttle())) {
            return;
        }
        double limit = aircraft.getMaxSpeedForChecks();
        double horizontal = Math.sqrt(p.motionX() * p.motionX() + p.motionZ() * p.motionZ());
        double f = horizontal > limit ? limit / horizontal : 1.0;
        aircraft.setDeltaMovement(p.motionX() * f, Mth.clamp(p.motionY(), -10.0, 1.0), p.motionZ() * f);
        aircraft.setCurrentThrottle(aircraft.canUseFuel(true) || aircraft.getCurrentThrottle() >= p.throttle()
                ? Mth.clamp(p.throttle(), 0.0, 1.0) : aircraft.getCurrentThrottle());
    }

    static void handleSeatControl(SeatControlPayload p, IPayloadContext context) {
        Player player = context.player();
        AircraftEntity aircraft = riddenAircraft(player);
        if (aircraft == null || player.getVehicle() instanceof io.github.mcheliceport.entity.uav.UavStationEntity) {
            return;
        }
        switch (p.action()) {
            case NEXT -> aircraft.switchNextSeat(player);
            case PREV -> aircraft.switchPrevSeat(player);
            case DISMOUNT -> {
                player.stopRiding();
                aircraft.interactSwitchingSeat(player, net.minecraft.world.InteractionHand.MAIN_HAND);
            }
            default -> {
            }
        }
    }

    // ---- weapons ----

    static void handleWeaponControl(WeaponControlPayload p, IPayloadContext context) {
        Player player = context.player();
        AircraftEntity aircraft = riddenAircraft(player);
        if (aircraft == null || aircraft.getId() != p.entityId()) {
            return;
        }
        if (player instanceof ServerPlayer sp) {
            p = screenWeaponControl(aircraft, sp, p);
        }
        applyWeaponControl(aircraft, player, p);
    }

    /**
     * M9 anti-cheat on a player's fire packet: too many fire packets or an impossible aim drop the
     * shot; an implausible fire origin is replaced by the vehicle's server position. Each failure
     * is recorded ({@link io.github.mcheliceport.combat.AntiCheat}). Everything but the shot passes.
     */
    public static WeaponControlPayload screenWeaponControl(AircraftEntity aircraft, ServerPlayer player,
            WeaponControlPayload p) {
        if (!p.useWeapon()) {
            return p;
        }
        if (!io.github.mcheliceport.combat.AntiCheat.allowFirePacket(player)) {
            return p.withoutUseWeapon();
        }
        if (!io.github.mcheliceport.combat.AntiCheat.aimValid(p.userYaw(), p.userPitch())) {
            io.github.mcheliceport.combat.AntiCheat.violation(player, io.github.mcheliceport.combat.AntiCheat.Check.AIM,
                    "aim " + p.userYaw() + "/" + p.userPitch());
            return p.withoutUseWeapon();
        }
        if (!Double.isFinite(p.x()) || !Double.isFinite(p.y()) || !Double.isFinite(p.z())
                || !io.github.mcheliceport.combat.AntiCheat.checkOrigin(player, aircraft, p.x(), p.y(), p.z())) {
            return p.withUseWeapon(p.option1(), p.option2(), aircraft.getX(), aircraft.getY(), aircraft.getZ(),
                    p.userYaw(), p.userPitch());
        }
        return p;
    }

    /** Upstream {@code PacketPlayerControlBase}'s weapon switch, use weapon and flare handling. */
    public static void applyWeaponControl(AircraftEntity aircraft, Player player, WeaponControlPayload p) {
        if (aircraft.isDestroyed()) {
            return;
        }
        AircraftWeapons weapons = aircraft.weapons();
        if (p.switchWeapon() >= 0) {
            weapons.switchWeapon(player, p.switchWeapon());
        }
        if (p.reload()) {
            weapons.manualReloadForPlayer(player);
        }
        var role = io.github.mcheliceport.entity.aircraft.CrewRoles.roleOf(aircraft, player);
        // M10b: loaders and passengers fire nothing, whatever weapon their seat has.
        if (p.useWeapon() && (role == null || role.canFire()) && Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z())
                && Float.isFinite(p.userYaw()) && Float.isFinite(p.userPitch())) {
            WeaponParam prm = new WeaponParam();
            prm.entity = aircraft;
            prm.user = player;
            // The client fires from where it saw the vehicle; a position far from the server's is not trusted.
            double dx = p.x() - aircraft.getX();
            double dy = p.y() - aircraft.getY();
            double dz = p.z() - aircraft.getZ();
            if (dx * dx + dy * dy + dz * dz < 16.0 * 16.0) {
                prm.setPosAndRot(p.x(), p.y(), p.z(), 0.0F, 0.0F);
            } else {
                prm.setPosAndRot(aircraft.getX(), aircraft.getY(), aircraft.getZ(), 0.0F, 0.0F);
            }
            prm.option1 = p.option1();
            prm.option2 = p.option2();
            weapons.setPacketWeaponUserAim(player, p.userYaw(), p.userPitch());
            boolean fired;
            try {
                weapons.updateWeaponsRotation();
                fired = weapons.useCurrentWeapon(prm);
            } finally {
                weapons.clearPacketWeaponUserAim();
            }
            if (!fired && player instanceof ServerPlayer mp) {
                weapons.syncCurrentWeapon(mp);
            }
        }
        // The client only offers countermeasures to the pilot and seat 1.
        if (aircraft.getSeatIdByEntity(player) <= 1) {
            if (p.flareType() > 0) {
                aircraft.useFlare(p.flareType());
            }
            if (p.useChaff()) {
                aircraft.useChaff();
            }
            if (p.useAps()) {
                aircraft.useAPS();
            }
            if (p.useEcm()) {
                aircraft.useECMJammer();
            }
        }
    }

    /** Upstream {@code PacketRequestResupply}: only from a player who has that vehicle's menu open. */
    static void handleResupply(ResupplyPayload p, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
                && player.containerMenu instanceof io.github.mcheliceport.menu.AircraftMenu menu
                && menu.getAircraft() != null && menu.getAircraft().getId() == p.aircraftId()
                && p.weaponId() >= 0 && p.weaponId() < menu.getAircraft().getWeaponNum()) {
            menu.getAircraft().weapons().supplyAmmo(p.weaponId(), player);
        }
    }

    /** Upstream {@code PacketCommandSave}, from the menu of that vehicle only. */
    static void handleAircraftCommand(AircraftCommandPayload p, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
                && player.containerMenu instanceof io.github.mcheliceport.menu.AircraftMenu menu
                && menu.getAircraft() != null && menu.getAircraft().getId() == p.aircraftId()) {
            menu.getAircraft().setCommand(p.command(), player);
        }
    }

    static void handleAmmoRequest(AmmoRequestPayload p, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && riddenAircraft(player) instanceof AircraftEntity ac
                && ac.getId() == p.aircraftId()) {
            if (p.weaponId() >= 0) {
                ac.weapons().sendAmmoNum(player, p.weaponId());
            } else {
                ac.weapons().sendAllAmmoNum(player);
            }
        }
    }

    /** M10b: only a rider in a commander seat of that vehicle marks. */
    static void handleCrewMark(CrewMarkPayload p, IPayloadContext context) {
        Player player = context.player();
        if (riddenAircraft(player) instanceof AircraftEntity ac && ac.getId() == p.aircraftId()
                && io.github.mcheliceport.entity.aircraft.CrewRoles.roleOf(ac, player)
                        == io.github.mcheliceport.content.aircraft.CrewRole.COMMANDER) {
            ac.setCrewMark(io.github.mcheliceport.entity.aircraft.CrewRoles.markTarget(ac, player));
        }
    }

    static void handleAirburstDist(AirburstDistPayload p, IPayloadContext context) {
        Player player = context.player();
        if (riddenAircraft(player) instanceof AircraftEntity ac && ac.getId() == p.aircraftId()) {
            WeaponSet ws = ac.getCurrentWeapon(player);
            if (ws.getCurrentWeapon() != null) {
                ws.getCurrentWeapon().airburstDist = Math.max(0, p.dist());
            }
        }
    }

    static void handleWeaponSync(WeaponSyncPayload p, IPayloadContext context) {
        if (context.player().level().getEntity(p.aircraftId()) instanceof AircraftEntity ac) {
            ac.weapons().applySync(p, ac.getEntityBySeatId(p.seatId()) == context.player());
        }
    }

    static void handleStatusRequest(AircraftStatusRequestPayload p, IPayloadContext context) {
        Player player = context.player();
        if (player instanceof ServerPlayer serverPlayer
                && player.level().getEntity(p.entityId()) instanceof AircraftEntity aircraft
                && player.distanceToSqr(aircraft) < 512.0 * 512.0) {
            sendToPlayer(serverPlayer, statusOf(aircraft));
        }
    }

    /** Upstream {@code PacketStatusResponse#setParameter}. */
    public static AircraftStatusResponsePayload statusOf(AircraftEntity aircraft) {
        byte seatNum = (byte) (aircraft.getSeatNum() + 1);
        byte[] weapons = new byte[Math.max(seatNum, 1)];
        for (int i = 0; i < weapons.length; i++) {
            weapons[i] = (byte) aircraft.weapons().getWeaponIDBySeatID(i);
        }
        return new AircraftStatusResponsePayload(aircraft.getId(), seatNum, weapons);
    }

    /** Upstream {@code PacketStatusResponse#onReceive}: every seat's selected weapon. */
    static void handleStatusResponse(AircraftStatusResponsePayload p, IPayloadContext context) {
        if (p.seatNum() <= 0 || p.weaponIds().length != p.seatNum()) {
            LOGGER.debug("Vehicle status response for entity {} has a bad seat count {}", p.entityId(), p.seatNum());
            return;
        }
        if (context.player().level().getEntity(p.entityId()) instanceof AircraftEntity ac) {
            for (int i = 0; i < p.seatNum(); i++) {
                ac.weapons().updateWeaponID(i, p.weaponIds()[i]);
            }
        }
    }
}
