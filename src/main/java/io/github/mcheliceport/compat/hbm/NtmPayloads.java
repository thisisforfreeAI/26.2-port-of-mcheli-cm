package io.github.mcheliceport.compat.hbm;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;
import org.jspecify.annotations.Nullable;

/**
 * Upstream's {@code processHBMExplosion}: the NTM payload of a weapon that has an {@code NTM}
 * block, set off where its projectile explodes. NTM Next (mod id {@code hbm}) is optional; this
 * class never touches its classes, and {@link NtmBridge}, which does, loads only when it is
 * installed. Without it, or when a payload fails, the warning is logged once and the weapon keeps
 * its normal McHeli explosion.
 */
public final class NtmPayloads {

    public static final String MOD_ID = "hbm";
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private NtmPayloads() {
    }

    public static boolean available() {
        return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
    }

    /**
     * Sets off {@code info}'s payload at the given position. {@code blockPower} sizes the nuke and
     * VNT and NT explosions, as upstream's {@code expBlock}.
     */
    public static void detonate(ServerLevel level, @Nullable Entity exploder, @Nullable WeaponInfo info, double x,
            double y, double z, float blockPower) {
        if (info == null || !info.useHBM || info.payloadNTM == WeaponInfo.Payload.NONE) {
            return;
        }
        if (!available()) {
            warnOnce("missing", "Weapon {} has an {} payload, but NTM Next is not installed; it explodes normally",
                    info.name, info.payloadNTM);
            return;
        }
        try {
            NtmBridge.detonate(level, exploder, info, x, y, z, blockPower);
        } catch (LinkageError | RuntimeException e) {
            warnOnce("failed:" + info.payloadNTM, "NTM {} payload of weapon {} failed; further failures of this payload "
                    + "are not logged", info.payloadNTM, info.name, e);
        }
    }

    static void warnOnce(String key, String message, Object... args) {
        if (WARNED.add(key)) {
            McheliCePort.LOGGER.warn("[NTM] " + message, args);
        }
    }
}
