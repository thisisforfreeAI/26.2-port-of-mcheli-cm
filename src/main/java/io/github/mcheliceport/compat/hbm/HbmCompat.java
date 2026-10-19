package io.github.mcheliceport.compat.hbm;

import com.mojang.logging.LogUtils;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.content.weapon.hbm.FireContainer;
import io.github.mcheliceport.content.weapon.hbm.MistContainer;
import io.github.mcheliceport.content.weapon.hbm.MukeContainer;
import io.github.mcheliceport.content.weapon.hbm.VNTSettingContainer;
import java.lang.reflect.Array;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/** Optional NTM Next bridge. HBM effects supplement the normal McHeli blast, as upstream did. */
public final class HbmCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static final String STANDARD = "com.hbm.explosion.vanillant.standard.";

    private HbmCompat() {}

    public enum Outcome { IGNORED, APPLIED, FALLBACK }

    /** Called on the server before the normal explosion's EffectOnly check. */
    public static Outcome detonate(ServerLevel level, Entity projectile, @Nullable WeaponInfo info,
            double x, double y, double z, float blockPower) {
        if (info == null || !info.useHBM || info.payloadNTM == WeaponInfo.Payload.NONE) return Outcome.IGNORED;
        if (!ModList.get().isLoaded("hbm")) {
            warn("missing", "NTM Next (hbm) is not installed");
            return Outcome.FALLBACK;
        }
        try {
            switch (info.payloadNTM) {
                case NTM_MINI_NUKE -> miniNuke(level, x, y, z,
                        Objects.requireNonNull(info.mukeContainer, "MiniNuke settings missing"), info.effectOnly);
                case NTM_NUKE -> {
                    // NTM treats an integer radius of zero as its default 25-block warhead.
                    if (!Float.isFinite(blockPower) || blockPower < 1)
                        throw new IllegalArgumentException("NUKE requires block explosion power of at least one");
                    if (!info.effectOnly) {
                        Entity blast = (Entity) HbmCalls.callStatic("com.hbm.entity.logic.EntityNukeExplosionMK5",
                                "statFac", level, (int) blockPower, x, y, z);
                        level.addFreshEntity(blast);
                    }
                    // NTM's factory adds the visual entity itself.
                    HbmCalls.callStatic("com.hbm.entity.effect.EntityNukeTorex", "statFac",
                            level, x, y, z, blockPower);
                }
                case NTM_FIRE -> fire(level, x, y, z,
                        Objects.requireNonNull(info.fireContainer, "Fire settings missing"), info.effectOnly);
                case NTM_MIST -> {
                    if (!info.effectOnly) mist(level, x, y, z,
                            Objects.requireNonNull(info.mistContainer, "Mist settings missing"));
                }
                case NTM_VNT -> vnt(level, projectile, x, y, z, blockPower,
                        Objects.requireNonNull(info.vntSettingContainer, "VNT settings missing"), info.effectOnly);
                case NTM_NT, NTM_CHEMICAL -> {
                    warn(info.payloadNTM.name(), "NTM Next does not support legacy " + info.payloadNTM
                            + " through this bridge");
                    return Outcome.FALLBACK;
                }
                case NONE -> { }
            }
            return Outcome.APPLIED;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            // Never retry a partially applied payload: the regular blast below still runs once.
            warn(info.payloadNTM.name(), "NTM Next payload " + info.payloadNTM + " could not run: " + e);
            return Outcome.FALLBACK;
        }
    }

    private static void warn(String key, String reason) {
        if (WARNED.add(key)) LOGGER.warn("[McHeli/NTM Next] {}. Settings are preserved; weapons use their normal "
                + "McHeli explosion, except EffectOnly weapons, which do not cause a normal explosion.", reason);
    }

    private static void miniNuke(ServerLevel level, double x, double y, double z, MukeContainer settings,
            boolean effectOnly) throws ReflectiveOperationException {
        boolean tiny = settings.particle.equalsIgnoreCase("TinyTot");
        if (effectOnly) {
            Object payload = HbmCalls.create("com.hbm.packet.toclient.MukePayload", x, y + 0.5, z, tiny, false);
            Object audience = HbmCalls.create("com.hbm.handler.threading.TargetPoint", level, x, y, z, 250.0);
            HbmCalls.callStatic("com.hbm.packet.PacketWire", "sendToAllAround", payload, audience);
            return;
        }
        Object params = HbmCalls.create("com.hbm.explosion.ExplosionNukeSmall$MukeParams");
        HbmCalls.field(params, "miniNuke", settings.miniNuke);
        HbmCalls.field(params, "safe", settings.safe);
        HbmCalls.field(params, "blastRadius", settings.blastRadius);
        HbmCalls.field(params, "killRadius", settings.killRadius);
        HbmCalls.field(params, "radiationLevel", settings.radiationLevel);
        HbmCalls.field(params, "shrapnelCount", settings.shrapnelCount);
        HbmCalls.field(params, "resolution", settings.resolution);
        HbmCalls.field(params, "tinytot", tiny);
        HbmCalls.callStatic("com.hbm.explosion.ExplosionNukeSmall", "explode", level, x, y, z, params);
    }

    private static void fire(ServerLevel level, double x, double y, double z, FireContainer settings,
            boolean effectOnly) throws ReflectiveOperationException {
        int type = switch (settings.fireType.toUpperCase(Locale.ROOT).trim()) {
            case "BALEFIRE", "BALE" -> 1;
            case "PHOSPHORUS", "PHOSPHOR", "WP" -> 2;
            case "OXY", "OXYGEN" -> 3;
            case "BLACK" -> 4;
            default -> 0;
        };
        if (!effectOnly) {
            for (int i = 0; i < Math.max(1, settings.count); i++) {
                Entity fire = (Entity) HbmCalls.create("com.hbm.entity.effect.EntityFireLingering", level);
                HbmCalls.call(fire, "setType", type);
                HbmCalls.call(fire, "setArea", settings.width, settings.height);
                HbmCalls.call(fire, "setDuration", lifetime(level, settings.lifetime, settings.lifetimeVariance));
                fire.setPos(spread(level, x, settings.areaSpread), y, spread(level, z, settings.areaSpread));
                level.addFreshEntity(fire);
            }
        }
        if (settings.particles) HbmCalls.callStatic("com.hbm.particle.helper.FlameCreator", "composeEffect",
                level, x, y, z, type == 1 ? 1 : 0);
    }

    private static void mist(ServerLevel level, double x, double y, double z, MistContainer settings)
            throws ReflectiveOperationException {
        if (settings.fluidType.equalsIgnoreCase("None")) return;
        Object fluid = HbmCalls.callStatic("com.hbm.inventory.fluid.NTMFluids", "byName",
                settings.fluidType.toUpperCase(Locale.ROOT));
        for (int i = 0; i < settings.cloudCount; i++) {
            Entity mist = (Entity) HbmCalls.create("com.hbm.entity.effect.EntityMist", level);
            HbmCalls.call(mist, "setType", fluid);
            HbmCalls.call(mist, "setArea", settings.width, settings.height);
            HbmCalls.call(mist, "setDuration", lifetime(level, settings.lifetime, settings.lifetimeVariance));
            mist.setPos(spread(level, x, settings.areaSpread), y, spread(level, z, settings.areaSpread));
            level.addFreshEntity(mist);
        }
    }

    private static double spread(ServerLevel level, double center, int spread) {
        return spread <= 0 ? center : center + level.getRandom().nextInt(Math.multiplyExact(spread, 2)) - spread;
    }

    private static int lifetime(ServerLevel level, int ticks, int variance) {
        return variance <= 0 ? ticks : Math.addExact(ticks, level.getRandom().nextInt(variance));
    }

    private static void vnt(ServerLevel level, Entity projectile, double x, double y, double z, float power,
            VNTSettingContainer settings, boolean effectOnly) throws ReflectiveOperationException {
        if (!effectOnly) {
            Object explosion = HbmCalls.create("com.hbm.explosion.vanillant.ExplosionVNT",
                    level, x, y, z, power, projectile);
            if (settings.preset != null) HbmCalls.call(explosion,
                    switch (settings.preset.toUpperCase(Locale.ROOT).trim()) {
                        case "AMAT", "ANTIMATTER" -> "makeAmat";
                        default -> "makeStandard";
                    });
            for (String part : List.of("BlockAllocator", "BlockProcessor", "EntityProcessor", "PlayerProcessor")) {
                if (settings.rawEntry.get(part) instanceof Map<?, ?> map)
                    HbmCalls.call(explosion, "set" + part, processor(map));
            }
            if (settings.rawEntry.get("SFX") instanceof List<?> list) {
                Object effects = Array.newInstance(Class.forName("com.hbm.explosion.vanillant.interfaces.IExplosionSFX"),
                        list.size());
                for (int i = 0; i < list.size(); i++) Array.set(effects, i, processor((Map<?, ?>) list.get(i)));
                HbmCalls.call(explosion, "setSFX", effects);
            }
            HbmCalls.call(explosion, "explode");
        }
        var effect = settings.explosionEffect;
        if (effect.isSmall) {
            HbmCalls.callStatic("com.hbm.particle.helper.ExplosionSmallCreator", "composeEffect", level, x, y, z,
                    effect.cloudCount, effect.cloudScale, effect.cloudSpeedMult);
        } else {
            // NTM Next replaced the old fully parameterized composeEffect with named presets.
            HbmCalls.callStatic("com.hbm.particle.helper.ExplosionCreator",
                    effect.cloudScale >= 6.5F ? "composeEffectLarge" : effect.cloudScale <= 2F
                            ? "composeEffectSmall" : "composeEffectStandard", level, x, y, z);
        }
    }

    /** Configured VNT processors are restricted to HBM's public standard processor package. */
    static Object processor(Map<?, ?> definition) throws ReflectiveOperationException {
        String type = (String) definition.get("Type");
        if (type == null || !type.matches("[A-Za-z][A-Za-z0-9]*"))
            throw new IllegalArgumentException("Invalid VNT processor Type: " + type);
        Object[] args = definition.get("Constructor") instanceof List<?> list ? list.toArray() : new Object[0];
        Object result = HbmCalls.create(STANDARD + type, args);
        for (var entry : definition.entrySet()) {
            String key = (String) entry.getKey();
            if (key.equals("Type") || key.equals("Constructor")) continue;
            Object value = entry.getValue() instanceof Map<?, ?> nested ? processor(nested) : entry.getValue();
            String method = Character.toLowerCase(key.charAt(0)) + key.substring(1);
            if (value instanceof Boolean flag) {
                if (!flag) continue;
                try {
                    HbmCalls.call(result, method);
                    continue;
                } catch (NoSuchMethodException ignored) { }
                try {
                    HbmCalls.call(result, "set" + key);
                    continue;
                } catch (NoSuchMethodException ignored) { }
            }
            try {
                HbmCalls.call(result, method, value);
            } catch (NoSuchMethodException ignored) {
                HbmCalls.field(result, key, value);
            }
        }
        return result;
    }
}
