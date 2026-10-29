package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.hud.CockpitFeedbackLayer;
import io.github.mcheliceport.client.hud.CockpitFeedbackLook;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.util.McheliMath;
import org.joml.Matrix4f;

/** Millimetre-scale vibration of the existing cockpit assembly, with a steady pilot camera. */
public final class CockpitInstrumentMotion {
    private CockpitInstrumentMotion(){}
    public static void apply(AircraftEntity ac,AircraftRenderState render,float partial){
        if(render.far||render.thermal||!CockpitFeedbackLayer.active(ac))return;
        double time=ac.level().getGameTime()+partial;float damage=CockpitFeedbackLayer.damage();
        double throttle=CockpitFeedbackLayer.instrumentThrottle(ac);
        float x=CockpitFeedbackLook.shift(time,ac.getId(),throttle,damage)*.005F;
        float y=CockpitFeedbackLook.shift(time*.83,ac.getId()+9,throttle,damage)*.005F;
        var offset=McheliMath.rotVec3(x,y,0,-render.yaw,-render.pitch,-render.roll);
        for(int i=0;i<render.draws.size();i++){
            var draw=render.draws.get(i);
            var pose=new Matrix4f().translation((float)offset.x,(float)offset.y,(float)offset.z).mul(draw.pose());
            render.draws.set(i,new AircraftPartPoses.Draw(pose,draw.groups()));
        }
    }
}
