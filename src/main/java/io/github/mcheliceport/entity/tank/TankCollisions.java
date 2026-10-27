package io.github.mcheliceport.entity.tank;

import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.aircraft.AircraftHitBox;
import io.github.mcheliceport.entity.seat.SeatEntity;
import io.github.mcheliceport.entity.weapon.BaseBulletEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.weapon.DamageScaling;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Server-only MCH_EntityTank collision/crushing, including while a client pilots the hull. */
final class TankCollisions {
    private final TankEntity tank;
    // Upstream refreshes/decrements these counters once per TEN ticks: 8 means up to 80 ticks.
    private final Map<Entity, Integer> excluded = new HashMap<>();

    TankCollisions(TankEntity tank) { this.tank = tank; }

    void rememberCarrier(Entity carrier) { this.excluded.put(carrier, 60); }

    void tick(ServerLevel level) {
        this.updateExcluded();
        if (this.tank.isPassenger()) { return; }
        var info = this.tank.getTankInfo();
        if (info == null) { return; }
        boolean crush = CommonConfig.COLLISION_DESTROY_BLOCK.get();
        var rules = GroundCollisionBlocks.rules(info.weightType);
        if (crush) {
            for (var wheel : info.wheels) {
                for (int sign : new int[] {-1, 1}) {
                    Vec3 p = this.tank.getTransformedPosition(new Vec3(sign * wheel.pos().x, wheel.pos().y, wheel.pos().z));
                    BlockPos pos = BlockPos.containing(p.add(0.5, 0.5, 0.5));
                    var state = level.getBlockState(pos);
                    if (state.is(Blocks.SNOW)) { level.removeBlock(pos, false); }
                    else if (state.is(Blocks.LILY_PAD) || state.is(Blocks.CAKE)) {
                        level.destroyBlock(pos, false, this.tank);
                    }
                }
            }
        }
        for (AircraftHitBox box : this.tank.getHitBoxes()) {
            if (!this.tank.isHitBoxActive(box) || box.getInfo() == null || this.tank.getRandom().nextInt(3) != 0) { continue; }
            if (crush) {
                var b = box.getInfo();
                this.crush(level, this.tank.getTransformedPosition(new Vec3(b.offsetX, b.offsetY, b.offsetZ)), b.width, b.height, rules);
            }
            this.ram(level, box.getBoundingBox());
        }
        if (crush) { this.crush(level, this.tank.position(), this.tank.getBbWidth() * 1.5, this.tank.getBbHeight() * 2, rules); }
        this.ram(level, this.tank.getBoundingBox());
    }

    private void updateExcluded() {
        if (this.tank.getCountOnUpdate() % 10 != 0) { return; }
        for (int seat = 0; seat <= this.tank.getSeatNum(); seat++) {
            Entity rider = this.tank.getEntityBySeatId(seat);
            if (rider != null) { this.excluded.put(rider, 8); }
        }
        Entity carrier = this.tank.getVehicle();
        if (carrier instanceof SeatEntity seat) { carrier = seat.getParent(); }
        if (carrier != null) { this.excluded.put(carrier, 60); }
        var chain = io.github.mcheliceport.entity.chain.ChainEntity.attached(this.tank);
        if (chain != null && chain.other(this.tank) != null) { this.excluded.put(chain.other(this.tank), 60); }
        this.excluded.replaceAll((entity, count) -> count - 1);
        this.excluded.entrySet().removeIf(e -> e.getValue() <= 0 || e.getKey().isRemoved());
    }

    private void crush(ServerLevel level, Vec3 center, double width, double height, GroundCollisionBlocks.Rules rules) {
        int ws = (int) (width + 2) / 2, hs = (int) (height + 2) / 2;
        for (int x = -ws; x <= ws; x++) {
            for (int z = -ws; z <= ws; z++) {
                for (int y = -hs; y <= hs + 1; y++) {
                    BlockPos pos = BlockPos.containing(center.x + x - 0.5, center.y + y - 1, center.z + z - 0.5);
                    if (pos.getY() < level.getMinY() || pos.getY() >= level.getMaxY() || !level.hasChunkAt(pos)) { continue; }
                    var state = level.getBlockState(pos);
                    if (state.isAir()) { continue; }
                    if (rules.protectedBlocks().contains(state)) { break; }
                    if (rules.breakable().contains(state)) {
                        // One in eight blocks drops items. Keep upstream's column stopping rule.
                        if (this.tank.getRandom().nextInt(8) == 0) { level.destroyBlock(pos, true, this.tank); }
                        else { level.removeBlock(pos, false); }
                        break;
                    }
                }
            }
        }
    }

    private boolean canRam(Entity entity) {
        if (io.github.mcheliceport.entity.chain.ChainEntity.linked(this.tank, entity)
                || entity.getRootVehicle() == this.tank.getRootVehicle() || entity.isRemoved() || entity instanceof ItemEntity || entity instanceof ExperienceOrb
                || entity instanceof SeatEntity || entity instanceof BaseBulletEntity
                || entity.getType() == ModEntityTypes.CHAIN.get()
                || this.tank.isMountedEntity(entity) || this.excluded.containsKey(entity)) { return false; }
        Entity host = entity.getVehicle();
        if (host instanceof SeatEntity seat) { host = seat.getParent(); }
        if (host == this.tank || host != null && this.excluded.containsKey(host)) { return false; }
        if (entity instanceof TankEntity other && other.getTankInfo() != null && other.getTankInfo().weightType == 2) {
            return CommonConfig.COLLISION_ENTITY_TANK_DAMAGE.get();
        }
        return CommonConfig.COLLISION_ENTITY_DAMAGE.get();
    }

    private void ram(ServerLevel level, AABB bounds) {
        Vec3 motion = this.tank.getDeltaMovement();
        double speed = motion.length();
        if (!Double.isFinite(speed) || speed <= 0.05) { return; }
        float damage = (float) (speed * 15);
        // Modern multipart entities can appear as both the parent and several parts in one query.
        Set<Entity> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Entity hit : level.getEntities(this.tank, bounds.inflate(0.3))) {
            if (hit instanceof AircraftHitBox box && !box.getParent().isHitBoxActive(box)) { continue; }
            Entity target = hit instanceof AircraftHitBox box ? box.getParent() : hit;
            if (!seen.add(target) || !this.canRam(target)) { continue; }
            double distance = Math.min(5, Math.hypot(target.getX() - this.tank.getX(), target.getZ() - this.tank.getZ()));
            // Pinned upstream accumulates the proximity bonus across candidates in each box query.
            damage = (float) (damage + (5 - distance));
            Entity pilot = this.tank.getPilot();
            DamageSource source = pilot instanceof LivingEntity living ? level.damageSources().mobAttack(living)
                    : level.damageSources().generic();
            DamageScaling.applyHurtResistantTime(target);
            var blood = io.github.mcheliceport.effect.VehicleBlood.ram(target, this.tank, source, motion);
            boolean applied = target.hurtServer(level, source, damage);
            io.github.mcheliceport.effect.VehicleBlood.finish(blood, applied);
            if (target instanceof AbstractArrow) { target.discard(); }
            else {
                double knockback = target instanceof AircraftEntity ? 0.05 : 1.5;
                target.push(motion.x * knockback, 0, motion.z * knockback);
                target.hurtMarked = true;
            }
            if (this.tank.getTankInfo().weightType != 2 && (target.getBbWidth() >= 1 || target.getBbHeight() >= 1.5)) {
                DamageSource recoil = target instanceof LivingEntity living ? level.damageSources().mobAttack(living)
                        : level.damageSources().generic();
                this.tank.hurtServer(level, recoil, damage / 3);
            }
        }
    }
}
