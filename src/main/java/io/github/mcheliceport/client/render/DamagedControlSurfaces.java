package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.model.*;
import io.github.mcheliceport.client.particle.WreckSequenceEffects;
import io.github.mcheliceport.client.particle.VehicleAtmosphereLook;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.damage.*;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Original textured control panels, preserving the body coordinate space for existing hit marks. */
public final class DamagedControlSurfaces {
    private DamagedControlSurfaces() {}
    public static boolean enabled(){return WreckSequenceEffects.enabled()&&ClientConfig.get(ClientConfig.EFFECT_STYLE)==ClientConfig.EffectStyle.POLISHED;}
    private static float health(AircraftEntity ac,int slot) {
        var zones=DamageZones.of(ac.getAcInfo());
        for(int i=0;i<zones.size();i++) {
            var z=zones.get(i);
            if(slot<2?z.kind()==ComponentKind.WING&&z.index()==slot:z.kind()==ComponentKind.AIRFRAME_TAIL)
                return ComponentHealth.health(ac.getComponentHealth(),i);
        }
        return 1;
    }
    public static boolean missing(AircraftEntity ac,int slot){return enabled()&&ac.getAcInfo()!=null&&DamagedControlLook.detached(health(ac,slot));}
    public static Matrix4f hinge(Matrix4f body,ControlSurfaceMeshes.Panel panel,float angle) {
        Vec3 pivot=panel.pivot(),axis=panel.axis();
        return new Matrix4f(body).translate((float)pivot.x,(float)pivot.y,(float)pivot.z)
                .rotate(angle,(float)axis.x,(float)axis.y,(float)axis.z).translate((float)-pivot.x,(float)-pivot.y,(float)-pivot.z);
    }
    public static void apply(PlaneEntity ac,AircraftRenderState render,WreckSequenceEffects.State state,float partial) {
        if(state.airframe==null)return;
        if(!enabled()) {
            int baseline=0;for(int slot=0;slot<6;slot++){
                if(DamagedControlLook.detached(health(ac,slot)))baseline|=1<<slot;
                state.scattered.remove("control"+slot);
            }
            state.controlMask=baseline;state.controlLossAt.clear();return;
        }
        int lost=0;boolean active=false;
        float hull=VehicleAtmosphereLook.damage(ac.getHP(),ac.getMaxHP());
        float[] strength=new float[6];
        for(int slot=0;slot<6;slot++) {
            float h=health(ac,slot);if(DamagedControlLook.detached(h))lost|=1<<slot;
            strength[slot]=DamagedControlLook.strength(h,hull);active|=strength[slot]>0;
        }
        long now=ac.level().getGameTime();
        double distance=Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(ac.position());
        if(state.controlMask>=0)for(int slot=0;slot<6;slot++) {
            int bit=1<<slot;
            if((lost&bit)!=0&&(state.controlMask&bit)==0&&!render.far&&distance<40*40)state.controlLossAt.put(slot,now);
            if((lost&bit)==0){state.controlLossAt.remove(slot);state.scattered.remove("control"+slot);}
        }
        state.controlMask=lost;
        if(!active)return;
        float fade=(float)Math.clamp((40-Math.sqrt(distance))/12,0,1);
        Vec3 motion=AirframeDamage.fragmentMotion(ac.getDeltaMovement(),ac.position().subtract(ac.xo,ac.yo,ac.zo));
        var out=new ArrayList<AircraftPartPoses.Draw>();
        for(var draw:render.draws)for(var group:draw.groups()) {
            if(!group.name().equals("$body")){out.add(new AircraftPartPoses.Draw(draw.pose(),List.of(group)));continue;}
            var model=BreakupMeshes.controls(group,state.airframe);
            if(model==null||model.panels().isEmpty()){out.add(new AircraftPartPoses.Draw(draw.pose(),List.of(group)));continue;}
            // Preserve the original complete profile for later detached whole-wing fragments.
            if(state.controlLayout==null||state.airframe.mask()==0)state.controlLayout=model;
            var unchanged=new ArrayList<McheliModel.Group>();unchanged.add(model.kept());
            for(var panel:model.panels()) {
                int slot=panel.slot();
                if((lost&(1<<slot))!=0) {
                    Long born=state.controlLossAt.get(slot);String key="control"+slot;
                    if(born!=null&&now>=born&&now-born<=28&&!render.far&&fade>0&&!state.scattered.contains(key)) {
                        Vec3 p=panel.pivot();
                        if(LoosePanels.controlPiece(ac,render.texture,panel.group(),draw.pose(),new Vector3f((float)p.x,(float)p.y,(float)p.z),slot))state.scattered.add(key);
                    }
                    continue;
                }
                float angle=DamagedControlLook.angle(strength[slot],motion.length(),now+partial,ac.getId()+slot*31,slot==2||slot==3,fade);
                if(angle==0)unchanged.add(panel.group());else out.add(new AircraftPartPoses.Draw(hinge(draw.pose(),panel,angle),List.of(panel.group())));
            }
            out.add(new AircraftPartPoses.Draw(draw.pose(),List.copyOf(unchanged)));
        }
        render.draws.clear();render.draws.addAll(out);
    }
    static McheliModel.Group fragment(WreckSequenceEffects.State state,McheliModel.Group group,int section) {
        int lost=state.controlMask&(section==0?1:section==1?2:60);
        return enabled()&&lost>0&&state.controlLayout!=null?BreakupMeshes.controlFragment(group,state.controlLayout,lost):group;
    }
}
