package io.github.mcheliceport.client.effect;

import io.github.mcheliceport.client.render.WreckScene;
import io.github.mcheliceport.config.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * Ground marks under a crashed plane or helicopter: a burn scar at the impact, a gouged groove along the
 * path it slid, and the fuel pool it burns in. They are cut to the top faces of the block columns beneath,
 * last as long as the wreck and fade after it. Client-only; no block writes.
 */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class WreckGround {
    static final Identifier TEXTURE=Identifier.fromNamespaceAndPath("mcheli_ce_port","textures/effect/m8_wreck_ground.png");
    public enum Kind {SCAR,GROOVE,POOL}
    /** A rectangle on the ground, {@code half} along {@code axis} and {@code width} across it. */
    record Mark(Kind kind,double x,double z,double y,double ax,double az,double half,double width,long born){}
    /** One clipped polygon on one block top: xz corners with uvs inside the mark's tile. */
    record Piece(double[] x,double[] z,double y,float[] u,float[] v,int light){}
    private static final class Marks {
        final List<Mark> pending=new ArrayList<>();final List<Mark> marks=new ArrayList<>();
        final Map<Mark,List<Piece>> pieces=new IdentityHashMap<>();int trail;boolean scar,pool;
    }
    private static final Map<WreckScene.Scene,Marks> MARKS=new IdentityHashMap<>();
    private static ClientLevel world;
    private static int columns;
    private WreckGround(){}
    public static void clear(){MARKS.clear();world=null;}
    public static int pieces(){return MARKS.values().stream().mapToInt(m->m.pieces.values().stream().mapToInt(List::size).sum()).sum();}
    /** Scar radius for a hull of this span, and the pool's final radius. */
    public static float scarRadius(float span){return Math.clamp(span*.32F,1.6F,4.5F);}
    public static float poolRadius(float span){return Math.clamp(span*.26F,1.3F,3.6F);}
    /** The pool spreads over its first second and a half. */
    public static float poolGrowth(long age){return age<0?0:Math.clamp(.35F+age/30F,0,1);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        if(mc.level!=world){clear();world=mc.level;}
        if(world==null||!WreckScene.enabled()){MARKS.clear();return;}
        if(mc.isPaused())return;
        long now=world.getGameTime();columns=0;
        MARKS.keySet().removeIf(s->!WreckScene.scenes().contains(s));
        for(var scene:WreckScene.scenes()){
            if(scene.impact==null)continue;
            var m=MARKS.computeIfAbsent(scene,k->new Marks());
            if(!m.scar){m.scar=true;var h=scene.heading;
                m.pending.add(new Mark(Kind.SCAR,scene.impact.x,scene.impact.z,scene.impact.y,h.x,h.z,scarRadius(scene.span),scarRadius(scene.span),now));}
            // Groove segments between successive trail points, gouged as wide as the fuselage sits.
            while(m.trail+1<scene.trail.size()){
                Vec3 a=scene.trail.get(m.trail),b=scene.trail.get(m.trail+1);m.trail++;
                double dx=b.x-a.x,dz=b.z-a.z,len=Math.hypot(dx,dz);if(len<.05)continue;
                m.pending.add(new Mark(Kind.GROOVE,(a.x+b.x)/2,(a.z+b.z)/2,Math.min(a.y,b.y),dx/len,dz/len,len/2+.04,Math.clamp(scene.span*.09,.55,1.4),now));
            }
            // The pool collects where the wreck comes to rest, once it has stopped sliding.
            if(!m.pool&&scene.fresh()&&scene.position!=null&&now-scene.impactTick>8&&scene.goneTick<0){
                var last=scene.trail.isEmpty()?scene.impact:scene.trail.getLast();
                if(scene.position.distanceToSqr(last)<.2||now-scene.impactTick>80){m.pool=true;var h=scene.heading;
                    m.pending.add(new Mark(Kind.POOL,scene.position.x,scene.position.z,scene.position.y,h.x,h.z,poolRadius(scene.span),poolRadius(scene.span),now));}
            }
            // Fitting is spread over ticks: at most 48 column reads a tick across every wreck.
            while(!m.pending.isEmpty()&&columns<48){var mark=m.pending.removeFirst();m.marks.add(mark);m.pieces.put(mark,fit(world,mark));}
        }
    }
    /** Cuts a mark to the top faces of the columns it covers; columns far above or below it are skipped. */
    static List<Piece> fit(ClientLevel level,Mark mark){
        var out=new ArrayList<Piece>();double reach=Math.hypot(mark.half,mark.width);
        int x0=(int)Math.floor(mark.x-reach),x1=(int)Math.floor(mark.x+reach),z0=(int)Math.floor(mark.z-reach),z1=(int)Math.floor(mark.z+reach);
        for(int bx=x0;bx<=x1;bx++)for(int bz=z0;bz<=z1;bz++){
            var poly=clip(mark,bx,bz);if(poly==null)continue;
            columns++;
            var top=new BlockPos(bx,(int)Math.floor(mark.y)+2,bz);if(!level.hasChunkAt(top))continue;
            int surface=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,bx,bz);
            if(Math.abs(surface-mark.y)>2.2)continue;
            var ground=new BlockPos(bx,surface-1,bz);var state=level.getBlockState(ground);
            if(!state.getFluidState().isEmpty()||state.isAir())continue;
            var shape=state.getCollisionShape(level,ground);if(shape.isEmpty())continue;
            double y=ground.getY()+shape.max(net.minecraft.core.Direction.Axis.Y)+.012+(mark.kind==Kind.POOL?.004:mark.kind==Kind.GROOVE?.002:0);
            int n=poly.length/2;double[] xs=new double[n],zs=new double[n];float[] us=new float[n],vs=new float[n];
            int tile=mark.kind.ordinal();
            for(int i=0;i<n;i++){
                xs[i]=poly[i*2];zs[i]=poly[i*2+1];
                double dx=xs[i]-mark.x,dz=zs[i]-mark.z;
                double along=dx*mark.ax+dz*mark.az,across=-dx*mark.az+dz*mark.ax;
                float u=(float)Math.clamp((across/mark.width+1)/2,0,1);
                // The groove tile repeats along its length, so successive segments meet without a seam.
                float v=(float)Math.clamp((along/mark.half+1)/2,0,1);
                us[i]=(tile+.004F+u*.992F)/3F;vs[i]=.002F+v*.996F;
            }
            out.add(new Piece(xs,zs,y,us,vs,LightCoordsUtil.getLightCoords(level,ground.above())));
        }
        return out;
    }
    /** The mark's rectangle clipped to the unit square of a block column, as an xz polygon, or null. */
    static double[] clip(Mark mark,int bx,int bz){
        // Wound like FluidStains' quads, so the cut faces up and is not culled.
        double[] poly={bx,bz,bx,bz+1,bx+1,bz+1,bx+1,bz};
        double[][] planes={{mark.ax,mark.az,mark.half},{-mark.ax,-mark.az,mark.half},{-mark.az,mark.ax,mark.width},{mark.az,-mark.ax,mark.width}};
        for(var p:planes){poly=cut(poly,p[0],p[1],p[0]*mark.x+p[1]*mark.z+p[2]);if(poly.length<6)return null;}
        return poly;
    }
    /** Sutherland-Hodgman against the half plane nx*x+nz*z <= d. */
    private static double[] cut(double[] poly,double nx,double nz,double d){
        var out=new ArrayList<Double>();int n=poly.length/2;
        for(int i=0;i<n;i++){
            double ax=poly[i*2],az=poly[i*2+1],bx=poly[(i+1)%n*2],bz=poly[(i+1)%n*2+1];
            double da=nx*ax+nz*az-d,db=nx*bx+nz*bz-d;
            if(da<=0){out.add(ax);out.add(az);}
            if(da<=0!=db<=0){double t=da/(da-db);out.add(ax+(bx-ax)*t);out.add(az+(bz-az)*t);}
        }
        double[] r=new double[out.size()];for(int i=0;i<r.length;i++)r[i]=out.get(i);return r;
    }
    /** Scars and grooves fade in over half a second; the pool darkens as it spreads. */
    static float alpha(Mark mark,long now,float fade){
        float in=Math.clamp((now-mark.born)/10F,0,1);
        return in*fade*(mark.kind==Kind.POOL?.92F:mark.kind==Kind.GROOVE?.95F:.9F);
    }
    @SubscribeEvent public static void submit(SubmitCustomGeometryEvent event){
        if(MARKS.isEmpty()||world!=Minecraft.getInstance().level||!WreckScene.enabled())return;
        var camera=event.getLevelRenderState().cameraRenderState.pos;long now=world.getGameTime();
        int cap=ClientConfig.get(ClientConfig.EFFECT_BUDGET)==ClientConfig.EffectBudget.LOW?160:600;
        record Draw(Piece piece,Mark mark,int color,float scale,double cx,double cz){}
        var draws=new ArrayList<Draw>();
        for(var e:MARKS.entrySet()){
            float fade=e.getKey().fade(now);if(fade<=0)continue;
            for(var mark:e.getValue().marks){
                if(camera.distanceToSqr(mark.x,mark.y,mark.z)>96*96)continue;
                int color=((int)(255*alpha(mark,now,fade))<<24)|0xFFFFFF;
                float scale=mark.kind==Kind.POOL?poolGrowth(now-mark.born):1;
                for(var p:e.getValue().pieces.getOrDefault(mark,List.of())){if(draws.size()>=cap)break;draws.add(new Draw(p,mark,color,scale,mark.x,mark.z));}
            }
        }
        if(draws.isEmpty())return;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(),RenderTypes.entityTranslucent(TEXTURE,false),(pose,buffer)->{
            for(var d:draws){
                var p=d.piece;int n=p.x.length;
                // A convex polygon as a fan of quads (the last corner repeats on triangles).
                for(int i=1;i+1<n;i+=2){
                    int[] idx={0,i,i+1,Math.min(i+2,n-1)};
                    for(int k:idx){
                        double x=d.cx+(p.x[k]-d.cx)*d.scale,z=d.cz+(p.z[k]-d.cz)*d.scale;
                        buffer.addVertex(pose,(float)(x-camera.x),(float)(p.y-camera.y),(float)(z-camera.z)).setColor(d.color)
                                .setUv(p.u[k],p.v[k]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(p.light).setNormal(pose,0,1,0);
                    }
                }
            }
        });
    }
}
