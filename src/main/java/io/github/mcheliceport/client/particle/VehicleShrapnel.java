package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.*;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Cosmetic three-face metal pieces; no gameplay entities, block reads or collision queries. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class VehicleShrapnel {
    private static final ArrayDeque<WeakReference<BlastFragmentParticle>> LIVE=new ArrayDeque<>();
    private static ClientLevel world;
    private static long sampleTick=Long.MIN_VALUE;
    private static int births;
    private VehicleShrapnel(){}
    private static int cap(){return switch(ClientConfig.get(ClientConfig.EFFECT_BUDGET)){case LOW->16;case BALANCED->32;case HIGH->48;};}
    public static void clear(){for(var ref:LIVE){var p=ref.get();if(p!=null)p.remove();}LIVE.clear();world=null;births=0;sampleTick=Long.MIN_VALUE;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(world!=mc.level||!EffectQuality.enhanced()||!ClientConfig.get(ClientConfig.VEHICLE_DAMAGE_DETAILS)){clear();world=mc.level;}
        LIVE.removeIf(r->{var p=r.get();return p==null||!p.isAlive();});
        while(LIVE.size()>cap()){var p=LIVE.removeFirst().get();if(p!=null)p.remove();}
    }
    public static void emit(ClientLevel level,Vec3 position,Vec3 incoming,Vec3 normal,Vec3 vehicleMotion,boolean cannon,int seed){
        if(!EffectQuality.enhanced()||!ClientConfig.get(ClientConfig.VEHICLE_DAMAGE_DETAILS))return;
        if(world!=level){clear();world=level;}
        long now=level.getGameTime();if(now!=sampleTick){sampleTick=now;births=0;}
        if(Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(position)>40*40)return;
        LIVE.removeIf(r->{var p=r.get();return p==null||!p.isAlive();});
        int count=cannon?4:1;if(EffectPerformance.pressure()>0)count=Math.max(1,count/(1+EffectPerformance.pressure()));
        for(int i=0;i<count&&births<16&&LIVE.size()<cap();i++){
            float radius=cannon?.06F:.035F;
            if(!EffectAdmission.admit(level,position.x,position.y,position.z,false,radius*1.75F))break;
            var velocity=VehicleShrapnelLook.velocity(incoming,normal,seed,i,cannon).add(vehicleMotion.scale(.25));
            var start=position.add(normal.scale(.035));
            var p=new BlastFragmentParticle(level,start.x,start.y,start.z,velocity.x,velocity.y,velocity.z,
                    Blocks.IRON_BLOCK.defaultBlockState(),BlockPos.containing(position),Double.NEGATIVE_INFINITY,radius,cannon?28:20,false);
            Minecraft.getInstance().particleEngine.add(p);LIVE.addLast(new WeakReference<>(p));births++;
        }
    }
    public static int liveCount(){return LIVE.size();}
}
