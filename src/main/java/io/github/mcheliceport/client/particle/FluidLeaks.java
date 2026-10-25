package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.client.effect.*;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.util.McheliMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

final class FluidLeaks {
    private FluidLeaks(){}
    static void emit(ClientLevel level,AircraftEntity aircraft,Vec3 engine,float severity,long now){
        if(aircraft.isDestroyed()||Math.floorMod(now+aircraft.getId(),7*(1+EffectPerformance.pressure()))!=0)return;
        boolean coolant=(aircraft.getId()&1)!=0;
        double side=(aircraft.getId()&2)==0?1:-1;
        Vec3 origin=engine.add(McheliMath.rotVec3(side*Math.min(1.4,aircraft.getBbWidth()*.65),-.25,0,
                -aircraft.getYaw(),-aircraft.getPitch(),-aircraft.getRoll()));
        // A little runoff escapes the lower body/track edge; drops still start at the machinery.
        double runout=.4;
        if(aircraft instanceof io.github.mcheliceport.entity.tank.TankEntity){
            var model=io.github.mcheliceport.client.model.AircraftModels.peek(aircraft.getAcInfo());
            if(model!=null)runout=Math.max(runout,Math.min(3.2,Math.max(Math.abs(model.body().minX),Math.abs(model.body().maxX))+.1)
                    -Math.min(1.4,aircraft.getBbWidth()*.65));
        }
        Vec3 runoff=origin.add(McheliMath.rotVec3(side*runout,0,0,-aircraft.getYaw(),-aircraft.getPitch(),-aircraft.getRoll()));
        double floor=FluidLeakLook.parked(aircraft.getDeltaMovement().lengthSqr())?FluidStains.add(level,runoff,coolant):Double.NaN;
        if(!VehicleAtmosphere.admit(level,origin,.045F))return;
        int tint=FluidLeakLook.tint(coolant);Vec3 motion=aircraft.getDeltaMovement().scale(.2);
        Minecraft.getInstance().particleEngine.add(new SurfaceDebrisParticle(level,origin.x,origin.y,origin.z,
                motion.x,-.08,motion.z,.035F,((tint>>16)&255)/255F,((tint>>8)&255)/255F,(tint&255)/255F,
                Double.isFinite(floor)?floor:origin.y-8,24,.05F));
    }
}
