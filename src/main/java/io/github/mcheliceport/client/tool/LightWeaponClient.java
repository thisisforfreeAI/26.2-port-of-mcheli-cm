package io.github.mcheliceport.client.tool;

import io.github.mcheliceport.client.input.ModKeyMappings;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.ServerSettings;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.gltd.GltdEntity;
import io.github.mcheliceport.item.LightWeaponItem;
import io.github.mcheliceport.net.LightWeaponControlPayload;
import io.github.mcheliceport.weapon.WeaponBase;
import io.github.mcheliceport.weapon.WeaponClientHooks;
import io.github.mcheliceport.weapon.WeaponCreator;
import io.github.mcheliceport.weapon.guidance.WeaponGuidanceSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * The client side of the hand-held Stinger and Javelin (upstream
 * {@code MCH_ClientLightWeaponTickHandler}). Aiming (holding use for more than ten ticks) zooms
 * and locks on to what is in view; the attack key fires once the lock is complete. An empty
 * launcher reloads itself after two seconds when a missile is in the inventory and the player is
 * not aiming. The zoom key cycles the zoom, the camera-mode key switches night vision (after three
 * seconds of aiming) and the weapon-mode key switches the Javelin between direct and top attack.
 * These are the vehicle keys (Z, C and X by default), read here although the player is not riding.
 */
public final class LightWeaponClient {

    /** Upstream: top attack needs a target at least 40 blocks away. */
    public static final double TOP_ATTACK_MIN_DIST = 40.0;
    private static final int RELOAD_TICKS = 40;

    private static int reloadCount;
    private static int lockonSoundCount;
    private static int weaponMode;
    private static int selectedZoom;
    private static WeaponGuidanceSystem gs = defaultGuidance();
    private static @Nullable WeaponBase weapon;
    private static boolean heldItem;
    private static @Nullable Player prevPlayer;
    private static Item prevItem = net.minecraft.world.item.Items.AIR;
    private static final Edge MODE = new Edge();
    private static final Edge ATTACK = new Edge();
    private static final Edge ZOOM = new Edge();
    private static final Edge CAMERA = new Edge();

    private LightWeaponClient() {
    }

    private static WeaponGuidanceSystem defaultGuidance() {
        WeaponGuidanceSystem g = new WeaponGuidanceSystem();
        g.setLockCountMax(40);
        g.lockRange = 120.0;
        return g;
    }

    public static WeaponGuidanceSystem guidance() {
        return gs;
    }

    public static @Nullable WeaponBase weapon() {
        return weapon;
    }

    public static int weaponMode() {
        return weaponMode;
    }

    public static int selectedZoom() {
        return selectedZoom;
    }

    public static void reset() {
        initWeaponParam();
        weapon = null;
        gs = defaultGuidance();
        heldItem = false;
        prevPlayer = null;
        prevItem = net.minecraft.world.item.Items.AIR;
    }

    private static void initWeaponParam() {
        reloadCount = 0;
        weaponMode = 0;
        selectedZoom = 0;
    }

    /** Upstream: nothing happens at the designator or in a vehicle's own seat. */
    public static boolean canUse(@Nullable Player player) {
        return player != null && !(player.getVehicle() instanceof GltdEntity)
                && !(player.getVehicle() instanceof AircraftEntity);
    }

    /** Whether the sight is up: aiming a light weapon, not at the designator or flying. */
    public static boolean isAiming(@Nullable Player player) {
        return canUse(player) && LightWeaponItem.isHeld(player);
    }

    /** Client tick. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean inGui = mc.gui.screen() != null;
        boolean keys = player != null && !inGui;
        boolean modeDown = MODE.update(keys && RangeFinderClient.physicallyDown(ModKeyMappings.SWITCH_WEAPON_MODE));
        boolean attackHeld = keys && RangeFinderClient.physicallyDown(ModKeyMappings.USE_WEAPON);
        boolean attackDown = ATTACK.update(attackHeld);
        boolean zoomDown = ZOOM.update(keys && RangeFinderClient.physicallyDown(ModKeyMappings.ZOOM));
        boolean cameraDown = CAMERA.update(keys && RangeFinderClient.physicallyDown(ModKeyMappings.CAMERA_MODE));
        boolean wasHeld = heldItem;
        if (prevPlayer != player) {
            initWeaponParam();
            prevPlayer = player;
        }
        ItemStack is = player != null && canUse(player) ? player.getMainHandItem() : ItemStack.EMPTY;
        if (is.getItem() instanceof LightWeaponItem lweapon) {
            if (prevItem != is.getItem()) {
                initWeaponParam();
                weapon = WeaponCreator.createWeapon(player.level(), LightWeaponItem.weaponName(is), Vec3.ZERO, 0.0F,
                        0.0F, null, false);
                if (weapon != null && weapon.getGuidanceSystem() != null) {
                    gs = weapon.getGuidanceSystem();
                }
            }
            if (weapon == null) {
                prevItem = is.getItem();
                return;
            }
            gs.setWorld(player.level());
            gs.lockRange = ServerSettings.client().stingerLockRange();
            selectedZoom = selectedZoom % weapon.getInfo().zoom.length;
            if (LightWeaponItem.isLoaded(is)) {
                if (player.getTicksUsingItem() > LightWeaponItem.AIM_TICKS) {
                    gs.lock(player);
                    if (gs.getLockCount() > 0) {
                        if (lockonSoundCount > 0) {
                            lockonSoundCount--;
                        } else {
                            lockonSoundCount = (int) (7 * (1.0 - (float) gs.getLockCount() / gs.getLockCountMax()));
                            if (lockonSoundCount < 3) {
                                lockonSoundCount = 2;
                            }
                            WeaponClientHooks.playSound("lockon", 1.0F, 1.0F);
                        }
                    }
                } else {
                    gs.clearLock();
                }
                reloadCount = 0;
            } else {
                lockonSoundCount = 0;
                if (LightWeaponItem.hasBullet(player, lweapon.bullet()) && !player.isUsingItem()) {
                    if (reloadCount == 10) {
                        WeaponClientHooks.playSound("fim92_reload", 1.0F, 1.0F);
                    }
                    if (reloadCount < RELOAD_TICKS) {
                        reloadCount++;
                        if (reloadCount == RELOAD_TICKS) {
                            ClientPacketDistributor.sendToServer(
                                    new LightWeaponControlPayload(false, 0, 0, true, (byte) 0));
                        }
                    }
                } else {
                    reloadCount = 0;
                }
                gs.clearLock();
            }
            if (!inGui) {
                control(player, is, modeDown, attackHeld, attackDown, zoomDown, cameraDown);
            }
            heldItem = LightWeaponItem.isHeld(player);
        } else {
            lockonSoundCount = 0;
            reloadCount = 0;
            heldItem = false;
        }
        if (wasHeld && !heldItem && player != null) {
            // Upstream: putting the weapon down switches off the night vision it gave.
            MobEffectInstance nv = player.getEffect(MobEffects.NIGHT_VISION);
            if (nv == null || nv.getDuration() < 250) {
                ClientPacketDistributor.sendToServer(new LightWeaponControlPayload(false, 0, 0, false,
                        LightWeaponControlPayload.NIGHT_VISION_OFF));
                player.removeEffect(MobEffects.NIGHT_VISION);
            }
        }
        prevItem = is.getItem();
        gs.update();
    }

    /** Upstream {@code playerControl}. */
    private static void control(LocalPlayer player, ItemStack is, boolean modeDown, boolean attackHeld,
            boolean attackDown, boolean zoomDown, boolean cameraDown) {
        WeaponBase w = weapon;
        boolean useWeapon = false;
        int targetId = 0;
        byte cameraMode = 0;
        boolean autoShot = ClientConfig.get(ClientConfig.LIGHT_WEAPON_AUTO_FIRE) && LightWeaponItem.isLoaded(is)
                && gs.isLockComplete();
        if (modeDown && w.numMode > 1) {
            weaponMode = (weaponMode + 1) % w.numMode;
            WeaponClientHooks.playSound("pi", 0.5F, 0.9F);
        }
        if (attackHeld || autoShot) {
            boolean result = false;
            if (LightWeaponItem.isLoaded(is) && gs.isLockComplete()) {
                if (canFire(player)) {
                    useWeapon = true;
                    targetId = gs.lastLockEntity != null ? gs.lastLockEntity.getId() : 0;
                    gs.clearLock();
                    result = true;
                }
            }
            if (attackDown && !result && player.getTicksUsingItem() > 5) {
                WeaponClientHooks.playSound("ng", 1.0F, 1.0F);
            }
        }
        if (zoomDown) {
            int prev = selectedZoom;
            selectedZoom = (selectedZoom + 1) % w.getInfo().zoom.length;
            if (prev != selectedZoom) {
                WeaponClientHooks.playSound("zoom", 0.5F, 1.0F);
            }
        }
        if (cameraDown) {
            if (player.getEffect(MobEffects.NIGHT_VISION) != null) {
                player.removeEffect(MobEffects.NIGHT_VISION);
                cameraMode = LightWeaponControlPayload.NIGHT_VISION_OFF;
                WeaponClientHooks.playSound("pi", 0.5F, 0.9F);
            } else if (player.getTicksUsingItem() > LightWeaponItem.NIGHT_VISION_TICKS) {
                cameraMode = LightWeaponControlPayload.NIGHT_VISION_ON;
                WeaponClientHooks.playSound("pi", 0.5F, 0.9F);
            } else {
                WeaponClientHooks.playSound("ng", 1.0F, 1.0F);
            }
        }
        if (useWeapon || cameraMode != 0) {
            ClientPacketDistributor.sendToServer(new LightWeaponControlPayload(useWeapon, targetId, weaponMode,
                    false, cameraMode));
        }
    }

    /** Upstream: in top attack (mode 1) the target must be at least 40 blocks away across the ground. */
    public static boolean canFire(Player player) {
        Entity target = gs.getTargetEntity();
        return weaponMode == 0 || target == null || horizontalDistance(player, target) >= TOP_ATTACK_MIN_DIST;
    }

    public static double horizontalDistance(Entity a, Entity b) {
        double dx = b.getX() - a.getX();
        double dz = b.getZ() - a.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Upstream {@code W_Reflection.setCameraZoom}: the sight's zoom while aiming. */
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        LocalPlayer player = Minecraft.getInstance().player;
        WeaponBase w = weapon;
        if (w != null && isAiming(player) && event.getCamera().entity() == player) {
            float[] zoom = w.getInfo().zoom;
            event.setFOV(event.getFOV() / zoom[selectedZoom % zoom.length]);
        }
    }

    /** Upstream {@code BuiltInLightWeaponItemRenderer}: no launcher in view while aiming in first person. */
    public static void onRenderHand(RenderHandEvent event) {
        if (isAiming(Minecraft.getInstance().player)) {
            event.setCanceled(true);
        }
    }

    /** Upstream {@code MCH_Key#isKeyDown}: true on the tick a key goes down. */
    private static final class Edge {
        private boolean before;

        boolean update(boolean now) {
            boolean down = now && !this.before;
            this.before = now;
            return down;
        }
    }
}
