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
    private static ClientLevel world;private static long budgetTick=Long.MIN_VALUE;
    private static int impacts,surges,sounds,totalImpacts,totalSurges;
    private DebrisImpactEffects() {}
    public static void clear(){world=null;budgetTick=Long.MIN_VALUE;impacts=surges=sounds=totalImpacts=totalSurges=0;}
    private static void budget(ClientLevel level){if(world!=level){clear();world=level;}if(budgetTick!=level.getGameTime()){budgetTick=level.getGameTime();impacts=surges=sounds=0;}}
    private static float[] tint(ClientLevel level,BlockPos at,net.minecraft.world.level.block.state.BlockState state){
        if(state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)||state.is(net.minecraft.world.level.block.Blocks.PODZOL))return new float[]{.48F,.38F,.25F};
        if(state.is(net.minecraft.world.level.block.Blocks.SNOW)||state.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK))return new float[]{.93F,.95F,.98F};
        return ClientEffects.groundTint(level,at,state,.69F,.65F,.56F);
    }
    /** A few low, broken dust fans when the wreck's suspension gives way. */
    public static boolean settle(ClientLevel level,io.github.mcheliceport.entity.aircraft.AircraftEntity ac){
        if(!WreckSequenceEffects.enabled())return false;budget(level);if(surges>=2)return false;
        var scene=io.github.mcheliceport.client.render.WreckScene.get(ac);
        if(!ac.onGround()&&(scene==null||scene.impact==null))return false;
        boolean emitted=false;int seed=ac.getUUID().hashCode();float span=Math.clamp(ac.getBbWidth()*.45F,.7F,2.5F);
        for(int i=0;i<3;i++){
            double angle=seed*.17+i*2.3,dx=Math.cos(angle),dz=Math.sin(angle);
            var at=ac.position().add(dx*span,.6,dz*span);
            if(!level.hasChunkAt(BlockPos.containing(at)))continue;
            var hit=level.clip(new net.minecraft.world.level.ClipContext(at,at.add(0,-1.8,0),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,
                    net.minecraft.world.phys.shapes.CollisionContext.empty()));
            if(hit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK||hit.isInside()||hit.getDirection()!=net.minecraft.core.Direction.UP)continue;
            var state=level.getBlockState(hit.getBlockPos());if(!state.getFluidState().isEmpty())continue;
            var p=hit.getLocation().add(0,.07,0);var color=tint(level,hit.getBlockPos(),state);
            if(VehicleAtmosphere.admit(level,p,1)){
                Minecraft.getInstance().particleEngine.add(new BlastGroundDustParticle(level,p.x,p.y,p.z,dx*.18,dz*.18,
                        .8F+span*.25F,color[0],color[1],color[2],24,.36F));emitted=true;
            }
        }
        if(emitted){surges++;totalSurges++;}return emitted;
    }
    /** {@code speed} is the contact speed in blocks per tick; {@code span} is the fragment's largest size. */
    public static void strike(ClientLevel level,Vec3 at,Vec3 velocity,double speed,float span,int seed) {
        if(!WreckEffects.refined()||speed<.08)return;
        budget(level);if(impacts>=4)return;impacts++;totalImpacts++;
        float strength=(float)Math.clamp(speed/1.2,.15,1);
        var engine=Minecraft.getInstance().particleEngine;
        var below=BlockPos.containing(at.add(0,-.2,0));var state=level.getBlockState(below);
        int dust=Math.round(2+5*strength);
        if(state.getRenderShape()!=RenderShape.INVISIBLE)for(int i=0;i<dust;i++) {
            double a=seed*1.7+i*2.39,r=span*.25*(.4+.6*((i*37+seed)%7)/6.0);
            var p=at.add(Math.cos(a)*r,.05,Math.sin(a)*r);
            if(!VehicleAtmosphere.admit(level,p,.12F))break;
            engine.add(new BlockDustParticle(level,p.x,p.y,p.z,Math.cos(a)*.12*strength+velocity.x*.2,.08+.18*strength,Math.sin(a)*.12*strength+velocity.z*.2,state,below));
        }
        float radius=Math.clamp(.5F+span*.3F,.6F,2.2F);
        var color=tint(level,below,state);
        if(state.getRenderShape()!=RenderShape.INVISIBLE&&state.getFluidState().isEmpty())for(int i=0;i<2;i++){
            if(!VehicleAtmosphere.admit(level,at,radius))break;
            double angle=seed*.17+i*2.6;
            engine.add(new BlastGroundDustParticle(level,at.x,at.y+.08,at.z,
                    velocity.x*.16+Math.cos(angle)*.14*strength,velocity.z*.16+Math.sin(angle)*.14*strength,
                    radius,color[0],color[1],color[2],26+(int)(12*strength),.25F+.17F*strength));
        }
        if(speed>.25&&sounds<2){sounds++;Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(SoundEvents.ANVIL_LAND,SoundSource.BLOCKS,
                .15F+.35F*strength,(float)Math.clamp(1.1-span*.08,.5,1),RandomSource.create(seed),at.x,at.y,at.z));}
        int sparks=speed>.35&&state.getDestroySpeed(level,below)>1?Math.round(2+4*strength):0;
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
        var below=BlockPos.containing(at.add(0,-.2,0));var state=level.getBlockState(below);
        if(state.getDestroySpeed(level,below)>1&&VehicleAtmosphere.admit(level,at,.04F))
            engine.add(new SparkParticle(level,at.x,at.y+.03,at.z,-velocity.x*.6+Math.sin(tick*.7+seed)*.05,.08,-velocity.z*.6+Math.cos(tick*.7+seed)*.05,8,.035F));
        if(state.getRenderShape()!=RenderShape.INVISIBLE&&VehicleAtmosphere.admit(level,at,.12F))
            engine.add(new BlockDustParticle(level,at.x,at.y+.05,at.z,-velocity.x*.3,.06,-velocity.z*.3,state,below));
    }
    public static int impacts(){return totalImpacts;}public static int surges(){return totalSurges;}
}
