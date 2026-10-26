package io.github.mcheliceport.client.render;

import io.github.mcheliceport.effect.VehicleImpactLook;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.net.VehicleImpactPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3f;

/** Cosmetic blast notification: six occlusion rays and eighteen pending patches per world tick. */
public final class VehicleBlastScars {
    private static java.lang.ref.WeakReference<ClientLevel> world=new java.lang.ref.WeakReference<>(null);
    private static long lastTick=Long.MIN_VALUE;
    private static int rays;
    private VehicleBlastScars() {}
    public static void blast(ClientLevel level, Vec3 origin, float size) {
        if (!VehicleImpactMarks.enabled() || !Float.isFinite(size) || size<1 || !Double.isFinite(origin.lengthSqr())) return;
        if (world.get()!=level || lastTick!=level.getGameTime()) { world=new java.lang.ref.WeakReference<>(level); lastTick=level.getGameTime(); rays=0; }
        int scanned=0; double range=Math.min(24,Math.max(3,size*1.6));
        for (var e:level.entitiesForRendering()) {
            if (++scanned>128 || rays>=6) break;
            if (!(e instanceof AircraftEntity ac) || ac.isDestroyed() || ac.getAcInfo()==null || ac.isRemoved()
                    || origin.distanceToSqr(ac.position())>range*range
                    || Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(ac.position())>48*48) continue;
            var models=io.github.mcheliceport.client.model.AircraftModels.peek(ac.getAcInfo());
            double height=models==null?1.5:Math.clamp(models.body().maxY*.4+.35,1,3);
            var body=VehicleImpactLook.body(ac.getYaw(),ac.getPitch(),ac.getRoll());
            var center=body.transformPosition(new Vector3f(0,(float)height-.35F,0));
            Vec3 target=ac.position().add(center.x,center.y,center.z), direction=target.subtract(origin).normalize();
            if (direction.lengthSqr()<.5 || !level.hasChunkAt(BlockPos.containing(origin))
                    || !level.hasChunkAt(BlockPos.containing(target))) continue;
            rays++;
            if (level.clip(new ClipContext(origin,target,ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,
                    CollisionContext.empty())).getType()!=HitResult.Type.MISS) continue;
            var inverse=new org.joml.Matrix4f(body).invert();
            var point=inverse.transformPosition(new Vector3f(center));
            var incoming=inverse.transformDirection(new Vector3f((float)direction.x,(float)direction.y,(float)direction.z)).normalize();
            var tangent=new Vector3f(incoming).cross(0,1,0);
            if(tangent.lengthSquared()<.01F)tangent.set(1,0,0);else tangent.normalize();
            for(int i=0;i<3;i++) {
                var p=new Vector3f(point).fma((i-1)*.45F,tangent);p.y+=(i==1?.28F:0);
                VehicleImpactMarks.receiveBlast(new VehicleImpactPayload(ac.getId(),ac.getUUID(),p.x,p.y,p.z,
                        incoming.x,incoming.y,incoming.z,.55F,true,(int)lastTick*31+i));
            }
        }
    }
    public static int rays() { return rays; }
}
