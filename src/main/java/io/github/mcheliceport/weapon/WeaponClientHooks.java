package io.github.mcheliceport.weapon;

import io.github.mcheliceport.content.weapon.CartridgeInfo;
import io.github.mcheliceport.net.ClientSoundPayload;
import io.github.mcheliceport.net.LaserShotPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What the weapon code asks of the client: upstream's direct calls into client classes
 * ({@code MCH_PlayerViewHandler.applyRecoil}, {@code W_McClient.playSound}, the cartridge
 * entity, the render view position). The client registers its implementation; on a server every
 * call does nothing.
 */
public final class WeaponClientHooks {

    /** The client's side of the weapon code. Only called on the client thread. */
    public interface Hooks {
        default void vehicleBlood(io.github.mcheliceport.net.VehicleBloodPayload payload) {}
        default void weaponHeat(net.minecraft.world.entity.Entity shooter,int mount,io.github.mcheliceport.content.weapon.WeaponInfo info,Vec3 position,float yaw,float pitch){}
        default void vehicleImpact(io.github.mcheliceport.net.VehicleImpactPayload payload) {}
        /** Cosmetic shot details; the existing client shot state supplies authority and timing. */
        default void weaponShot(Level level, Vec3 position, Vec3 motion, io.github.mcheliceport.content.weapon.WeaponInfo info,
                float yaw, float pitch) {}
        /** Upstream {@code MCH_PlayerViewHandler#applyRecoil}: kicks the local player's view. */
        void applyRecoil(float pitch, float yaw, float recoverFactor);

        /** Upstream {@code W_McClient#playSoundClick}. */
        void playSoundClick(float volume, float pitch);

        /** Upstream {@code W_McClient#playSound}: a McHeli sound at the local player, heard only here. */
        void playSound(String name, float volume, float pitch);

        /** Upstream {@code MCH_EntityCartridge#spawnCartridge}. */
        void spawnCartridge(Level level, CartridgeInfo cartridge, double x, double y, double z, double mx,
                double my, double mz, float yaw, float pitch);

        /** The render view position (upstream {@code RenderManager.viewerPos}), for laser designation. */
        Vec3 viewPosition();

        /** The local player. */
        @Nullable Entity localPlayer();

        /** Starts the railgun's charge sound, if it is not playing. Returns a handle to stop it with. */
        @Nullable Object playChargeSound(String name, double x, double y, double z);

        /** Stops a sound {@link #playChargeSound} started. */
        void stopSound(Object handle);

        /** Upstream {@code MCH_ClientProxy#hitBullet}: the hit marker outside a vehicle. */
        void hitMarker();

        /** Upstream {@code PacketLaserShot#onReceive}: draws a laser beam. */
        void laserShot(LaserShotPayload payload);

        /** Upstream {@code PacketClientSound#onReceive}: a sound with distance delay and distortion. */
        void clientSound(ClientSoundPayload payload);

        default void explosionSound(io.github.mcheliceport.net.ExplosionSoundPayload payload) {}
    }

    private static volatile @Nullable Hooks hooks;

    private WeaponClientHooks() {
    }

    public static void set(@Nullable Hooks value) {
        hooks = value;
    }

    /** The client's hooks, or null on a dedicated server. */
    public static @Nullable Hooks get() {
        return hooks;
    }

    public static void applyRecoil(float pitch, float yaw, float recoverFactor) {
        Hooks h = hooks;
        if (h != null) {
            h.applyRecoil(pitch, yaw, recoverFactor);
        }
    }

    public static void playSoundClick(float volume, float pitch) {
        Hooks h = hooks;
        if (h != null) {
            h.playSoundClick(volume, pitch);
        }
    }

    public static void playSound(String name, float volume, float pitch) {
        Hooks h = hooks;
        if (h != null && name != null && !name.isEmpty()) {
            h.playSound(name, volume, pitch);
        }
    }

    public static void spawnCartridge(Level level, @Nullable CartridgeInfo cartridge, double x, double y, double z,
            double mx, double my, double mz, float yaw, float pitch) {
        Hooks h = hooks;
        if (h != null && cartridge != null && level.isClientSide()) {
            h.spawnCartridge(level, cartridge, x, y, z, mx, my, mz, yaw, pitch);
        }
    }

    public static void weaponHeat(net.minecraft.world.entity.Entity shooter,int mount,io.github.mcheliceport.content.weapon.WeaponInfo info,Vec3 position,float yaw,float pitch){
        Hooks h=hooks;if(h!=null&&shooter.level().isClientSide())h.weaponHeat(shooter,mount,info,position,yaw,pitch);
    }
    public static void weaponShot(Level level, Vec3 position, Vec3 motion, io.github.mcheliceport.content.weapon.WeaponInfo info,
            float yaw, float pitch) {
        Hooks h = hooks;
        if (h != null && level.isClientSide()) h.weaponShot(level, position, motion, info, yaw, pitch);
    }

    public static @Nullable Vec3 viewPosition() {
        Hooks h = hooks;
        return h != null ? h.viewPosition() : null;
    }

    public static @Nullable Entity localPlayer() {
        Hooks h = hooks;
        return h != null ? h.localPlayer() : null;
    }

    public static @Nullable Object playChargeSound(String name, double x, double y, double z) {
        Hooks h = hooks;
        return h != null ? h.playChargeSound(name, x, y, z) : null;
    }

    public static void stopSound(@Nullable Object handle) {
        Hooks h = hooks;
        if (h != null && handle != null) {
            h.stopSound(handle);
        }
    }

    public static void hitMarker() {
        Hooks h = hooks;
        if (h != null) {
            h.hitMarker();
        }
    }

    public static void laserShot(LaserShotPayload payload) {
        Hooks h = hooks;
        if (h != null) {
            h.laserShot(payload);
        }
    }

    public static void clientSound(ClientSoundPayload payload) {
        Hooks h = hooks;
        if (h != null) {
            h.clientSound(payload);
        }
    }
}
