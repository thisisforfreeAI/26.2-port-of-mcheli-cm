package io.github.mcheliceport.weapon.guidance;

import net.minecraft.world.entity.Entity;

/** Upstream {@code MCH_IEntityLockChecker}: a vehicle's extra say in what its seekers may lock. */
public interface EntityLockChecker {

    boolean canLockEntity(Entity entity);
}
