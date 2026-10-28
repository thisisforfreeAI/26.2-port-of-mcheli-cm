package io.github.mcheliceport.client.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.Vec3;

/** Ground strikes and scraping of torn airframe sections: block dust, a low dust puff, metal sparks and a clang. */
public final class DebrisImpactEffects {
    private DebrisImpactEffects() {}
    /** {@code speed} is the contact speed in blocks per tick; {@code span} is the fragment's largest size. */
    public static void strike(ClientLevel level,Vec3 at,Vec3 velocity,double speed,float span,int seed) {
        if(!WreckEffects.refined()||speed<.08)return;
        float strength=(float)Math.clamp(speed/1.2,.15,1);
        var engine=Minecraft.getInstance().particleEngine;
        var below=BlockPos.containing(at.add(0,-.2,0));var state=level.getBlockState(below);
        int dust=Math.round(2+5*strength);
        if(state.getRenderShape()!=RenderShape.INVISIBLE)for(int i=0;i<dust;i++) {
            double a=seed*1.7+i*2.39,r=span*.25*(.4+.6*((i*37+seed)%7)/6.0);
            var p=at.add(Math.cos(a)*r,.05,Math.sin(a)*r);
            if(!EffectAdmission.admit(level,p.x,p.y,p.z,false))break;
            engine.add(new BlockDustParticle(level,p.x,p.y,p.z,Math.cos(a)*.12*strength+velocity.x*.2,.08+.18*strength,Math.sin(a)*.12*strength+velocity.z*.2,state,below));
        }
        if(VehicleAtmosphere.admit(level,at,.6F*span)) {
            var puff=new SmokeParticle(level,at.x,at.y+.1,at.z,velocity.x*.12,.02,velocity.z*.12);
            puff.enhanced=true;puff.coherentDrift=true;puff.disableCollision();puff.useBillow(SmokeAtlas.WISPS);
            puff.setColor(.52F,.48F,.42F);puff.setAlphaF(.22F*strength);puff.setParticleMaxAge(30+(int)(30*strength));
            puff.particleScale=1.2F+span*.25F;puff.particleMaxScale=3+span*.8F;puff.diffusible=true;
            engine.add(puff);
        }
        if(speed>.25)Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(SoundEvents.ANVIL_LAND,SoundSource.BLOCKS,
                .15F+.35F*strength,(float)Math.clamp(1.1-span*.08,.5,1),RandomSource.create(seed),at.x,at.y,at.z));
        int sparks=speed>.35?Math.round(2+4*strength):0;
        for(int i=0;i<sparks;i++) {
            if(!VehicleAtmosphere.admit(level,at,.04F))break;
            double a=seed*.9+i*1.83;
            engine.add(new SparkParticle(level,at.x,at.y+.05,at.z,velocity.x*.35+Math.cos(a)*.14,.1+.12*strength,velocity.z*.35+Math.sin(a)*.14,10+i%3*4,.04F));
        }
    }
    /** A fragment skidding along the ground throws a thin spray of sparks and grit behind it. */
    public static void scrape(ClientLevel level,Vec3 at,Vec3 velocity,long tick,int seed) {
        double speed=velocity.horizontalDistance();
        if(!WreckEffects.refined()||speed<.12||Math.floorMod(tick+seed,2+EffectPerformance.pressure())!=0)return;
        var engine=Minecraft.getInstance().particleEngine;
        if(VehicleAtmosphere.admit(level,at,.04F))
            engine.add(new SparkParticle(level,at.x,at.y+.03,at.z,-velocity.x*.6+Math.sin(tick*.7+seed)*.05,.08,-velocity.z*.6+Math.cos(tick*.7+seed)*.05,8,.035F));
        var below=BlockPos.containing(at.add(0,-.2,0));var state=level.getBlockState(below);
        if(state.getRenderShape()!=RenderShape.INVISIBLE&&EffectAdmission.admit(level,at.x,at.y,at.z,false))
            engine.add(new BlockDustParticle(level,at.x,at.y+.05,at.z,-velocity.x*.3,.06,-velocity.z*.3,state,below));
    }
}
