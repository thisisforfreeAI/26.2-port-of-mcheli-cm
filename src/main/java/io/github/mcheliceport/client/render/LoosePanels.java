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
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Cosmetic panel movement and short-lived mesh debris. No hitboxes, items or gameplay entities. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class LoosePanels {
    private static final Map<McheliModel.Group,LoosePanelLook.Spec> SPECS=new WeakHashMap<>();
    private static final Identifier METAL=Identifier.withDefaultNamespace("textures/block/iron_block.png");
    /** A structural section's cut plane in model space, and a point on it where the tear burns. */
    public record Tear(int axis,float cut,int outward,Vector3f edge){}
    private static final Map<AircraftEntity,State> STATES=new LinkedHashMap<>();
    private static final List<Piece> PIECES=new ArrayList<>();
    private static ClientLevel world;private static long birthTick=Long.MIN_VALUE;private static int births,collisionCursor;
    private static final class State {
        final LoosePanelLook.Trigger trigger=new LoosePanelLook.Trigger();
        McheliModel.Group detached;int damage,occurrence;
    }
    private static final class Piece {
        final AircraftEntity owner;final McheliModel.Group group;final Identifier texture;final Matrix4f local;
        final DebrisMotion motion;final float span;final int seed;int light;
        int age,restAge=-1,life=60,airframeBit,controlSlot=-1;boolean scar,skin;
        Tear tear;Vector3f edge;
        Piece(AircraftEntity owner,McheliModel.Group group,Identifier texture,Matrix4f local,Vec3 position,int light){
            this.owner=owner;this.group=group;this.texture=texture;this.local=local;this.light=light;seed=owner.getId()*31+PIECES.size();
            Vec3 velocity=owner.getDeltaMovement().scale(.3).add(position.subtract(owner.position()).multiply(1,0,1).normalize().scale(.12)).add(0,.22,0);
            var part=io.github.mcheliceport.client.model.BreakupMeshes.part(group);
            var size=part==null?new Vector3f(.6F):new Vector3f(part.bounds().high()).sub(part.bounds().low()).mul(local.getScale(new Vector3f()));
            span=Math.max(size.x,Math.max(size.y,size.z));
            motion=new DebrisMotion(position,velocity,new Vector3f(),local.getNormalizedRotation(new Quaternionf()),size,seed);
        }
        Vec3 velocity(){return motion.velocity;}
        void velocity(Vec3 v){motion.velocity=v;}
        boolean settled(){return motion.phase==DebrisMotion.Phase.REST||motion.phase==DebrisMotion.Phase.SUNK;}
    }
    private LoosePanels(){}
    public static void wreckPiece(AircraftEntity owner,Identifier texture,McheliModel.Group group,Matrix4f pose,Vector3f center){
        scatter(owner,texture,group,pose,center,new Vec3(0,.22,0));
    }
    public static boolean scatter(AircraftEntity owner,Identifier texture,McheliModel.Group group,Matrix4f pose,Vector3f center,Vec3 impulse){
        var mc=Minecraft.getInstance();if(mc.level!=world){clear();world=mc.level;}
        long tick=world.getGameTime();if(birthTick!=tick){birthTick=tick;births=0;}
        // Pieces kept around a hulk give way to fresh ones, oldest first.
        if(PIECES.size()>=8)for(var p:PIECES)if(kept(p)){PIECES.remove(p);break;}
        int vertices=PIECES.stream().mapToInt(p->p.group.vertexCount()).sum();
        if(!enabled()||PIECES.size()>=8||births>=2||group.vertexCount()<3||group.vertexCount()>12000||vertices+group.vertexCount()>24000)return false;
        var at=pose.transformPosition(new Vector3f(center));var position=owner.position().add(at.x,at.y,at.z);
        int light=world.hasChunkAt(BlockPos.containing(position))?LightCoordsUtil.getLightCoords(world,BlockPos.containing(position)):0;
        var local=new Matrix4f().translation(-at.x,-at.y,-at.z).mul(pose);var piece=new Piece(owner,group,texture,local,position,light);piece.life=900;piece.velocity(piece.velocity().add(impulse).add(0,-.22,0));inherit(piece);PIECES.add(piece);births++;return true;
    }
    /** A live airframe fragment keeps full flight velocity and disappears if that component is repaired. */
    public static boolean airframePiece(AircraftEntity owner,Identifier texture,McheliModel.Group group,
            Matrix4f pose,Vector3f center,Vec3 impulse,int bit){
        if(!scatter(owner,texture,group,pose,center,Vec3.ZERO))return false;
        // Long enough to fall from altitude; a resting section fades after a while (see tick()).
        var piece=PIECES.getLast();piece.airframeBit=bit;piece.life=600;
        var v=io.github.mcheliceport.effect.VehicleImpactLook.body(owner.getYaw(),owner.getPitch(),owner.getRoll())
                .transformDirection(new Vector3f((float)impulse.x,(float)impulse.y,(float)impulse.z));
        piece.velocity(io.github.mcheliceport.damage.AirframeDamage.fragmentMotion(owner.getDeltaMovement(),
                owner.position().subtract(new Vec3(owner.xo,owner.yo,owner.zo))).add(v.x,v.y,v.z));inherit(piece);return true;
    }
    /**
     * Marks the piece just admitted as a torn structural section: it is drawn closed (lining and cap)
     * and its torn edge sparks, burns and trails smoke while it falls.
     */
    public static void tear(Tear tear){
        if(PIECES.isEmpty()||tear==null)return;var p=PIECES.getLast();if(p.skin||p.tear!=null)return;
        p.tear=tear;p.edge=p.local.transformPosition(new Vector3f(tear.edge()));
        // Structural sections fold about the torn root for a few ticks before they let go.
        if(!p.skin&&p.controlSlot<0)p.motion.tear(new Vec3(p.edge.x,p.edge.y,p.edge.z));
        io.github.mcheliceport.client.particle.BreakawayEffects.tear(world,edge(p),p.velocity(),p.owner.getId());
    }
    private static Vec3 edge(Piece p){return p.motion.position.add(p.motion.rotated(new Vec3(p.edge.x,p.edge.y,p.edge.z)));}
    /** The aircraft's turn rate becomes the fragment's spin, plus the tangential speed of its offset. */
    private static void inherit(Piece piece){
        var owner=piece.owner;var m=piece.motion;
        var rate=DebrisMotion.angularVelocity(DebrisMotion.attitude(owner.yRotO,owner.xRotO,owner.prevRoll),
                DebrisMotion.attitude(owner.getYaw(),owner.getPitch(),owner.getRoll()));
        m.velocity=m.velocity.add(new Vec3(rate.x,rate.y,rate.z).cross(m.position.subtract(owner.position())));m.inherit(rate);
    }
    public static int airframePieces(AircraftEntity owner){return (int)PIECES.stream().filter(p->p.owner==owner&&p.airframeBit!=0).count();}
    public static boolean skinPiece(AircraftEntity owner,Identifier texture,McheliModel.Group group,
            Matrix4f pose,Vector3f center,Vec3 impulse,int bit){
        if(!airframePiece(owner,texture,group,pose,center,impulse,bit))return false;
        var piece=PIECES.getLast();piece.skin=true;piece.life=36;
        if(owner.isDestroyed())piece.airframeBit=0;
        return true;
    }
    public static int skinPieces(AircraftEntity owner){return (int)PIECES.stream().filter(p->p.owner==owner&&p.skin).count();}
    public static boolean controlPiece(AircraftEntity owner,Identifier texture,McheliModel.Group group,Matrix4f pose,Vector3f center,int slot){
        if(!scatter(owner,texture,group,pose,center,Vec3.ZERO))return false;
        var piece=PIECES.getLast();piece.controlSlot=slot;piece.life=240;
        piece.velocity(io.github.mcheliceport.damage.AirframeDamage.fragmentMotion(owner.getDeltaMovement(),
                owner.position().subtract(owner.xo,owner.yo,owner.zo)).add(0,-.035,0));inherit(piece);return true;
    }
    public static int controlPieces(AircraftEntity owner){return (int)PIECES.stream().filter(p->p.owner==owner&&p.controlSlot>=0).count();}
    private static boolean enabled(){return EffectQuality.enhanced()&&ClientConfig.get(ClientConfig.VEHICLE_DAMAGE_DETAILS);}
    public static void clear(){STATES.clear();PIECES.clear();world=null;birthTick=Long.MIN_VALUE;births=0;collisionCursor=0;}
    private static LoosePanelLook.Spec spec(McheliModel.Group group){
        String name=group.name();
        if(!(name.regionMatches(true,0,"$hatch",0,6)||name.regionMatches(true,0,"$canopy",0,7)
                ||name.regionMatches(true,0,"$panel",0,6)||name.regionMatches(true,0,"$wb",0,3)))return null;
        if(!SPECS.containsKey(group)){if(SPECS.size()>=1024)SPECS.clear();SPECS.put(group,LoosePanelLook.spec(group));}
        return SPECS.get(group);
    }
    public static void extract(AircraftEntity entity,AircraftRenderState render,float partialTicks){
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
                // Airflow shakes a damaged panel; a wreck lying on the ground has none, and its charred skin is drawn unshaken.
                float angle=entity.isDestroyed()?0:LoosePanelLook.rattle(fraction,tick+partialTicks,entity.getId()+group.name().hashCode());
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
        PIECES.removeIf(p->p.owner.isRemoved() || ++p.age>p.life || p.skin&&(!io.github.mcheliceport.client.particle.WreckSequenceEffects.enabled()
                ||p.airframeBit==0&&!p.owner.isDestroyed())
                || (p.controlSlot>=0 ? !DamagedControlSurfaces.missing(p.owner,p.controlSlot)
                : p.airframeBit!=0 ? !io.github.mcheliceport.client.particle.WreckSequenceEffects.enabled()
                || (io.github.mcheliceport.damage.AirframeDamage.missing(p.owner)&p.airframeBit)==0
                : p.life>60&&!p.owner.isDestroyed()));
        for(var p:PIECES){
            // Sections that came to rest by a hulk stay as long as it does.
            if(kept(p))p.life=Math.max(p.life,p.age+40);
            p.motion.step();
            if(p.age%10==0){var block=BlockPos.containing(p.motion.position);if(world.hasChunkAt(block))p.light=LightCoordsUtil.getLightCoords(world,block);}
            if(p.motion.phase==DebrisMotion.Phase.SLIDE)
                io.github.mcheliceport.client.particle.DebrisImpactEffects.scrape(world,p.motion.position.add(0,-p.motion.height(),0),p.velocity(),tick,p.seed);
            if(p.restAge<0&&(p.settled()||p.motion.phase==DebrisMotion.Phase.SLIDE))rest(p);
        }
        for(var p:PIECES)if(p.edge!=null&&p.age<p.life-10)
            io.github.mcheliceport.client.particle.BreakawayEffects.trail(world,edge(p),p.velocity(),p.age,p.restAge>=0?p.age-p.restAge:-1,p.owner.getId());
        // Pieces at the ground are swept every tick; the rest share two round-robin rays a tick.
        var swept=Collections.newSetFromMap(new IdentityHashMap<Piece,Boolean>());
        for(var p:PIECES)if(p.motion.moving()&&nearGround(world,p.motion)||p.motion.checkSupport()){swept.add(p);sweep(p);}
        int moving=(int)PIECES.stream().filter(p->p.motion.moving()&&!swept.contains(p)).count();
        for(int i=0,tries=0;i<2&&moving>0&&tries<PIECES.size();tries++){
            var p=PIECES.get(Math.floorMod(collisionCursor++,PIECES.size()));if(!p.motion.moving()||swept.contains(p))continue;i++;sweep(p);
        }
    }

    private static void sweep(Piece p){
        var m=p.motion;Vec3 from=m.sweepFrom(),to=m.sweepTo();
        if(!world.hasChunkAt(BlockPos.containing(from))||!world.hasChunkAt(BlockPos.containing(to)))return;
        var hit=world.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,
                m.phase==DebrisMotion.Phase.SUNK?ClipContext.Fluid.NONE:ClipContext.Fluid.ANY,CollisionContext.empty()));
        // A sweep that starts inside terrain (spawned in a hillside) cannot resolve a contact face.
        if(hit.getType()!=HitResult.Type.BLOCK){m.clear();return;}
        if(hit.isInside()){m.blocked();return;}
        boolean water=world.getFluidState(hit.getBlockPos()).is(net.minecraft.tags.FluidTags.WATER);
        var n=new Vec3(hit.getDirection().getStepX(),hit.getDirection().getStepY(),hit.getDirection().getStepZ());
        Vec3 before=m.velocity;var contact=m.contact(hit.getLocation(),n,water);
        switch(contact){
            case SPLASH -> {io.github.mcheliceport.client.particle.VehicleWaterEffects.splash(world,hit.getLocation(),m.impact);p.life=Math.min(p.life,p.age+60);}
            case BOUNCE,LAND -> io.github.mcheliceport.client.particle.DebrisImpactEffects.strike(world,hit.getLocation(),before,m.impact,p.span,p.seed+m.bounces());
            default -> {}
        }
    }
    /**
     * Whether a fragment is at, or within this tick's fall of, the top of the terrain under it. Such a
     * fragment is swept every tick: left to the round-robin it fell into the ground for several ticks
     * between sweeps, then popped back out and bounced, which made settling debris shake and flicker.
     */
    static boolean nearGround(net.minecraft.world.level.Level level,DebrisMotion m){
        Vec3 from=m.sweepFrom(),to=m.sweepTo();
        int top=Math.max(level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                        net.minecraft.util.Mth.floor(from.x),net.minecraft.util.Mth.floor(from.z)),
                level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                        net.minecraft.util.Mth.floor(to.x),net.minecraft.util.Mth.floor(to.z)));
        return top>=Math.min(from.y,to.y)-1-Math.abs(m.velocity.y);
    }
    private static boolean kept(Piece p){return p.restAge>=0&&!p.skin&&p.owner.isDestroyed()&&!p.owner.isRemoved()&&p.motion.phase!=DebrisMotion.Phase.SUNK;}
    /** A section at rest leaves a scorch, smolders, and fades well after the fight has moved on. */
    private static void rest(Piece p){
        p.restAge=p.age;
        if(p.motion.phase==DebrisMotion.Phase.SUNK)return;
        if(!p.scar&&!p.skin){io.github.mcheliceport.client.effect.FluidStains.add(world,p.motion.position.add(0,.2,0),false);p.scar=true;}
        p.life=Math.min(p.life,p.age+(p.skin?20:p.airframeBit!=0||p.tear!=null?300:200));
    }
    @SubscribeEvent public static void submit(SubmitCustomGeometryEvent event){
        if(!enabled()||PIECES.isEmpty()||world!=Minecraft.getInstance().level)return;
        var camera=event.getLevelRenderState().cameraRenderState.pos;
        float partial=Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for(var p:PIECES){
            if(camera.distanceToSqr(p.motion.position)>40*40)continue;
            Vec3 at=p.motion.at(partial).subtract(camera);float age=p.age+partial;
            var transform=new Matrix4f().translation((float)at.x,(float)at.y,(float)at.z).rotate(p.motion.rotation(partial)).mul(p.local);
            boolean thermal=ThermalVision.drawsWhite(p.owner);
            Identifier texture=thermal?ThermalVision.WHITE:p.texture;
            int pieceLight=thermal?LightCoordsUtil.FULL_BRIGHT:p.light;
            // Torn sections fell burning and wreck panels burnt with the hull: both are charred.
            boolean burnt=!thermal&&WreckAppearance.enabled()&&(p.tear!=null||p.owner.isDestroyed());
            boolean hulk=burnt&&p.owner.isDestroyed();
            int color=((int)(255*Math.clamp((p.life-age)/15,0,1))<<24)|(thermal?0xFFFFFF:hulk?0x8A857E:0xDADADA);
            event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(),RenderTypes.entityTranslucent(texture,false),(pose,buffer)->{
                var local=pose.copy();local.mulPose(transform);ModelVertices.emit(buffer,local,List.of(p.group),color,pieceLight,false);
            });
            if(burnt){
                int alpha=(color>>>24)&255;
                if(p.tear!=null&&!p.owner.isDestroyed()){
                    // A section torn off in flight is scorched at its root, not painted over: the fire spreads out slowly.
                    var bands=io.github.mcheliceport.client.model.BreakupMeshes.scorch(p.group,p.tear.axis(),p.tear.cut(),-p.tear.outward());
                    float charring=Math.min(1,p.age/240F);
                    if(bands!=null)for(int b=0;b<2;b++){
                        var soot=bands[b];int sootColor=((int)(alpha*(b==0?.55F+.35F*charring:.15F+.35F*charring))<<24)|0xFFFFFF;
                        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(),RenderTypes.entityTranslucent(WreckAppearance.CHAR,false),(pose,buffer)->{
                            var local=pose.copy();local.mulPose(transform);ModelVertices.emit(buffer,local,List.of(soot),sootColor,pieceLight,false);
                        });
                    }
                }else{
                    var soot=io.github.mcheliceport.client.model.WreckMesh.sootOf(p.group);int sootColor=((int)(alpha*.9F)<<24)|0xFFFFFF;
                    event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(),RenderTypes.entityTranslucent(WreckAppearance.CHAR,false),(pose,buffer)->{
                        var local=pose.copy();local.mulPose(transform);ModelVertices.emit(buffer,local,List.of(soot),sootColor,pieceLight,false);
                    });
                }
            }
            var shell=p.tear==null?null:io.github.mcheliceport.client.model.BreakupMeshes.shell(p.group,p.tear.axis(),p.tear.cut(),p.tear.outward());
            if(shell==null)continue;
            int alpha=color&0xFF000000;
            event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(),RenderTypes.entityTranslucent(texture,false),(pose,buffer)->{
                var local=pose.copy();local.mulPose(transform);
                ModelVertices.emit(buffer,local,List.of(shell.lining()),alpha|(thermal?0xFFFFFF:PlaneAirframeParts.LINING&0xFFFFFF),pieceLight,false);
            });
            event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(),RenderTypes.entityTranslucent(thermal?texture:METAL,false),(pose,buffer)->{
                var local=pose.copy();local.mulPose(transform);
                ModelVertices.emit(buffer,local,List.of(shell.cap()),alpha|(thermal?0xFFFFFF:PlaneAirframeParts.CAP&0xFFFFFF),pieceLight,false);
            });
        }
    }
    public static int pieces(){return PIECES.size();}
    /** Centre and phase of an aircraft's live structural fragments, for probes. */
    public static List<String> airframeMotion(AircraftEntity owner){
        return PIECES.stream().filter(p->p.owner==owner&&p.airframeBit!=0&&!p.skin).map(p->p.airframeBit+":"+p.motion.phase+"@"+p.motion.position).toList();
    }
    public static Vec3 airframePosition(AircraftEntity owner,int bit){
        for(var p:PIECES)if(p.owner==owner&&p.airframeBit==bit&&!p.skin)return p.motion.position;return null;
    }
    public static boolean detached(AircraftEntity e){var s=STATES.get(e);return s!=null&&s.detached!=null;}
}
