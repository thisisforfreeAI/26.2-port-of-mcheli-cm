package io.github.mcheliceport.client.sound;

import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.net.ExplosionSoundPayload;
import java.lang.ref.WeakReference;
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
    private record Voice(ExplosionSoundPayload payload, float volume, float pitch) {}
    private static final ExplosionAudioQueue<Voice> pending = new ExplosionAudioQueue<>();
    private static WeakReference<ClientLevel> world = new WeakReference<>(null);
    private ExplosionAudio() {}

    public static void play(ExplosionSoundPayload p) {
        var mc = Minecraft.getInstance(); var level = mc.level;
        if (level == null || !Float.isFinite(p.power())) return;
        prepare(level);
        double distance = mc.gameRenderer.mainCamera().position().distanceToSqr(p.x(), p.y(), p.z());
        if (!Double.isFinite(distance) || distance > 64 * 64) return;
        long now = level.getGameTime();
        boolean enhanced = EffectQuality.enhanced();
        int arrival = enhanced ? ExplosionAudioLook.arrival(Math.sqrt(distance)) : 0;
        float pitch = enhanced ? 0.95F : Float.isFinite(p.pitch()) ? Math.clamp(p.pitch(), 0.5F, 2) : 0.7F;
        if (!pending.offer(now, now + arrival, true, new Voice(p, 4, pitch))) return;
        if (enhanced && p.power() >= 3)
            pending.offer(now, now + arrival + ExplosionAudioLook.tailDelay(p.power()), false,
                    new Voice(p, 2.4F, ExplosionAudioLook.tailPitch(p.power())));
    }
    private static void playVoice(Voice voice) {
        var p = voice.payload;
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.BLOCKS, voice.volume, voice.pitch, RandomSource.create(), p.x(), p.y(), p.z()));
    }
    private static void prepare(@org.jspecify.annotations.Nullable ClientLevel level) {
        if (world.get() != level) {
            pending.clear(); world = new WeakReference<>(level);
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance(); prepare(mc.level);
        if (mc.level == null || mc.isPaused()) return;
        long now = mc.level.getGameTime();
        pending.drain(now, ExplosionAudio::playVoice);
    }
}
