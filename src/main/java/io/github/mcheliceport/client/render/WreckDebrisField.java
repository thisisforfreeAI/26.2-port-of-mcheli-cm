package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.model.McheliModel;
import io.github.mcheliceport.client.model.WreckMesh;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The debris a crashed plane strews along its path: chunks of charred skin cut from its hull and its
 * sheared-off landing gear. On a fresh crash they are flung forward with the wreck's speed, bounce and
 * slide to rest; around a wreck first seen already down they lie settled behind it. They stay as long as
 * the wreck. Cosmetic meshes only: no entities, items or hitboxes.
 */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class WreckDebrisField {
    public static final int MAX_FIELDS=6,MAX_PIECES=72,MAX_VERTICES=36000,RAYS=3,GROUND_RAYS=24;
    static final Identifier SOOT=Identifier.fromNamespaceAndPath("mcheli_ce_port","textures/effect/m8_wreck_char.png");
    /** Skin chunks render darker than the hull's paint; gear keeps a little more of its colour. */
    static final int CHUNK_TINT=0xFF5E5A55,GEAR_TINT=0xFF77726B,SOOT_TINT=0xE0FFFFFF;
    private static final class Piece {
        final McheliModel.Group group,soot;final Identifier texture;final Matrix4f local;final DebrisMotion motion;
        final boolean gear;int light;
        Piece(McheliModel.Group group,Identifier texture,Matrix4f local,DebrisMotion motion,boolean gear,int light){
            this.group=group;this.soot=WreckMesh.sootOf(group);this.texture=texture;this.local=local;this.motion=motion;this.gear=gear;this.light=light;
        }
    }
    private static final class Field {final WreckScene.Scene scene;final AircraftEntity owner;final List<Piece> pieces=new ArrayList<>();
        Field(WreckScene.Scene scene,AircraftEntity owner){this.scene=scene;this.owner=owner;}}
    private static final Map<AircraftEntity,Field> FIELDS=new LinkedHashMap<>();
    private static ClientLevel world;private static int cursor;
    private WreckDebrisField(){}
    public static void clear(){FIELDS.clear();world=null;cursor=0;}
    public static int pieces(){return FIELDS.values().stream().mapToInt(f->f.pieces.size()).sum();}
    public static int pieces(AircraftEntity owner){var f=FIELDS.get(owner);return f==null?0:f.pieces.size();}
    public static boolean has(AircraftEntity owner){return FIELDS.containsKey(owner);}
    /**
     * Strews the field once, when the wreck has hit the ground. {@code hull} is the body draw's pose
     * (model to entity-relative) for the chunks; {@code gear} are the landing-gear draws the hulk lost.
     */
    public static void offer(AircraftEntity ac,Identifier texture,Matrix4f hull,List<WreckMesh.Chunk> chunks,List<AircraftPartPoses.Draw> gear){
        var mc=Minecraft.getInstance();if(mc.level!=world){clear();world=mc.level;}
        if(world==null||FIELDS.containsKey(ac)||!WreckScene.enabled())return;
        var scene=WreckScene.get(ac);if(scene==null||scene.impact==null||texture==null)return;
        if(FIELDS.size()>=MAX_FIELDS)FIELDS.remove(FIELDS.keySet().iterator().next());
        var field=new Field(scene,ac);FIELDS.put(ac,field);
        var random=net.minecraft.util.RandomSource.create(scene.seed);
        int vertices=FIELDS.values().stream().flatMap(f->f.pieces.stream()).mapToInt(p->p.group.vertexCount()).sum();
        var side=new Vec3(-scene.heading.z,0,scene.heading.x);
        var specs=new ArrayList<Object[]>();
        for(var c:chunks)specs.add(new Object[]{c.group(),new Matrix4f(hull).translate(c.center()),c.size(),false});
        for(var draw:gear)for(var g:draw.groups()){var b=bounds(g);if(b==null)continue;
            var center=new Vector3f(b[0]).add(b[1]).mul(.5F);float[] d=g.data().clone();
            for(int i=0;i+7<d.length;i+=8){d[i]-=center.x;d[i+1]-=center.y;d[i+2]-=center.z;}
            specs.add(new Object[]{new McheliModel.Group(g.name(),d),new Matrix4f(draw.pose()).translate(center),new Vector3f(b[1]).sub(b[0]).max(new Vector3f(.12F)),true});}
        for(var spec:specs){
            var group=(McheliModel.Group)spec[0];var pose=(Matrix4f)spec[1];var size=new Vector3f((Vector3f)spec[2]).mul(pose.getScale(new Vector3f()));boolean isGear=(boolean)spec[3];
            if(pieces()>=MAX_PIECES||vertices+group.vertexCount()>MAX_VERTICES)break;
            var at=pose.transformPosition(new Vector3f());
            var local=new Matrix4f().translation(-at.x,-at.y,-at.z).mul(pose);
            Vec3 start;Vec3 velocity;
            if(scene.fresh()){
                start=ac.position().add(at.x,at.y,at.z);
                double forward=Math.max(.25,scene.speed)*(.45+random.nextDouble()*.7);
                velocity=scene.heading.scale(forward).add(side.scale((random.nextDouble()-.5)*.3)).add(0,.14+random.nextDouble()*.24,0);
            }else{
                // Already down when first seen: lying where the slide left them, behind the hulk.
                double back=2+random.nextDouble()*Math.max(3,scene.length*1.3);
                var p=scene.impact.add(scene.heading.scale(-back)).add(side.scale((random.nextDouble()-.5)*scene.span*.8));
                int x=(int)Math.floor(p.x),z=(int)Math.floor(p.z);
                if(!world.hasChunkAt(new BlockPos(x,(int)p.y,z)))continue;
                start=new Vec3(p.x,world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)+size.y*.5+.3,p.z);velocity=new Vec3(0,-.05,0);
            }
            var block=BlockPos.containing(start);int light=world.hasChunkAt(block)?LightCoordsUtil.getLightCoords(world,block):0;
            var spin=new Vector3f((random.nextFloat()-.5F)*.5F,(random.nextFloat()-.5F)*.3F,(random.nextFloat()-.5F)*.5F);
            var motion=new DebrisMotion(start,velocity,scene.fresh()?spin:new Vector3f(),local.getNormalizedRotation(new Quaternionf()),size,scene.seed+field.pieces.size()*7919);
            field.pieces.add(new Piece(group,texture,local,motion,isGear,light));vertices+=group.vertexCount();
        }
    }
    private static Vector3f[] bounds(McheliModel.Group g){
        var d=g.data();if(d.length<24)return null;var lo=new Vector3f(Float.POSITIVE_INFINITY);var hi=new Vector3f(Float.NEGATIVE_INFINITY);
        for(int i=0;i+7<d.length;i+=8){var p=new Vector3f(d[i],d[i+1],d[i+2]);lo.min(p);hi.max(p);}return new Vector3f[]{lo,hi};
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.level!=world){clear();world=mc.level;}
        if(world==null||!WreckScene.enabled()){FIELDS.clear();return;}
        if(mc.isPaused())return;long tick=world.getGameTime();
        FIELDS.entrySet().removeIf(e->{var s=e.getValue().scene;return !WreckScene.scenes().contains(s)||s.goneTick>=0&&s.fade(tick)<=0||!e.getKey().isDestroyed()&&!e.getKey().isRemoved();});
        var moving=new ArrayList<Piece>();
        for(var f:FIELDS.values())for(var p:f.pieces){
            // Resting pieces tick too (it costs nothing), so they check the ground under them now and then.
            if(p.motion.phase==DebrisMotion.Phase.REST){p.motion.step();continue;}
            if(!p.motion.moving()&&p.motion.phase!=DebrisMotion.Phase.RIP)continue;
            p.motion.step();moving.add(p);
            if(p.motion.age()%10==0){var block=BlockPos.containing(p.motion.position);if(world.hasChunkAt(block))p.light=LightCoordsUtil.getLightCoords(world,block);}
            if(p.motion.phase==DebrisMotion.Phase.SLIDE)
                io.github.mcheliceport.client.particle.DebrisImpactEffects.scrape(world,p.motion.position.add(0,-p.motion.height(),0),p.motion.velocity,tick,f.scene.seed);
        }
        // Pieces at the ground are swept every tick (up to GROUND_RAYS); the rest share RAYS round-robin.
        var swept=Collections.newSetFromMap(new IdentityHashMap<Piece,Boolean>());
        for(var p:moving)if(swept.size()<GROUND_RAYS&&p.motion.moving()&&LoosePanels.nearGround(world,p.motion)){swept.add(p);sweep(p.motion);}
        for(var f:FIELDS.values())for(var p:f.pieces)if(swept.size()<GROUND_RAYS&&p.motion.checkSupport()){swept.add(p);sweep(p.motion);}
        for(int i=0,tries=0;i<RAYS&&!moving.isEmpty()&&tries<moving.size();tries++){
            var p=moving.get(Math.floorMod(cursor++,moving.size()));if(!p.motion.moving()||swept.contains(p))continue;i++;sweep(p.motion);
        }
    }
    private static void sweep(DebrisMotion m){
        Vec3 from=m.sweepFrom(),to=m.sweepTo();
        if(!world.hasChunkAt(BlockPos.containing(from))||!world.hasChunkAt(BlockPos.containing(to)))return;
        var hit=world.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,
                m.phase==DebrisMotion.Phase.SUNK?ClipContext.Fluid.NONE:ClipContext.Fluid.ANY,CollisionContext.empty()));
        if(hit.getType()!=HitResult.Type.BLOCK){m.clear();return;}
        if(hit.isInside()){m.blocked();return;}
        boolean water=world.getFluidState(hit.getBlockPos()).is(net.minecraft.tags.FluidTags.WATER);
        var n=new Vec3(hit.getDirection().getStepX(),hit.getDirection().getStepY(),hit.getDirection().getStepZ());
        Vec3 before=m.velocity;var contact=m.contact(hit.getLocation(),n,water);
        if(contact==DebrisMotion.Event.BOUNCE||contact==DebrisMotion.Event.LAND)
            io.github.mcheliceport.client.particle.DebrisImpactEffects.strike(world,hit.getLocation(),before,m.impact,.6F,cursor);
        else if(contact==DebrisMotion.Event.SPLASH)io.github.mcheliceport.client.particle.VehicleWaterEffects.splash(world,hit.getLocation(),m.impact);
    }
    @SubscribeEvent public static void submit(SubmitCustomGeometryEvent event){
        if(FIELDS.isEmpty()||world!=Minecraft.getInstance().level||!WreckScene.enabled())return;
        var camera=event.getLevelRenderState().cameraRenderState.pos;long now=world.getGameTime();
        float partial=Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for(var f:FIELDS.values()){
            float fade=f.scene.fade(now);if(fade<=0)continue;boolean thermal=ThermalVision.drawsWhite(f.owner);
            for(var p:f.pieces){
                if(camera.distanceToSqr(p.motion.position)>64*64||p.motion.phase==DebrisMotion.Phase.SUNK&&p.motion.age()>80)continue;
                Vec3 at=p.motion.at(partial).subtract(camera);
                var transform=new Matrix4f().translation((float)at.x,(float)at.y,(float)at.z).rotate(p.motion.rotation(partial)).mul(p.local);
                int alpha=(int)(255*fade)<<24;int light=thermal?LightCoordsUtil.FULL_BRIGHT:p.light;
                int color=alpha|(thermal?0xFFFFFF:(p.gear?GEAR_TINT:CHUNK_TINT)&0xFFFFFF);
                Identifier texture=thermal?ThermalVision.WHITE:p.texture;
                event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(),RenderTypes.entityTranslucent(texture,false),(pose,buffer)->{
                    var local=pose.copy();local.mulPose(transform);ModelVertices.emit(buffer,local,List.of(p.group),color,light,false);
                });
                if(thermal)continue;
                int soot=((int)((SOOT_TINT>>>24)*fade)<<24)|(SOOT_TINT&0xFFFFFF);
                event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(),RenderTypes.entityTranslucent(SOOT,false),(pose,buffer)->{
                    var local=pose.copy();local.mulPose(transform);ModelVertices.emit(buffer,local,List.of(p.soot),soot,light,false);
                });
            }
        }
    }
}
