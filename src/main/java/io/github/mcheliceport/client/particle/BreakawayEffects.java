package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.damage.AirframeDamage;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.util.McheliMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Torn-airframe effects for every plane: a separation burst where a wing or tail lets go, a burning,
 * sparking smoke trail behind the falling section, a short smolder where it lands, and arcing,
 * smoking stumps on a plane that keeps flying. All presentation-only, on the shared admission.
 */
public final class BreakawayEffects {
    private BreakawayEffects() {}

    /** The moment a section separates: flash, a spray of sparks, torn skin chips, flame and a sharp metal tear. */
    public static void tear(ClientLevel level,Vec3 at,Vec3 velocity,int seed) {
        if(!active(level,at,64))return;
        var engine=Minecraft.getInstance().particleEngine;var random=RandomSource.create(seed*31L+level.getGameTime());
        int pressure=1+EffectPerformance.pressure();
        if(VehicleAtmosphere.admit(level,at,.6F))engine.add(new ImpactCoreParticle(level,at.x,at.y,at.z,.55F,3));
        for(int i=0;i<12/pressure;i++){
            Vec3 d=direction(random).scale(.12+random.nextDouble()*.22).add(velocity.scale(.6)).add(0,.06,0);
            if(VehicleAtmosphere.admit(level,at,.05F))
                engine.add(new SparkParticle(level,at.x,at.y,at.z,d.x,d.y,d.z,14+random.nextInt(12),.03F+random.nextFloat()*.025F));
        }
        for(int i=0;i<6/pressure;i++){
            Vec3 d=direction(random).scale(.08+random.nextDouble()*.12).add(velocity.scale(.7)).add(0,.08,0);
            float shade=.22F+random.nextFloat()*.14F;
            if(VehicleAtmosphere.admit(level,at,.06F))engine.add(new SurfaceDebrisParticle(level,at.x,at.y,at.z,d.x,d.y,d.z,
                    .04F+random.nextFloat()*.05F,shade,shade*.97F,shade*.92F,at.y-48,40+random.nextInt(20),.25F+random.nextFloat()*.3F));
        }
        flame(level,at,.6F,.9F);flame(level,at.add(velocity.scale(-.5)),.45F,.75F);
        smoke(level,at,velocity.scale(.25),.16F,.6F,40,.9F);
        sound(at,SoundEvents.ANVIL_BREAK,.8F,.55F);sound(at,SoundEvents.GENERIC_EXPLODE.value(),.35F,1.6F);
    }

    /**
     * Each tick of a torn section's life. Falling: a hot fire at the tear cooling into a dark smoke
     * trail, with early spark showers and shed chips. Resting ({@code rested>=0}): flame then smolder.
     */
    public static void trail(ClientLevel level,Vec3 at,Vec3 velocity,int age,int rested,int seed) {
        if(!active(level,at,64))return;
        int pressure=1+EffectPerformance.pressure();var random=RandomSource.create(seed*131L+age);
        var engine=Minecraft.getInstance().particleEngine;
        if(rested<0) {
            float heat=Math.clamp(1-age/120F,0,1);
            if(age%pressure==0)smoke(level,at,velocity.scale(.1).add(0,.02,0),.1F+.22F*(1-heat),.35F+.25F*heat,34+random.nextInt(16),.55F);
            if(heat>0&&age%(2*pressure)==0)flame(level,at.add(velocity.scale(-.3)),.22F+.3F*heat,.85F*heat);
            if(age<36&&age%(3*pressure)==0)for(int i=0;i<2;i++){
                Vec3 d=direction(random).scale(.07).add(velocity.scale(.35)).add(0,-.02,0);
                if(VehicleAtmosphere.admit(level,at,.04F))engine.add(new SparkParticle(level,at.x,at.y,at.z,d.x,d.y,d.z,10+random.nextInt(8),.03F));
            }
            if(age<80&&age%(9*pressure)==0&&VehicleAtmosphere.admit(level,at,.05F)){
                Vec3 d=direction(random).scale(.06).add(velocity.scale(.5));float shade=.25F+random.nextFloat()*.1F;
                engine.add(new SurfaceDebrisParticle(level,at.x,at.y,at.z,d.x,d.y,d.z,.035F+random.nextFloat()*.03F,shade,shade,shade*.95F,at.y-48,36,.4F));
            }
            return;
        }
        if(rested<70&&rested%(5*pressure)==0)flame(level,at.add((random.nextDouble()-.5)*.3,0,(random.nextDouble()-.5)*.3),.3F,.75F*(1-rested/70F));
        if(rested<180&&rested%(9*pressure)==0)smoke(level,at,new Vec3(0,.03,0),.18F,.45F*(1-rested/220F),55,1.2F);
        if(rested<40&&rested%(8*pressure)==0&&VehicleAtmosphere.admit(level,at,.04F)){
            Vec3 d=direction(random).scale(.05).add(0,.14,0);engine.add(new SparkParticle(level,at.x,at.y,at.z,d.x,d.y,d.z,14,.03F));
        }
    }

    /** A plane flying on with a wing or tail torn off: severed wiring arcs and the open structure smokes. */
    static void stump(ClientLevel level,PlaneEntity plane,long tick) {
        if(plane.isDestroyed())return;
        var state=WreckSequenceEffects.get(plane);if(state==null||state.airframe==null)return;
        int missing=AirframeDamage.missing(plane)&state.airframe.mask();if(missing==0)return;
        int pressure=1+EffectPerformance.pressure();var engine=Minecraft.getInstance().particleEngine;
        Vec3 travel=AirframeDamage.fragmentMotion(plane.getDeltaMovement(),plane.position().subtract(plane.xo,plane.yo,plane.zo));
        for(int bit:new int[]{1,2,4}) {
            if((missing&bit)==0)continue;Vec3 local=state.airframe.openings().get(bit);if(local==null)continue;
            Vec3 at=plane.position().add(McheliMath.rotVec3(local,-plane.getYaw(),-plane.getPitch(),-plane.getRoll())).add(0,AircraftInfo.GLOBAL_Y_OFFSET,0);
            if(!active(level,at,48))return;
            Long born=state.airframeLossAt.get(bit);long age=born==null?400:tick-born;
            var random=RandomSource.create(tick*7+bit);
            if(Math.floorMod(tick+plane.getId()+bit,(age<100?3:7)*pressure)==0)for(int i=0;i<(age<100?2:1);i++){
                Vec3 d=direction(random).scale(.06).add(travel.scale(.55)).add(0,-.03,0);
                if(VehicleAtmosphere.admit(level,at,.04F))engine.add(new SparkParticle(level,at.x,at.y,at.z,d.x,d.y,d.z,8+random.nextInt(6),.028F));
            }
            if(age<240&&Math.floorMod(tick+plane.getId()+bit*3,3*pressure)==0)
                smoke(level,at,travel.scale(.2),.14F,.2F+.4F*(1-age/240F),36,.7F);
            if(age<60&&Math.floorMod(tick+plane.getId()+bit*5,2*pressure)==0)flame(level,at,.35F,.8F*(1-age/60F));
        }
    }

    private static boolean active(ClientLevel level,Vec3 at,double range) {
        return level!=null&&at!=null&&WreckSequenceEffects.enabled()
                &&Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(at)<=range*range;
    }
    private static Vec3 direction(RandomSource random) {
        double y=random.nextDouble()*2-1,a=random.nextDouble()*Math.PI*2,r=Math.sqrt(1-y*y);
        return new Vec3(Math.cos(a)*r,y,Math.sin(a)*r);
    }
    private static void flame(ClientLevel level,Vec3 at,float radius,float alpha) {
        if(alpha>.02F&&VehicleAtmosphere.admit(level,at,radius))Minecraft.getInstance().particleEngine.add(new WreckFlameParticle(level,at.x,at.y,at.z,radius,alpha));
    }
    private static void smoke(ClientLevel level,Vec3 at,Vec3 motion,float gray,float alpha,int life,float scale) {
        if(alpha<=.02F||!VehicleAtmosphere.admit(level,at,.5F))return;
        var p=new SmokeParticle(level,at.x,at.y,at.z,motion.x,motion.y,motion.z);
        p.enhanced=true;p.disableCollision();p.setColor(gray,gray,gray*.96F);p.setAlphaF(alpha);p.setParticleMaxAge(life);
        p.diffusible=true;p.particleScale=scale;p.particleMaxScale=scale*3.5F;Minecraft.getInstance().particleEngine.add(p);
    }
    private static void sound(Vec3 at,SoundEvent event,float volume,float pitch) {
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(event,SoundSource.BLOCKS,volume,pitch,RandomSource.create(),at.x,at.y,at.z));
    }
}
