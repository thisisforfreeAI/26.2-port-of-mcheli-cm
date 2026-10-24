package io.github.mcheliceport.client.weapon;

import io.github.mcheliceport.content.weapon.CartridgeInfo;
import io.github.mcheliceport.net.ClientSoundPayload;
import io.github.mcheliceport.net.LaserShotPayload;
import io.github.mcheliceport.util.McheliSounds;
import io.github.mcheliceport.weapon.WeaponClientHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The client's side of the weapon code ({@link WeaponClientHooks.Hooks}): the recoil kick on the
 * view (upstream {@code MCH_PlayerViewHandler}), UI and positioned sounds, the hit marker counter
 * and the hand-off to {@link WeaponEffects} for laser beams and cartridges.
 */
public final class WeaponClient implements WeaponClientHooks.Hooks {

    /** Upstream {@code MCH_GuiCommon.hitCount} when a hit lands. */
    public static final int HIT_COUNT = 15;

    private static float playerRecoilPitch;
    private static float playerRecoilYaw;
    private static float antiRecoilPitch;
    private static float antiRecoilYaw;
    private static float recoilControl = 0.8F;
    private static int hitCount;

    private WeaponClient() {
    }

    public static void register() {
        WeaponClientHooks.set(new WeaponClient());
    }

    /** Runs every client tick. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) {
            return;
        }
        if (hitCount > 0) {
            hitCount--;
        }
        updateRecoil(mc.player);
        WeaponEffects.tick(mc.level);
    }

    /** Upstream {@code MCH_PlayerViewHandler#onUpdate}. */
    private static void updateRecoil(@Nullable LocalPlayer player) {
        if (player == null) {
            return;
        }
        if (playerRecoilPitch > 0.0F) {
            playerRecoilPitch *= recoilControl;
        }
        if (playerRecoilPitch == 0.0F && playerRecoilYaw == 0.0F && antiRecoilPitch == 0.0F
                && antiRecoilYaw == 0.0F) {
            return;
        }
        float pitch = player.getXRot() - playerRecoilPitch;
        float yaw = player.getYRot() - playerRecoilYaw;
        antiRecoilPitch += playerRecoilPitch;
        antiRecoilYaw += playerRecoilYaw;
        player.setXRot(pitch + antiRecoilPitch * 0.2F);
        player.setYRot(yaw + antiRecoilYaw * 0.2F);
        antiRecoilPitch *= 0.8F;
        antiRecoilYaw *= 0.8F;
        playerRecoilYaw *= 0.8F;
        if (Math.abs(antiRecoilPitch) < 1.0E-4F && Math.abs(antiRecoilYaw) < 1.0E-4F
                && Math.abs(playerRecoilPitch) < 1.0E-4F && Math.abs(playerRecoilYaw) < 1.0E-4F) {
            antiRecoilPitch = antiRecoilYaw = playerRecoilPitch = playerRecoilYaw = 0.0F;
        }
    }

    /** The hit marker for hits outside a vehicle: 15 at a hit, counting down each tick. */
    public static int hitCount() {
        return hitCount;
    }

    /** Clears view kicks, beams and cartridges when leaving a world. */
    public static void reset() {
        playerRecoilPitch = playerRecoilYaw = antiRecoilPitch = antiRecoilYaw = 0.0F;
        hitCount = 0;
        WeaponEffects.clear();
    }

    @Override
    public void applyRecoil(float pitch, float yaw, float recoverFactor) {
        playerRecoilPitch += pitch;
        playerRecoilYaw += yaw;
        recoilControl = recoverFactor;
    }

    @Override
    public void playSoundClick(float volume, float pitch) {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, volume));
    }

    @Override
    public void playSound(String name, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(McheliSounds.event(name).value(), pitch, volume));
    }

    @Override
    public void spawnCartridge(Level level, CartridgeInfo cartridge, double x, double y, double z, double mx,
            double my, double mz, float yaw, float pitch) {
        WeaponEffects.spawnCartridge(level, cartridge, x, y, z, mx, my, mz, yaw, pitch);
    }

    @Override
    public void weaponShot(Level level, Vec3 position, Vec3 motion, io.github.mcheliceport.content.weapon.WeaponInfo info,
            float yaw, float pitch) {
        io.github.mcheliceport.client.particle.WeaponGroundEffects.shot(level, position, info, yaw, pitch);
        if (io.github.mcheliceport.config.EffectQuality.enhanced() && info.cartridge == null
                && ("machinegun1".equals(info.type) || "machinegun2".equals(info.type))) {
            WeaponEffects.spawnCartridge(level, new CartridgeInfo("cartridge", 0.18F, 90, -15, 0.35F, -0.04F, 0.35F),
                    position.x, position.y, position.z, motion.x, motion.y + 0.04, motion.z, yaw, pitch);
        }
    }

    @Override
    public Vec3 viewPosition() {
        return Minecraft.getInstance().gameRenderer.mainCamera().position();
    }

    @Override
    public @Nullable Entity localPlayer() {
        return Minecraft.getInstance().player;
    }

    @Override
    public @Nullable Object playChargeSound(String name, double x, double y, double z) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        SoundInstance sound = new SimpleSoundInstance(McheliSounds.event(name).value(), SoundSource.MASTER, 1.0F,
                1.0F, RandomSource.create(), x, y, z);
        Minecraft.getInstance().getSoundManager().play(sound);
        return sound;
    }

    @Override
    public void stopSound(Object handle) {
        if (handle instanceof SoundInstance sound) {
            Minecraft.getInstance().getSoundManager().stop(sound);
        }
    }

    @Override
    public void hitMarker() {
        hitCount = HIT_COUNT;
    }

    @Override
    public void laserShot(LaserShotPayload p) {
        WeaponEffects.addBeam(p.src(), p.dest(), p.argb(), p.width(), p.life(), p.pulsate(), p.renderStartDist());
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (!p.hitSomething() || level == null) {
            return;
        }
        // Upstream PacketWeaponLaserShooting#spawnImpactParticles.
        RandomSource rand = level.getRandom();
        Vec3 d = p.dest();
        for (int i = 0; i < 6; i++) {
            level.addParticle(ParticleTypes.CLOUD, d.x + (rand.nextFloat() - 0.5), d.y + 0.1,
                    d.z + (rand.nextFloat() - 0.5), rand.nextGaussian() * 0.02, Math.abs(rand.nextGaussian()) * 0.04,
                    rand.nextGaussian() * 0.02);
        }
        for (int i = 0; i < 4; i++) {
            level.addParticle(ParticleTypes.FLAME, d.x + (rand.nextFloat() - 0.5) * 0.5, d.y + 0.1,
                    d.z + (rand.nextFloat() - 0.5) * 0.5, 0.0, 0.02, 0.0);
        }
    }

    @Override
    public void explosionSound(io.github.mcheliceport.net.ExplosionSoundPayload p) {
        io.github.mcheliceport.client.sound.ExplosionAudio.play(p);
    }

    @Override
    public void clientSound(ClientSoundPayload p) {
        // Upstream PacketClientSound#onReceive.
        RandomSource rand = RandomSource.create();
        float pitch = (p.distort() ? 1.0F / (rand.nextFloat() * 0.4F + 0.8F) : 1.0F) * (p.silenced() ? 2.0F : 1.0F);
        float volume = p.silenced() ? 50.0F : 100.0F;
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(McheliSounds.event(p.sound()).value(),
                SoundSource.MASTER, volume, pitch, rand, p.x(), p.y(), p.z()));
    }
}
