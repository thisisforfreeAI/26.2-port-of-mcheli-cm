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
    /** Bump when payload encoding or menu slot layout changes. */
    public static final String VERSION = "5";

    private ModNetwork() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ModNetwork::registerPayloads);
        modBus.addListener(ModNetwork::onConfigReloaded);
        NeoForge.EVENT_BUS.addListener(ModNetwork::onPlayerLoggedIn);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(HeliControlPayload.TYPE, HeliControlPayload.CODEC, ModNetwork::handleHeliControl);
        registrar.playToServer(PlaneControlPayload.TYPE, PlaneControlPayload.CODEC, ModNetwork::handlePlaneControl);
        registrar.playToServer(ShipControlPayload.TYPE, ShipControlPayload.CODEC, ModNetwork::handleShipControl);
        registrar.playToServer(AircraftAttitudePayload.TYPE, AircraftAttitudePayload.CODEC,
                ModNetwork::handleAttitude);
        registrar.playToServer(AircraftMotionPayload.TYPE, AircraftMotionPayload.CODEC, ModNetwork::handleMotion);
        registrar.playToServer(SeatControlPayload.TYPE, SeatControlPayload.CODEC, ModNetwork::handleSeatControl);
        registrar.playToServer(AircraftStatusRequestPayload.TYPE, AircraftStatusRequestPayload.CODEC,
                ModNetwork::handleStatusRequest);
        registrar.playToClient(AircraftStatusResponsePayload.TYPE, AircraftStatusResponsePayload.CODEC,
                ModNetwork::handleStatusResponse);
        registrar.playToClient(ExplosionEffectPayload.TYPE, ExplosionEffectPayload.CODEC,
                (p, context) -> McheliEffects.explosionEffects(context.player().level(), p.x(), p.y(), p.z(),
                        p.size(), p.inWater(), p.affectedBlocks()));
        registrar.playToClient(ServerSettingsPayload.TYPE, ServerSettingsPayload.CODEC,
                (payload, context) -> ServerSettings.setClient(payload.toValues()));
        registrar.playToServer(WeaponControlPayload.TYPE, WeaponControlPayload.CODEC, ModNetwork::handleWeaponControl);
        registrar.playToServer(AmmoRequestPayload.TYPE, AmmoRequestPayload.CODEC, ModNetwork::handleAmmoRequest);
        registrar.playToServer(ResupplyPayload.TYPE, ResupplyPayload.CODEC, ModNetwork::handleResupply);
        registrar.playToServer(AirburstDistPayload.TYPE, AirburstDistPayload.CODEC, ModNetwork::handleAirburstDist);
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
            server.execute(() -> {
                ServerSettingsPayload payload = ServerSettingsPayload.of(ServerSettings.server());
                server.getPlayerList().getPlayers().forEach(player -> sendSettings(player, payload));
            });
        }
    }

    // ---- client to server ----

    /** The vehicle a player rides, in seat 0 or a crew seat (upstream's lookup in {@code onReceive}). */
    public static @Nullable AircraftEntity riddenAircraft(Player player) {
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof AircraftEntity aircraft) {
            return aircraft;
        }
        if (vehicle instanceof SeatEntity seat) {
            return seat.getParent();
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
            // Upstream always unmounted the pilot; only the pilot's client sent it.
            if (pilot) {
                aircraft.unmountEntity();
            } else if (!aircraft.parachuteCrew(player)) {
                player.stopRiding();
            }
            return;
        }
        if (aircraft.isDestroyed()) {
            return;
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
        if (p.switches().switchGunnerStatus()) {
            aircraft.setGunnerStatus(!aircraft.getGunnerStatus());
        }
        if (p.switchCameraMode() > 0 && aircraft.canSwitchCameraMode(aircraft.getSeatIdByEntity(player))) {
            aircraft.switchCameraMode(player, p.switchCameraMode() - 1);
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
        Player player = context.player();
        if (!(player.getVehicle() instanceof AircraftEntity aircraft) || aircraft.getId() != p.entityId()
                || !aircraft.isPilot(player) || aircraft.isDestroyed()) {
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
        if (aircraft == null) {
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
        applyWeaponControl(aircraft, player, p);
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
        if (p.useWeapon() && Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z())
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
