package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.client.model.*;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.damage.AirframeDamage;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.util.McheliMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/** Uses actual supported wing-tip vertices and the existing atmosphere admission/mesh worker. */
final class WingVaporEffects {
    private WingVaporEffects(){}
    static Vec3 motion(PlaneEntity plane){return AirframeDamage.fragmentMotion(plane.getDeltaMovement(),plane.position().subtract(plane.xo,plane.yo,plane.zo));}
    static boolean enabled(PlaneEntity plane){return ClientConfig.get(ClientConfig.AIRCRAFT_CONDENSATION)&&!plane.isDestroyed()&&!plane.isInWater()
            &&(plane.partWing==null||plane.partWing.getFactor()<.02F);}
    static void update(ClientLevel level,PlaneEntity plane,AircraftCondensationLook look,long now){
        look.tick(now,plane.getYaw(),plane.getPitch(),motion(plane).length(),level.getRainLevel(1),!plane.onGround(),enabled(plane));
    }
    static int emit(ClientLevel level,PlaneEntity plane,AircraftCondensationLook look,long now){
        if(!enabled(plane)||look.strength()<.04F)return 0;
        var budget=ClientConfig.get(ClientConfig.EFFECT_BUDGET);boolean low=budget==ClientConfig.EffectBudget.LOW;
        double distance=Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(plane.position());int range=low?24:48;
        if(distance>range*range||Math.floorMod(now+plane.getId(),AircraftCondensationLook.period(low,VehicleAtmosphere.stateCount(),EffectPerformance.pressure()))!=0)return 0;
        if(VehicleAtmosphere.emitted()>=EngineExhaustLook.cap(budget))return 0;
        Vec3 flight=motion(plane);if(flight.lengthSqr()<.16)return 0;
        var models=AircraftModels.peek(plane.getAcInfo());if(models==null)return 0;
        BreakupMeshes.Part shape=null;for(var group:models.body().groups)if(group.name().equals("$body")){shape=BreakupMeshes.part(group);break;}
        if(shape==null||shape.bounds().high().x-shape.bounds().low().x<5)return 0;
        int missing=AirframeDamage.missing(plane),mask=0;float fade=(float)Math.clamp((range-Math.sqrt(distance))/12,0,1);
        Vec3 direction=flight.normalize();
        for(int side=0;side<2;side++)if((missing&(1<<side))==0){
            var local=shape.tips().get(side);Vec3 at=plane.position().add(McheliMath.rotVec3(local,-plane.getYaw(),-plane.getPitch(),-plane.getRoll()))
                    .add(0,io.github.mcheliceport.content.aircraft.AircraftInfo.GLOBAL_Y_OFFSET,0).subtract(direction.scale(.5));
            float size=.8F+.25F*look.strength();
            if(VehicleAtmosphere.admit(level,at,size*2.2F)){
                Minecraft.getInstance().particleEngine.add(new CondensationParticle(level,at,flight,size,(.25F+.45F*look.strength())*fade,24));mask|=1<<side;
            }
        }
        return mask;
    }
}
