package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** A crashed aircraft's canopy bursts into glass shards that spray, bounce and settle, instead of flying off whole. */
public final class WreckGlass {
    private WreckGlass(){}
    /** Shards for a canopy {@code size} blocks across, centred at {@code at}, carried along with {@code motion}. */
    public static int shatter(ClientLevel level,Vec3 at,Vec3 motion,float size,int seed){
        if(!EffectQuality.enhanced()||!ClientConfig.get(ClientConfig.VEHICLE_DAMAGE_DETAILS))return 0;
        if(Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(at)>48*48)return 0;
        var block=BlockPos.containing(at);if(!level.hasChunkAt(block))return 0;
        double floor=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,block.getX(),block.getZ());
        int count=Math.clamp(Math.round(size*9),8,22)/(1+EffectPerformance.pressure());int made=0;
        var random=net.minecraft.util.RandomSource.create(seed);
        var glass=Blocks.GLASS.defaultBlockState();
        for(int i=0;i<count;i++){
            var start=at.add((random.nextDouble()-.5)*size,random.nextDouble()*.3,(random.nextDouble()-.5)*size);
            if(!EffectAdmission.admit(level,start.x,start.y,start.z,false,.06F))break;
            double a=random.nextDouble()*Math.PI*2,out=.06+random.nextDouble()*.14;
            var v=motion.scale(.35).add(Math.cos(a)*out,.12+random.nextDouble()*.22,Math.sin(a)*out);
            float radius=.03F+random.nextFloat()*.05F;
            Minecraft.getInstance().particleEngine.add(new BlastFragmentParticle(level,start.x,start.y,start.z,v.x,v.y,v.z,
                    glass,block,floor,radius,44+random.nextInt(16),false));
            made++;
        }
        return made;
    }
}
