package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.model.McheliModel;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Cosmetic panel movement and short-lived mesh debris. No hitboxes, items or gameplay entities. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class LoosePanels {
    private static final Map<McheliModel.Group,LoosePanelLook.Spec> SPECS=new WeakHashMap<>();
    private static final Map<AircraftEntity,State> STATES=new LinkedHashMap<>();
    private static final List<Piece> PIECES=new ArrayList<>();
    private static ClientLevel world;private static long birthTick=Long.MIN_VALUE;private static int births,collisionCursor;
    private static final class State {
        final LoosePanelLook.Trigger trigger=new LoosePanelLook.Trigger();
        McheliModel.Group detached;int damage,occurrence;
    }
    private static final class Piece {
        final AircraftEntity owner;final McheliModel.Group group;final Identifier texture;final Matrix4f local;
        Vec3 position,previous,velocity,sampled;final float phase;final int light;
        int age,restAge,life=60;boolean scar;boolean bounced,settled;
        Piece(AircraftEntity owner,McheliModel.Group group,Identifier texture,Matrix4f local,Vec3 position,int light){
            this.owner=owner;this.group=group;this.texture=texture;this.local=local;this.position=this.previous=this.sampled=position;
            this.velocity=owner.getDeltaMovement().scale(.3).add(position.subtract(owner.position()).multiply(1,0,1).normalize().scale(.12)).add(0,.22,0);this.light=light;phase=(owner.getId()%17)*.2F;
        }
    }
    private LoosePanels(){}
    public static void wreckPiece(AircraftEntity owner,Identifier texture,McheliModel.Group group,Matrix4f pose,Vector3f center){
        scatter(owner,texture,group,pose,center,new Vec3(0,.22,0));
    }
    public static boolean scatter(AircraftEntity owner,Identifier texture,McheliModel.Group group,Matrix4f pose,Vector3f center,Vec3 impulse){
        var mc=Minecraft.getInstance();if(mc.level!=world){clear();world=mc.level;}
        long tick=world.getGameTime();if(birthTick!=tick){birthTick=tick;births=0;}
        int vertices=PIECES.stream().mapToInt(p->p.group.vertexCount()).sum();
        if(!enabled()||PIECES.size()>=8||births>=2||group.vertexCount()<3||group.vertexCount()>12000||vertices+group.vertexCount()>24000)return false;
        var at=pose.transformPosition(new Vector3f(center));var position=owner.position().add(at.x,at.y,at.z);
        int light=world.hasChunkAt(BlockPos.containing(position))?LightCoordsUtil.getLightCoords(world,BlockPos.containing(position)):0;
        var local=new Matrix4f().translation(-at.x,-at.y,-at.z).mul(pose);var piece=new Piece(owner,group,texture,local,position,light);piece.life=900;piece.velocity=piece.velocity.add(impulse).add(0,-.22,0);PIECES.add(piece);births++;return true;
    }
    private static boolean enabled(){return EffectQuality.enhanced()&&ClientConfig.get(ClientConfig.VEHICLE_DAMAGE_DETAILS);}
    public static void clear(){STATES.clear();PIECES.clear();world=null;birthTick=Long.MIN_VALUE;births=0;collisionCursor=0;}
    private static LoosePanelLook.Spec spec(McheliModel.Group group){
        String name=group.name();
        if(!(name.regionMatches(true,0,"$hatch",0,6)||name.regionMatches(true,0,"$canopy",0,7)
                ||name.regionMatches(true,0,"$panel",0,6)||name.regionMatches(true,0,"$wb",0,3)))return null;
        if(!SPECS.containsKey(group)){if(SPECS.size()>=1024)SPECS.clear();SPECS.put(group,LoosePanelLook.spec(group));}
        return SPECS.get(group);
    }
    public static void extract(AircraftEntity entity,AircraftRenderState render){
        var mc=Minecraft.getInstance();if(mc.level!=world){clear();world=mc.level;}
        if(!enabled()||render.far||render.texture==null
                ||mc.gameRenderer.mainCamera().position().distanceToSqr(entity.position())>32*32)return;
        if(STATES.size()>=64&&!STATES.containsKey(entity))return;
        var state=STATES.computeIfAbsent(entity,k->new State());long tick=world.getGameTime();
        int damage=entity.getDamageTaken(),maxHp=entity.getMaxHP();
        if(state.detached!=null&&damage<state.damage-Math.max(1,maxHp*.05F)){
            state.detached=null;PIECES.removeIf(p->p.owner==entity);
        }
        boolean detach=state.trigger.tick(damage,maxHp);
        if(state.detached==null&&(maxHp<=0||damage<=maxHp*.65F))return;
        AircraftPartPoses.Draw chosen=null;McheliModel.Group selected=null;LoosePanelLook.Spec best=null;
        int scanned=0,selectedOccurrence=0;var occurrences=new IdentityHashMap<McheliModel.Group,Integer>();
        for(var draw:render.draws)for(var group:draw.groups()){
            int occurrence=occurrences.getOrDefault(group,0);occurrences.put(group,occurrence+1);
            if(++scanned>256)break;
            var s=spec(group);if(s!=null&&(best==null||s.rank()<best.rank())){chosen=draw;selected=group;selectedOccurrence=occurrence;best=s;}
        }
        if(birthTick!=tick){birthTick=tick;births=0;}
        if(detach&&selected!=null&&PIECES.size()<8&&births<2){
            Vector3f center=chosen.pose().transformPosition(new Vector3f(best.center()));
            Vec3 position=entity.position().add(center.x,center.y,center.z);
            var block=BlockPos.containing(position);int light=world.hasChunkAt(block)?LightCoordsUtil.getLightCoords(world,block):0;
            Matrix4f local=new Matrix4f().translation(-center.x,-center.y,-center.z).mul(chosen.pose());
            PIECES.add(new Piece(entity,selected,render.texture,local,position,light));births++;
            state.detached=selected;state.damage=damage;state.occurrence=selectedOccurrence;
        }
        float fraction=maxHp>0?(float)damage/maxHp:0;
        var draws=new ArrayList<AircraftPartPoses.Draw>();occurrences.clear();
        for(var draw:render.draws){
            var unchanged=new ArrayList<McheliModel.Group>();
            for(var group:draw.groups()){
                int occurrence=occurrences.getOrDefault(group,0);occurrences.put(group,occurrence+1);
                if(group==state.detached&&occurrence==state.occurrence){PanelInteriors.add(render,draw.pose(),group);continue;}
                var s=spec(group);
                if(s==null){unchanged.add(group);continue;}
                float angle=LoosePanelLook.rattle(fraction,tick,entity.getId()+group.name().hashCode());
                if(angle==0){unchanged.add(group);continue;}
                var c=s.center();var pose=new Matrix4f(draw.pose()).translate(c).rotateZ(angle).rotateX(angle*.45F).translate(-c.x,-c.y,-c.z);
                draws.add(new AircraftPartPoses.Draw(pose,List.of(group)));
            }
            if(!unchanged.isEmpty())draws.add(new AircraftPartPoses.Draw(draw.pose(),List.copyOf(unchanged)));
        }
        render.draws.clear();render.draws.addAll(draws);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(world!=mc.level||!enabled()){clear();return;}if(world==null||mc.isPaused())return;
        long tick=world.getGameTime();STATES.entrySet().removeIf(e->e.getKey().isRemoved()||e.getKey().level()!=world||mc.gameRenderer.mainCamera().position().distanceToSqr(e.getKey().position())>96*96);
        PIECES.removeIf(p->p.owner.isRemoved()||p.life>60&&!p.owner.isDestroyed()||++p.age>p.life);
        for(var p:PIECES){p.previous=p.position;if(!p.settled){p.position=p.position.add(p.velocity);p.velocity=p.velocity.multiply(.98,1,.98).add(0,-.025,0);}}
        // Round-robin swept segments: at most two world rays per tick, even with eight pieces.
        for(int i=0;i<2&&!PIECES.isEmpty();i++){
            var p=PIECES.get(Math.floorMod(collisionCursor++,PIECES.size()));if(p.settled)continue;
            if(!world.hasChunkAt(BlockPos.containing(p.position))||!world.hasChunkAt(BlockPos.containing(p.sampled)))continue;
            var hit=world.clip(new ClipContext(p.sampled,p.position,ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,CollisionContext.empty()));
            if(hit.getType()==HitResult.Type.BLOCK){
                if(world.getFluidState(hit.getBlockPos()).is(net.minecraft.tags.FluidTags.WATER)){
                    io.github.mcheliceport.client.particle.VehicleWaterEffects.splash(world,hit.getLocation(),p.velocity.length());
                    p.position=hit.getLocation().add(0,-.15,0);p.settled=true;p.restAge=p.age;p.age=Math.max(p.age,p.life-5);p.velocity=Vec3.ZERO;p.sampled=p.position;continue;
                }
                var n=new Vec3(hit.getDirection().getStepX(),hit.getDirection().getStepY(),hit.getDirection().getStepZ());
                p.position=hit.getLocation().add(n.scale(.1));p.previous=p.position;
                if(p.bounced){p.settled=true;p.restAge=p.age;p.velocity=Vec3.ZERO;if(!p.scar){io.github.mcheliceport.client.effect.FluidStains.add(world,p.position.add(0,.2,0),false);p.scar=true;}p.age=Math.max(p.age,Math.min(42,p.life-18));}
                else{p.bounced=true;p.velocity=p.velocity.subtract(n.scale(1.25*p.velocity.dot(n))).scale(.3);}
            }
            p.sampled=p.position;
        }
    }
    @SubscribeEvent public static void submit(SubmitCustomGeometryEvent event){
        if(!enabled()||PIECES.isEmpty()||world!=Minecraft.getInstance().level)return;
        var camera=event.getLevelRenderState().cameraRenderState.pos;
        float partial=Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for(var p:PIECES){
            if(camera.distanceToSqr(p.position)>40*40)continue;
            Vec3 at=p.previous.lerp(p.position,partial).subtract(camera);float age=p.age+partial;
            var transform=new Matrix4f().translation((float)at.x,(float)at.y,(float)at.z)
                    .rotateXYZ((p.settled?p.restAge:age)*.09F+p.phase,(p.settled?p.restAge:age)*.05F,(p.settled?p.restAge:age)*.025F).mul(p.local);
            boolean thermal=ThermalVision.drawsWhite(p.owner);
            Identifier texture=thermal?ThermalVision.WHITE:p.texture;
            int pieceLight=thermal?LightCoordsUtil.FULL_BRIGHT:p.light;
            int color=((int)(255*Math.clamp((p.life-age)/15,0,1))<<24)|(thermal?0xFFFFFF:0xDADADA);
            event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(),RenderTypes.entityTranslucent(texture,false),(pose,buffer)->{
                var local=pose.copy();local.mulPose(transform);ModelVertices.emit(buffer,local,List.of(p.group),color,pieceLight,false);
            });
        }
    }
    public static int pieces(){return PIECES.size();}
    public static boolean detached(AircraftEntity e){var s=STATES.get(e);return s!=null&&s.detached!=null;}
}
