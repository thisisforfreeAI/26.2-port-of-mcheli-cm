package io.github.mcheliceport.weapon;

import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.weapon.guidance.EntityLockChecker;
import java.util.Locale;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Creates a weapon from its definition and copies the definition's values into it, with
 * upstream's interval padding. Ported from upstream {@code MCH_WeaponCreator}.
 */
public final class WeaponCreator {

    private WeaponCreator() {
    }

    /** The definition of a weapon name (content names are lowercase). */
    public static @Nullable WeaponInfo info(@Nullable String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        WeaponInfo info = ContentRegistries.weapon().get(name);
        return info != null ? info : ContentRegistries.weapon().get(name.toLowerCase(Locale.ROOT));
    }

    public static @Nullable WeaponBase createWeapon(Level world, String weaponName, Vec3 vec, float yaw, float pitch,
            @Nullable EntityLockChecker lockChecker, boolean onTurret) {
        WeaponInfo info = info(weaponName);
        if (info == null || info.type.isEmpty()) {
            return null;
        }
        WeaponTypes.Factory factory = WeaponTypes.get(info.type);
        if (factory == null) {
            return null;
        }
        WeaponBase weapon = factory.create(world, vec, yaw, pitch, weaponName, info);
        weapon.displayName = info.displayName;
        weapon.power = info.power;
        weapon.acceleration = info.acceleration;
        weapon.explosionPower = info.explosion;
        weapon.explosionPowerInWater = info.explosionInWater;
        weapon.interval = info.delay;
        weapon.delayedInterval = info.delay;
        weapon.numMode = info.modeNum;
        weapon.piercing = info.piercing;
        weapon.heatCount = info.heatCount;
        weapon.onTurret = onTurret;
        weapon.setLockCountMax(info.lockTime);
        weapon.setLockChecker(lockChecker);
        if (info.maxHeatCount > 0 && weapon.heatCount < 2) {
            weapon.heatCount = 2;
        }
        int adjustedInterval = adjustInterval(info.delay);
        if (world.isClientSide()) {
            weapon.interval = adjustedInterval;
            weapon.heatCount++;
            weapon.cartridge = info.cartridge;
        }
        weapon.delayedInterval = adjustedInterval;
        weapon.modifyCommonParameters();
        return weapon;
    }

    /**
     * Upstream's padding of the fire delay for the client and for gunners: a little longer than
     * the definition's, and negative (a "busy" cooldown) from 30 ticks up.
     */
    public static int adjustInterval(int delay) {
        int adjusted = delay;
        if (adjusted < 4) {
            adjusted++;
        } else if (adjusted < 7) {
            adjusted += 2;
        } else if (adjusted < 10) {
            adjusted += 3;
        } else if (adjusted < 20) {
            adjusted += 6;
        } else {
            adjusted += 10;
            if (adjusted >= 40) {
                adjusted = -adjusted;
            }
        }
        return adjusted;
    }
}
