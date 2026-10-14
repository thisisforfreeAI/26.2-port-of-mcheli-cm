package io.github.mcheliceport.content.weapon;

import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import org.jspecify.annotations.Nullable;

/**
 * A status effect a weapon applies on hit.
 *
 * <p>Ported from upstream {@code com.norwood.mcheli.MCH_PotionEffect}, which held a resolved 1.12
 * {@code PotionEffect}. The port stores the effect ID (for example {@code minecraft:slowness}) and
 * resolves it against the registry when the effect is applied, so content parses without a running
 * game. Upstream created its effects as non-ambient with particles shown.
 *
 * @param effectId  mob effect ID
 * @param duration  duration in ticks
 * @param amplifier effect level minus one
 * @param startDist distance from the impact where the effect starts to apply
 * @param endDist   distance from the impact where it stops applying
 */
public record PotionEffectInfo(String effectId, int duration, int amplifier, int startDist, int endDist) {

    /** 1.12 numeric potion IDs 1-27, as used by legacy TXT files, by their effect IDs. */
    private static final @Nullable String[] LEGACY_IDS = {
            null, "minecraft:speed", "minecraft:slowness", "minecraft:haste", "minecraft:mining_fatigue",
            "minecraft:strength", "minecraft:instant_health", "minecraft:instant_damage", "minecraft:jump_boost",
            "minecraft:nausea", "minecraft:regeneration", "minecraft:resistance", "minecraft:fire_resistance",
            "minecraft:water_breathing", "minecraft:invisibility", "minecraft:blindness", "minecraft:night_vision",
            "minecraft:hunger", "minecraft:weakness", "minecraft:poison", "minecraft:wither",
            "minecraft:health_boost", "minecraft:absorption", "minecraft:saturation", "minecraft:glowing",
            "minecraft:levitation", "minecraft:luck", "minecraft:unluck"
    };

    /** Returns the effect ID for a 1.12 numeric potion ID, or null if there was no such potion. */
    public static @Nullable String fromLegacyId(int id) {
        return id > 0 && id < LEGACY_IDS.length ? LEGACY_IDS[id] : null;
    }

    /** A fresh effect instance (non-ambient, particles shown), or empty when the ID names no effect. */
    public Optional<MobEffectInstance> toInstance() {
        Identifier id = Identifier.tryParse(this.effectId);
        if (id == null) {
            return Optional.empty();
        }
        return BuiltInRegistries.MOB_EFFECT.get(id)
                .map(effect -> new MobEffectInstance(effect, this.duration, this.amplifier, false, true));
    }
}
