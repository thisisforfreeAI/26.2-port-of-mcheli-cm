package io.github.mcheliceport.effect;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.aircraft.AircraftHitBox;
import io.github.mcheliceport.entity.weapon.BaseBulletEntity;
import io.github.mcheliceport.entity.weapon.BulletEntity;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.VehicleImpactPayload;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Bounded, server-authoritative notifications for direct gun hits only. */
public final class VehicleImpactEmitter {
    private static final Map<AircraftEntity,VehicleImpactLook.Gate> VEHICLES=new WeakHashMap<>();
    private static final Map<ServerLevel,VehicleImpactLook.Gate> LEVELS=new WeakHashMap<>();
    private VehicleImpactEmitter() {}
    public static void hit(BaseBulletEntity bullet,Entity target) {
        if(!(bullet instanceof BulletEntity) || !(bullet.level() instanceof ServerLevel level))return;
        AircraftEntity ac=target instanceof AircraftEntity a?a:target instanceof AircraftHitBox box?box.getParent():null;
        if(ac==null || ac.isRemoved() || ac.isDestroyed() || ac.isIronCurtainActive() || bullet.getInfo()==null)return;
        Vec3 dir=bullet.getDeltaMovement();if(dir.lengthSqr()<1e-8)return;
        dir=dir.normalize();
        // EntityHitResult(entity) contains the entity origin, not its ray contact.
        // Recover a cosmetic contact without changing the legacy impact/explosion result.
        var contact=VehicleImpactLook.contact(bullet.position(),dir,target.getBoundingBox());
        if(contact.isEmpty())return;
        Vec3 hit=contact.get();
        long now=level.getGameTime();
        if(VEHICLES.size()>=512 && !VEHICLES.containsKey(ac))VEHICLES.remove(VEHICLES.keySet().iterator().next());
        if(!VEHICLES.computeIfAbsent(ac,k->new VehicleImpactLook.Gate()).admit(now,4,4)
                || !LEVELS.computeIfAbsent(level,k->new VehicleImpactLook.Gate()).admit(now,1,32))return;
        var inverse=VehicleImpactLook.body(ac.getYaw(),ac.getPitch(),ac.getRoll()).invert();
        Vec3 rel=hit.subtract(ac.position());dir=dir.normalize();
        var point=inverse.transformPosition(new Vector3f((float)rel.x,(float)rel.y,(float)rel.z));
        var direction=inverse.transformDirection(new Vector3f((float)dir.x,(float)dir.y,(float)dir.z)).normalize();
        boolean cannon=bullet.explosionPower>0;
        var payload=new VehicleImpactPayload(ac.getId(),ac.getUUID(),point.x,point.y,point.z,
                direction.x,direction.y,direction.z,VehicleImpactLook.radius(cannon,bullet.getPower()),cannon,bullet.getId());
        if(!payload.valid())return;
        for(var player:level.getChunkSource().chunkMap.getPlayersWatching(ac)) {
            if(player.distanceToSqr(ac)<96*96)ModNetwork.sendToPlayer(player,payload);
        }
    }
}
