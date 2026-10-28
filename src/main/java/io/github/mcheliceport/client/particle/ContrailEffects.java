package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.*;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.damage.AirframeDamage;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.util.McheliMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Authored jet outlets, Minecraft-scaled altitude/coldness and existing shared admission. */
final class ContrailEffects {
    private ContrailEffects(){}
    private static boolean jets(PlaneEntity plane){
        var info=plane.getAcInfo();if(info==null)return false;
        if(!info.engineOutlets.isEmpty()){for(int i=0;i<Math.min(4,info.engineOutlets.size());i++)if(info.engineOutlets.get(i).jet())return true;return false;}
        var p=plane.getPlaneInfo();return p!=null&&(!p.nozzles.isEmpty()||!p.exhaustFlames.isEmpty());
    }
    private static boolean enabled(ClientLevel level,PlaneEntity plane){
        return ClientConfig.get(ClientConfig.AIRCRAFT_CONDENSATION)&&level.dimensionType().hasSkyLight()&&!plane.isDestroyed()&&!plane.onGround()
                &&!plane.isInWater()&&plane.canUseFuel()&&plane.getCurrentThrottle()>.3&&Math.abs(plane.getNozzleRotation())<10
                &&(AirframeDamage.missing(plane)&4)==0&&jets(plane);
    }
    static int scope(ClientLevel level,AircraftEntity aircraft){return aircraft instanceof PlaneEntity p&&p.getY()>160&&enabled(level,p)?EffectTuning.contrailDistance(ClientConfig.get(ClientConfig.EFFECT_BUDGET)):80;}
    static void update(ClientLevel level,PlaneEntity plane,AircraftCondensationLook look,long now){
        boolean active=enabled(level,plane);if(!active){look.cold(0,false);return;}
        var pos=BlockPos.containing(plane.position());
        if(plane.getY()>140&&look.sampleTemperature(now)&&level.hasChunkAt(pos))look.temperature(now,level.getBiome(pos).value().getBaseTemperature());
        look.cold(AircraftCondensationLook.coldTarget(plane.getY(),look.temperature(),WingVaporEffects.motion(plane).length(),plane.getCurrentThrottle(),true,true),true);
    }
    static int emit(ClientLevel level,PlaneEntity plane,AircraftCondensationLook look,long now,boolean far){
        if(!enabled(level,plane)||look.contrail()<.025F)return 0;
        var quality=ClientConfig.get(ClientConfig.EFFECT_BUDGET);int base=quality==ClientConfig.EffectBudget.LOW?6:quality==ClientConfig.EffectBudget.HIGH?2:3;
        int period=Math.max(far?8:base,base*Math.max(1,(VehicleAtmosphere.stateCount()+11)/12))*(1+EffectPerformance.pressure());
        if(Math.floorMod(now+plane.getId(),period)!=0)return 0;
        double distance=Math.sqrt(Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(plane.position()));
        int range=EffectTuning.contrailDistance(quality);if(distance>range)return 0;
        float fade=(float)Math.clamp((range-distance)/Math.min(96,range/2),0,1);if(fade<.01F)return 0;
        Vec3 flight=WingVaporEffects.motion(plane);int mask=0,index=0;
        int samples=!far&&quality!=ClientConfig.EffectBudget.LOW&&EffectPerformance.pressure()==0&&flight.length()>1.1?2:1;
        float radius=AircraftCondensationLook.contrailRadius(flight.length(),period,samples,far);
        for(AircraftInfo.EngineOutlet outlet:EngineExhaustLook.outlets(plane.getAcInfo(),plane.getNozzleRotation())){
            int bit=1<<index++;if(!outlet.jet())continue;
            Vec3 direction=McheliMath.rotVec3(outlet.direction(),-plane.getYaw(),-plane.getPitch(),-plane.getRoll());
            Vec3 at=plane.position().add(McheliMath.rotVec3(outlet.position(),-plane.getYaw(),-plane.getPitch(),-plane.getRoll()))
                    .add(0,AircraftInfo.GLOBAL_Y_OFFSET,0).add(direction.scale(.6));
            for(int sample=0;sample<samples;sample++){
                Vec3 source=at.subtract(flight.scale((double)sample/samples));
                if(VehicleAtmosphere.admitContrail(level,source,radius*2.2F,far)){
                    Minecraft.getInstance().particleEngine.add(new CondensationParticle(level,source,flight,radius,.38F*look.contrail()*fade,96));mask|=bit;
                }
            }
        }
        return mask;
    }
}
