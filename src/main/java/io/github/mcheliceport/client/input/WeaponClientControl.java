package io.github.mcheliceport.client.input;

import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.content.aircraft.CrewRole;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.aircraft.CrewRoles;
import io.github.mcheliceport.net.AirburstDistPayload;
import io.github.mcheliceport.net.CrewMarkPayload;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.WeaponControlPayload;
import io.github.mcheliceport.util.McheliSounds;
import io.github.mcheliceport.weapon.WeaponSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * A rider's weapon keys, read once per client tick: upstream
 * {@code MCH_AircraftClientTickHandler#handleCombatSystems}. The client predicts the shot
 * ({@link io.github.mcheliceport.entity.aircraft.AircraftWeapons#prepareCurrentWeapon}) and tells
 * the server, which fires.
 *
 * <p>Vanilla's attack and use are held back while riding a seat with a weapon (decision D21), and
 * the mouse wheel switches weapons instead of the hotbar when {@code switchWeaponWithMouseWheel} is
 * on. Upstream played a refusal sound for each of flare, chaff and APS on their shared default key;
 * here one sounds only when none of the vehicle's countermeasures could be used.
 */
public final class WeaponClientControl {

    private static int mouseWheel;

    private WeaponClientControl() {
    }

    /** Runs for any ridden vehicle, after the vehicle kind's own controls. */
    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        AircraftEntity ac = player != null ? ModNetwork.riddenAircraft(player) : null;
        if (ac == null || ac.getAcInfo() == null || mc.gui.screen() != null) {
            mouseWheel = 0;
            return;
        }
        WeaponControlPayload pc = handleCombatSystems(player, ac);
        if (pc != null && !pc.isEmpty()) {
            ClientPacketDistributor.sendToServer(pc);
        }
    }

    private static WeaponControlPayload handleCombatSystems(LocalPlayer player, AircraftEntity ac) {
        WeaponControlPayload pc = WeaponControlPayload.none(ac.getId());
        if (ac.isDestroyed()) {
            return pc;
        }
        int sid = ac.getSeatIdByEntity(player);
        CrewRole role = CrewRoles.roleOf(ac, player);
        if (ModKeyMappings.MARK_TARGET.consumeClick()) {
            if (role == CrewRole.COMMANDER) {
                ClientPacketDistributor.sendToServer(new CrewMarkPayload(ac.getId()));
            } else {
                playSoundNg();
            }
        }
        boolean flareKey = ModKeyMappings.FLARE.consumeClick();
        boolean chaffKey = ModKeyMappings.CHAFF.consumeClick();
        boolean apsKey = ModKeyMappings.APS.consumeClick();
        boolean ecmKey = ModKeyMappings.ECM_JAMMER.consumeClick();
        if ((flareKey || chaffKey || apsKey || ecmKey) && sid <= 1) {
            boolean used = false;
            boolean tried = false;
            if (flareKey && ac.haveFlare()) {
                tried = true;
                if (ac.canUseFlare() && ac.useFlare(ac.getCurrentFlareType())) {
                    pc = pc.withFlare(ac.getCurrentFlareType());
                    ac.nextFlareType();
                    used = true;
                }
            }
            if (chaffKey && ac.getAcInfo().haveChaff()) {
                tried = true;
                if (ac.canUseChaff() && ac.useChaff()) {
                    pc = pc.withChaff();
                    used = true;
                }
            }
            if (apsKey && ac.haveAPS()) {
                tried = true;
                if (ac.canUseAPS() && ac.useAPS()) {
                    pc = pc.withAps();
                    used = true;
                }
            }
            if (ecmKey && ac.haveECMJammer()) {
                tried = true;
                if (ac.useECMJammer()) {
                    pc = pc.withEcm();
                    used = true;
                }
            }
            if (!used || !tried) {
                playSoundNg();
            }
        }

        boolean switch1 = ModKeyMappings.SWITCH_WEAPON_1.consumeClick();
        boolean switch2 = ModKeyMappings.SWITCH_WEAPON_2.consumeClick();
        boolean modeKey = ModKeyMappings.SWITCH_WEAPON_MODE.consumeClick();
        boolean reloadKey = ModKeyMappings.RELOAD_WEAPON.consumeClick();
        boolean airburstKey = ModKeyMappings.AIRBURST_RESET.consumeClick();
        int wheel = mouseWheel;
        mouseWheel = 0;
        if (!ac.isPilotReloading()) {
            if (reloadKey) {
                pc = pc.withReload();
            }
            if (wheel == 0 && !switch1 && !switch2) {
                if (modeKey) {
                    ac.weapons().switchCurrentWeaponMode(player);
                } else if (ModKeyMappings.USE_WEAPON.isDown() && (role == null || role.canFire())
                        && ac.weapons().prepareCurrentWeapon(player)) {
                    WeaponSet weapon = ac.getCurrentWeapon(player);
                    pc = pc.withUseWeapon(weapon.getOptionParm1(), weapon.getOptionParm2(), ac.xo, ac.yo, ac.zo,
                            ac.weapons().getWeaponUserYaw(player), ac.weapons().getWeaponUserPitch(player));
                }
            } else {
                int next = ac.weapons().getNextWeaponID(player, wheel > 0 ? -1 : 1);
                ac.weapons().switchWeapon(player, next);
                pc = pc.withSwitchWeapon(next);
            }
        }

        if (airburstKey) {
            WeaponSet ws = ac.getCurrentWeapon(player);
            if (ws.getInfo() != null && ws.getInfo().canAirburst) {
                int dist = ac.weapons().resetAirburstDistance(player, ws.getCurrentWeapon());
                if (dist >= 0) {
                    ClientPacketDistributor.sendToServer(new AirburstDistPayload(ac.getId(), dist));
                }
            }
        }
        return pc;
    }

    /** Whether the local player rides a seat that has a weapon selected. */
    private static boolean ridesWeaponSeat() {
        LocalPlayer player = Minecraft.getInstance().player;
        AircraftEntity ac = player != null ? ModNetwork.riddenAircraft(player) : null;
        return ac != null && ac.getAcInfo() != null && ac.getCurrentWeaponID(player) >= 0;
    }

    /** Decision D21: no punching or using items from a weapon seat. */
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if ((event.isAttack() || event.isUseItem()) && ridesWeaponSeat()) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    /** Upstream {@code SwitchWeaponWithMouseWheel}: the wheel picks the weapon, not the hotbar slot. */
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (!ClientConfig.SWITCH_WEAPON_WITH_MOUSE_WHEEL.get() || Minecraft.getInstance().gui.screen() != null
                || !ridesWeaponSeat()) {
            return;
        }
        double dy = event.getScrollDeltaY();
        if (dy != 0.0) {
            mouseWheel += dy > 0.0 ? 1 : -1;
            event.setCanceled(true);
        }
    }

    private static void playSoundNg() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(McheliSounds.event("ng"), 1.0F));
    }
}
