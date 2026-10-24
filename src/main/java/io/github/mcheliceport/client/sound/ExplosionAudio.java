package io.github.mcheliceport.client.sound;

import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.net.ExplosionSoundPayload;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Bounded distance-delayed crack/tail; clears on world changes and never doubles server audio. */
@EventBusSubscriber(modid="mcheli_ce_port", value=Dist.CLIENT)
public final class ExplosionAudio {
    private record Pending(long tick, SimpleSoundInstance sound) {}
    private static final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private static WeakReference<ClientLevel> world = new WeakReference<>(null);
    private static long admissionTick = Long.MIN_VALUE;
    private static int admitted;
    private ExplosionAudio() {}

    public static void play(ExplosionSoundPayload p) {
        var mc = Minecraft.getInstance(); var level = mc.level;
        if (level == null || !Float.isFinite(p.power())) return;
        prepare(level);
        double distance = mc.gameRenderer.mainCamera().position().distanceToSqr(p.x(), p.y(), p.z());
        if (!Double.isFinite(distance) || distance > 64 * 64) return;
        long now = level.getGameTime();
        if (admissionTick != now) { admissionTick = now; admitted = 0; }
        // At most four voices admitted per tick and 32 awaiting their arrival.
        boolean enhanced = EffectQuality.enhanced();
        if (admitted >= 4 || pending.size() >= 32) return;
        int arrival = enhanced ? ExplosionAudioLook.arrival(Math.sqrt(distance)) : 0;
        float pitch = enhanced ? 0.95F : Float.isFinite(p.pitch()) ? Math.clamp(p.pitch(), 0.5F, 2) : 0.7F;
        add(p, now + arrival, 4, pitch);
        if (enhanced && p.power() >= 3 && admitted < 4 && pending.size() < 32)
            add(p, now + arrival + ExplosionAudioLook.tailDelay(p.power()), 2.4F,
                    ExplosionAudioLook.tailPitch(p.power()));
    }
    private static void add(ExplosionSoundPayload p, long tick, float volume, float pitch) {
        pending.add(new Pending(tick, new SimpleSoundInstance(SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.BLOCKS, volume, pitch, RandomSource.create(), p.x(), p.y(), p.z())));
        admitted++;
    }
    private static void prepare(@org.jspecify.annotations.Nullable ClientLevel level) {
        if (world.get() != level) {
            pending.clear(); world = new WeakReference<>(level); admissionTick = Long.MIN_VALUE; admitted = 0;
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance(); prepare(mc.level);
        if (mc.level == null || mc.isPaused()) return;
        long now = mc.level.getGameTime();
        pending.removeIf(p -> { if (p.tick > now) return false; mc.getSoundManager().play(p.sound); return true; });
    }
}
