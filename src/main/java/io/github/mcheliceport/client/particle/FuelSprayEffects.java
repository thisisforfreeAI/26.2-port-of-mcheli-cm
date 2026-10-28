package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.damage.AirframeDamage;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.util.McheliMath;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/** No independent state: recent losses, exact cut anchors and repairs belong to the airframe. */
final class FuelSprayEffects {
    private FuelSprayEffects() {}
    static void emit(ClientLevel level,PlaneEntity plane,List<Vec3> engines,long tick) {
        if(!WreckEffects.refined()||plane.isDestroyed())return;
        var state=WreckSequenceEffects.get(plane);
        if(state==null||state.airframe==null)return;
        int missing=AirframeDamage.missing(plane)&state.airframe.mask()&3;
        if(missing==0)return;
        int period=3*(1+EffectPerformance.pressure());
        if(Math.floorMod(tick+plane.getId(),period)!=0)return;
        int choice=Math.floorMod(Math.floorDiv(tick+plane.getId(),period),2);
        // Prefer alternating live leaks; a quiet/healed side must not suppress the other one.
        for(int attempt=0;attempt<2;attempt++) {
            int side=(choice+attempt)%2,bit=1<<side;
            if((missing&bit)==0)continue;
            int age=AirframeFuelLook.age(tick,state.airframeLossAt.get(bit));
            float strength=AirframeFuelLook.envelope(age);
            Vec3 local=state.airframe.openings().get(bit);
            if(strength<=0||local==null)continue;
            Vec3 at=plane.position().add(McheliMath.rotVec3(local.add(side==0?-.06:.06,0,0),
                    -plane.getYaw(),-plane.getPitch(),-plane.getRoll())).add(0,AircraftInfo.GLOBAL_Y_OFFSET,0);
            double distance=Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(at);
            if(distance>=48*48)return;
            strength*=(float)Math.clamp((48-Math.sqrt(distance))/16,0,1);
            if(!VehicleAtmosphere.admit(level,at,.7F))return;
            Vec3 travel=AirflowFireLook.motion(AirframeDamage.fragmentMotion(plane.getDeltaMovement(),
                    plane.position().subtract(plane.xo,plane.yo,plane.zo)));
            Vec3 eject=McheliMath.rotVec3(side==0?-.09:.09,.015,-.045,
                    -plane.getYaw(),-plane.getPitch(),-plane.getRoll());
            Vec3 velocity=travel.scale(.18).add(eject);
            long slot=Math.floorDiv(tick+plane.getId(),period);
            if(Math.floorMod(slot,4)==0)Minecraft.getInstance().particleEngine.add(new Vapor(level,at,velocity,strength));
            else {
                int ignition=-1;
                boolean burning=AirflowFireLook.strength(VehicleAtmosphereLook.damage(plane.getHP(),plane.getMaxHP()))>0
                        &&!EngineExhaustLook.outlets(plane.getAcInfo(),plane.getNozzleRotation()).isEmpty();
                if(burning&&Math.floorMod(slot,3)==0)for(Vec3 engine:engines) {
                    int t=AirframeFuelLook.ignitionTick(at.subtract(engine),velocity.subtract(travel));
                    if(t>=0&&(ignition<0||t<ignition))ignition=t;
                }
                Minecraft.getInstance().particleEngine.add(new FuelSprayParticle(level,at,velocity,strength,ignition));
            }
            return; // One request for the entire aircraft, including when both wings are torn.
        }
    }
    static final class Vapor extends SmokeParticle {
        Vapor(ClientLevel level,Vec3 at,Vec3 velocity,float strength) {
            super(level,at.x,at.y,at.z,velocity.x,velocity.y+.012,velocity.z);
            useBillow(SmokeAtlas.WISPS);enhanced=true;coherentDrift=true;disableCollision();
            setColor(.94F,.94F,.90F);setAlphaF(.16F*strength);setParticleMaxAge(24);
            diffusible=true;particleScale=1.6F;particleMaxScale=6.5F;motionYUpAge=.35F;
        }
    }
}
