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
import io.github.mcheliceport.entity.seat.SeatEntity;
import net.minecraft.server.MinecraftServer;
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
    /** Bump when a payload's encoding changes. */
    public static final String VERSION = "2";

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
        if (player.connection != null && player.connection.hasChannel(ServerSettingsPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, payload);
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
        boolean pilot = heli.isPilot(player);
        if (p.unmount() == HeliControlPayload.UnmountAction.UNMOUNT_SELF) {
            // Upstream always unmounted the pilot; only the pilot's client sent it.
            if (pilot) {
                heli.unmountEntity();
            } else {
                player.stopRiding();
            }
            return;
        }
        if (heli.isDestroyed()) {
            return;
        }
        if (pilot) {
            switch (p.bladeStatus()) {
                case UNFOLD -> {
                    if (heli.canSwitchFoldBlades() && heli.isFoldBlades()) {
                        heli.setFoldBladeStat(HeliEntity.BLADES_UNFOLDING);
                    }
                }
                case FOLD -> {
                    if (heli.canSwitchFoldBlades() && !heli.isFoldBlades()) {
                        heli.setFoldBladeStat(HeliEntity.BLADES_FOLDING);
                    }
                }
                default -> {
                }
            }
            switch (p.modeSwitch()) {
                case GUNNER_OFF -> heli.switchGunnerMode(false);
                case GUNNER_ON -> heli.switchGunnerMode(true);
                case HOVERING_OFF -> heli.switchHoveringMode(false);
                case HOVERING_ON -> heli.switchHoveringMode(true);
                default -> {
                }
            }
            PlayerControlSwitches s = p.switches();
            heli.throttleUp = s.throttleUp();
            heli.throttleDown = s.throttleDown();
            heli.moveLeft = s.moveLeft();
            heli.moveRight = s.moveRight();
            if (p.hatch() != HeliControlPayload.HatchSwitch.NONE && heli.getAcInfo() != null
                    && heli.getAcInfo().haveHatch()) {
                heli.foldHatch(p.hatch() == HeliControlPayload.HatchSwitch.UNFOLD);
            }
            if (p.switchFreeLook() > 0) {
                heli.switchFreeLookMode(p.switchFreeLook() == 1);
            }
            switch (p.gear()) {
                case FOLD -> heli.foldLandingGear();
                case UNFOLD -> heli.unfoldLandingGear();
                default -> {
                }
            }
        }
        if (p.switches().openGui()) {
            heli.openContainer(player);
        }
        if (p.switches().switchGunnerStatus()) {
            heli.setGunnerStatus(!heli.getGunnerStatus());
        }
        if (p.switchCameraMode() > 0 && heli.canSwitchCameraMode(heli.getSeatIdByEntity(player))) {
            heli.switchCameraMode(player, p.switchCameraMode() - 1);
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
        if (!(player.getVehicle() instanceof HeliEntity heli) || heli.getId() != p.entityId()
                || !heli.isPilot(player) || !heli.followsPilot()) {
            return;
        }
        applyPilotMotion(heli, p);
    }

    /** Takes the pilot's velocity and throttle, limited to what the heli can do. */
    public static void applyPilotMotion(HeliEntity heli, AircraftMotionPayload p) {
        if (!Double.isFinite(p.motionX()) || !Double.isFinite(p.motionY()) || !Double.isFinite(p.motionZ())
                || !Float.isFinite(p.throttle())) {
            return;
        }
        double limit = heli.getMaxSpeedForChecks();
        double horizontal = Math.sqrt(p.motionX() * p.motionX() + p.motionZ() * p.motionZ());
        double f = horizontal > limit ? limit / horizontal : 1.0;
        heli.setDeltaMovement(p.motionX() * f, Mth.clamp(p.motionY(), -10.0, 1.0), p.motionZ() * f);
        heli.setCurrentThrottle(heli.canUseFuel(true) || heli.getCurrentThrottle() >= p.throttle()
                ? Mth.clamp(p.throttle(), 0.0, 1.0) : heli.getCurrentThrottle());
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
            PacketDistributor.sendToPlayer(serverPlayer, statusOf(aircraft));
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
