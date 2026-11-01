package io.github.mcheliceport.compat.hbm;

import io.github.mcheliceport.content.weapon.WeaponInfo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Fabric stand-in for the NeoForge build's NTM Next bridge. NTM Next exists only for NeoForge, so
 * {@link NtmPayloads#available()} is always false here and this is never called.
 */
final class NtmBridge {

    private NtmBridge() {
    }

    static void detonate(ServerLevel level, @Nullable Entity exploder, WeaponInfo info, double x, double y, double z,
            float blockPower) {
        throw new UnsupportedOperationException("NTM Next is not available on Fabric");
    }
}
