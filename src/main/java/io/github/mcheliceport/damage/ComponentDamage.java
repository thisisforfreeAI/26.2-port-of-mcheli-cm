package io.github.mcheliceport.damage;

import io.github.mcheliceport.combat.LagCompensation;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.effect.VehicleImpactLook;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.weapon.BaseBulletEntity;
import java.util.List;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The server side of M9 component damage. A hit still drains hull HP exactly as before; on top, a
 * direct projectile hit damages the component zone its flight passes through first, and an
 * explosion damages every zone within {@link #BLAST_FALLOFF_BLOCKS} with distance falloff. Fire,
 * lava, crashes and melee touch no component. Repair restores components in proportion to the
 * hull HP it restores, the most damaged first; a full repair restores them all.
 */
public final class ComponentDamage {

    /** A component's whole health, as a share of the vehicle's max HP. */
    public static final float HEALTH_SHARE_OF_MAX_HP = 0.35F;
    public static final double BLAST_FALLOFF_BLOCKS = 6.0;
    /** Zones are grown by this much for projectile rays, since they are rough boxes. */
    public static final double RAY_SLACK = 0.25;
    /** How far past the hit a round can still reach a component, in blocks. */
    public static final double PENETRATION = 3.0;

    private ComponentDamage() {
    }

    /** Called by the vehicle after it took {@code damage} hull damage from {@code source}. */
    public static void onHurt(AircraftEntity ac, DamageSource source, float damage) {
        AircraftInfo info = ac.getAcInfo();
        if (info == null || damage <= 0.0F) {
            return;
        }
        List<DamageZone> zones = DamageZones.of(info);
        if (zones.isEmpty()) {
            return;
        }
        long packed = ac.getComponentHealth();
        long before = packed;
        Entity direct = source.getDirectEntity();
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            Vec3 at = source.getSourcePosition();
            if (at == null) {
                return;
            }
            Vec3 body = toBody(ac, at, ac.position());
            for (int i = 0; i < zones.size(); i++) {
                double falloff = 1.0 - zones.get(i).distanceTo(body) / BLAST_FALLOFF_BLOCKS;
                if (falloff > 0.0) {
                    packed = ComponentHealth.damage(packed, i,
                            fraction(damage * (float) falloff, zones.get(i).kind().damageShare, ac.getMaxHP()));
                }
            }
        } else if (direct instanceof BaseBulletEntity bullet) {
            Vec3 motion = bullet.getDeltaMovement();
            if (motion.lengthSqr() < 1.0E-8) {
                return;
            }
            // Where the vehicle was for this shot (M9a lag compensation), so the ray meets the zones it saw.
            Vec3 origin = ac.position().add(LagCompensation.shift(ac, bullet.hitRewindTicks()));
            Vec3 from = toBody(ac, bullet.position().subtract(motion), origin);
            Vec3 dir = dirToBody(ac, motion.normalize());
            // The ray starts one tick of flight before the hit segment; the hit lies within two.
            int slot = pick(zones, from, dir, 2.0 * motion.length() + PENETRATION);
            if (slot >= 0) {
                packed = ComponentHealth.damage(packed, slot,
                        fraction(damage, zones.get(slot).kind().damageShare, ac.getMaxHP()));
            }
        }
        if (packed != before) {
            ac.setComponentHealth(packed);
        }
    }

    /** Called by the vehicle after a repair of {@code amount} hull HP. */
    public static void onRepair(AircraftEntity ac, int amount) {
        AircraftInfo info = ac.getAcInfo();
        if (info == null) {
            return;
        }
        if (ac.getDamageTaken() <= 0) {
            ac.setComponentHealth(ComponentHealth.FULL);
            return;
        }
        int count = DamageZones.of(info).size();
        ac.setComponentHealth(ComponentHealth.repair(ac.getComponentHealth(), count,
                amount / Math.max(1.0F, HEALTH_SHARE_OF_MAX_HP * ac.getMaxHP())));
    }

    /** The share of a component's whole health a hit takes. */
    public static float fraction(float damage, float share, int maxHp) {
        return damage * share / Math.max(1.0F, HEALTH_SHARE_OF_MAX_HP * maxHp);
    }

    /**
     * The zone a body-space ray enters first within {@code maxDistance} (zones grown by
     * {@link #RAY_SLACK}), or -1.
     */
    public static int pick(List<DamageZone> zones, Vec3 from, Vec3 dir, double maxDistance) {
        int best = -1;
        double bestT = Double.POSITIVE_INFINITY;
        double bestVolume = Double.POSITIVE_INFINITY;
        for (int i = 0; i < zones.size(); i++) {
            DamageZone z = zones.get(i);
            DamageZone grown = new DamageZone(z.kind(), z.index(), z.center(),
                    z.halfSize().add(RAY_SLACK, RAY_SLACK, RAY_SLACK), z.partName());
            double t = grown.entry(from, dir);
            double volume = z.halfSize().x * z.halfSize().y * z.halfSize().z;
            // Broad fallback engine boxes can share the wing/tail surface. Prefer the specific zone
            // at the same entry point, retaining nearest-entry behavior for separate components.
            if (!Double.isNaN(t) && t <= maxDistance && (t < bestT - 1.0E-6
                    || Math.abs(t - bestT) <= 1.0E-6 && volume < bestVolume)) {
                bestT = t;
                bestVolume = volume;
                best = i;
            }
        }
        return best;
    }

    private static Matrix4f inverseBody(AircraftEntity ac) {
        return VehicleImpactLook.body(ac.getYaw(), ac.getPitch(), ac.getRoll()).invert();
    }

    static Vec3 toBody(AircraftEntity ac, Vec3 world, Vec3 origin) {
        Vector3f p = inverseBody(ac).transformPosition(new Vector3f((float) (world.x - origin.x),
                (float) (world.y - origin.y), (float) (world.z - origin.z)));
        return new Vec3(p.x, p.y, p.z);
    }

    static Vec3 dirToBody(AircraftEntity ac, Vec3 dir) {
        Vector3f d = inverseBody(ac).transformDirection(new Vector3f((float) dir.x, (float) dir.y, (float) dir.z))
                .normalize();
        return new Vec3(d.x, d.y, d.z);
    }
}
