package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/** No state, attachment scans or secondary emitters; births share VehicleAtmosphere admission. */
final class AirflowFireEffects {
    private AirflowFireEffects() {}
    static void light(ClientLevel level,AircraftEntity aircraft,java.util.List<Vec3> origins,float severity){
        float strength=AirflowFireLook.strength(severity);
        if(!supports(aircraft)||strength<=0)return;
        var outlets=EngineExhaustLook.outlets(aircraft.getAcInfo(),aircraft instanceof PlaneEntity p?p.getNozzleRotation():0);
        if(aircraft instanceof PlaneEntity&&outlets.isEmpty())return;
        for(int i=0;i<Math.min(2,origins.size());i++){
            Vec3 at=origins.get(i);
            if(!outlets.isEmpty())at=at.add(io.github.mcheliceport.util.McheliMath.rotVec3(
                    outlets.get(i%outlets.size()).direction(),-aircraft.getYaw(),-aircraft.getPitch(),-aircraft.getRoll()).scale(.75));
            var block=net.minecraft.core.BlockPos.containing(at);
            if(!level.hasChunkAt(block)||!level.getFluidState(block).isEmpty())continue;
            io.github.mcheliceport.client.render.SceneLights.fire(level,aircraft,2+i,at.add(0,.1,0),strength*.65F,3+4*strength);
        }
    }
    static boolean supports(AircraftEntity aircraft) {
        return WreckEffects.refined()&&(aircraft instanceof PlaneEntity||aircraft instanceof HeliEntity);
    }
    private static Vec3 motion(AircraftEntity aircraft) {
        return io.github.mcheliceport.damage.AirframeDamage.fragmentMotion(aircraft.getDeltaMovement(),
                aircraft.position().subtract(aircraft.xo,aircraft.yo,aircraft.zo));
    }
    static McheliParticle flame(ClientLevel level,AircraftEntity aircraft,Vec3 at,float radius,float opacity) {
        return supports(aircraft)?new AirflowFireParticle(level,at,motion(aircraft),radius,opacity)
                :new WreckFlameParticle(level,at.x,at.y,at.z,radius,opacity);
    }
    static void damage(ClientLevel level,AircraftEntity aircraft,Vec3 at,int engineIndex,float severity,long tick) {
        if(!supports(aircraft))return;
        float strength=AirflowFireLook.strength(severity);
        double distance=Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(at);
        if(strength<=0||distance>=48*48||Math.floorMod(tick+aircraft.getId(),3*(1+EffectPerformance.pressure()))!=0)return;
        var outlets=EngineExhaustLook.outlets(aircraft.getAcInfo(),aircraft instanceof PlaneEntity p?p.getNozzleRotation():0);
        if(aircraft instanceof PlaneEntity&&outlets.isEmpty())return;
        // Legacy nozzle positions are pivots inside the model; short-lived fire needs to
        // begin outside the outlet instead of being hidden by the engine shell.
        if(!outlets.isEmpty()) {
            var direction=io.github.mcheliceport.util.McheliMath.rotVec3(outlets.get(engineIndex%outlets.size()).direction(),
                    -aircraft.getYaw(),-aircraft.getPitch(),-aircraft.getRoll());
            at=at.add(direction.scale(.75));
        }
        at=at.add(0,.1,0);
        float fade=(float)Math.clamp((48-Math.sqrt(distance))/16,0,1);
        float radius=AirflowFireLook.radius(strength,motion(aircraft));
        if(VehicleAtmosphere.admit(level,at,radius*1.6F))
            Minecraft.getInstance().particleEngine.add(flame(level,aircraft,at,radius,(.45F+.4F*strength)*fade));
    }
}
