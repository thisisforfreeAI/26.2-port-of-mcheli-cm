package io.github.mcheliceport.client.effect;

import io.github.mcheliceport.client.particle.VehicleBloodEffects;
import java.util.ArrayDeque;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/** Small temporary splashes fitted to one surviving collision surface, never world block edits. */
@EventBusSubscriber(modid="mcheli_ce_port", value=Dist.CLIENT)
public final class BloodStains {
    // Reuse the original neutral wet-patch alpha mask; no NTM artwork.
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("mcheli_ce_port", "textures/effect/m8_fluid_stains.png");
    private static final ArrayList<Stain> MARKS = new ArrayList<>();
    private static final ArrayDeque<Pending> PENDING = new ArrayDeque<>();
    private static ClientLevel world;
    private static int cursor, rays;
    private record Pending(Vec3 origin, float width, int seed, long due) {}
    private record Stain(BlockPos pos, BlockState state, double x, double y, double z,
            double left, double right, double back, double front, float radius, int seed, long born, int light) {}
    private record Draw(float x0, float x1, float y, float z0, float z1,
            float u0, float u1, float v0, float v1, int color, int light) {}
    private BloodStains() {}
    public static void clear() { MARKS.clear(); PENDING.clear(); world=null; cursor=rays=0; }
    public static int count() { return MARKS.size(); }
    public static int rays() { return rays; }

    public static void queue(ClientLevel level, Vec3 feet, float width, int seed) {
        if (!VehicleBloodEffects.enabled()) return;
        if (world!=level) { clear(); world=level; }
        if (PENDING.size()>=32) return;
        // Explosion block updates must arrive before sampling the crater floor.
        PENDING.add(new Pending(feet.add(0,.5,0), width, seed, level.getGameTime()+2));
    }

    private static void sample(Pending pending, long now) {
        Vec3 origin=pending.origin;
        if (!world.hasChunkAt(BlockPos.containing(origin))
                || Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(origin)>48*48) return;
        rays++;
        var hit=world.clip(new ClipContext(origin, origin.add(0,-8,0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, CollisionContext.empty()));
        if (hit.getType()!=HitResult.Type.BLOCK || hit.getDirection()!=Direction.UP) return;
        var pos=hit.getBlockPos();
        if (!world.hasChunkAt(pos)) return;
        var state=world.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) return;
        Vec3 point=hit.getLocation();
        for (var box:state.getCollisionShape(world,pos).toAabbs()) {
            double top=pos.getY()+box.maxY;
            if (Math.abs(top-point.y)>.01 || point.x<pos.getX()+box.minX || point.x>pos.getX()+box.maxX
                    || point.z<pos.getZ()+box.minZ || point.z>pos.getZ()+box.maxZ) continue;
            if (MARKS.size()>=64) MARKS.removeFirst();
            MARKS.add(new Stain(pos.immutable(), state, point.x, top+.004, point.z,
                    pos.getX()+box.minX, pos.getX()+box.maxX, pos.getZ()+box.minZ, pos.getZ()+box.maxZ,
                    Math.clamp(.2F+pending.width*.12F,.2F,.5F), pending.seed, now,
                    LightCoordsUtil.getLightCoords(world,pos.above())));
            break;
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if (mc.level!=world || !VehicleBloodEffects.enabled()) { clear(); return; }
        if (world==null || mc.isPaused()) return;
        long now=world.getGameTime(); rays=0;
        while (!PENDING.isEmpty() && PENDING.peek().due<=now && rays<4) sample(PENDING.remove(),now);
        MARKS.removeIf(m -> now<m.born || now-m.born>=300);
        for (int i=0; i<8 && !MARKS.isEmpty(); i++) {
            int at=Math.floorMod(cursor++,MARKS.size()); var m=MARKS.get(at);
            if (!valid(m)) MARKS.remove(at);
        }
    }
    private static boolean valid(Stain m) { return world.hasChunkAt(m.pos) && world.getBlockState(m.pos)==m.state; }

    @SubscribeEvent public static void submit(SubmitCustomGeometryEvent event) {
        if (!VehicleBloodEffects.enabled() || MARKS.isEmpty() || world!=Minecraft.getInstance().level) return;
        var camera=event.getLevelRenderState().cameraRenderState.pos;
        long now=world.getGameTime(); var draws=new ArrayList<Draw>();
        for (var m:MARKS) {
            if (camera.distanceToSqr(m.x,m.y,m.z)>48*48 || !valid(m)) continue;
            double x0=Math.max(m.left,m.x-m.radius), x1=Math.min(m.right,m.x+m.radius);
            double z0=Math.max(m.back,m.z-m.radius), z1=Math.min(m.front,m.z+m.radius);
            float offset=(m.seed&1)*.5F, alpha=Math.clamp((300-(now-m.born))/80F,0,1)*.82F;
            draws.add(new Draw((float)(x0-camera.x),(float)(x1-camera.x),(float)(m.y-camera.y),
                    (float)(z0-camera.z),(float)(z1-camera.z), offset+(float)((x0-m.x)/m.radius+1)*.25F,
                    offset+(float)((x1-m.x)/m.radius+1)*.25F, (float)((z0-m.z)/m.radius+1)*.5F,
                    (float)((z1-m.z)/m.radius+1)*.5F, ((int)(255*alpha)<<24)|0x651515, m.light));
        }
        if (draws.isEmpty()) return;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.entityTranslucent(TEXTURE,false),(pose,buffer)->{
            for (var d:draws) {
                float[] xs={d.x0,d.x0,d.x1,d.x1}, zs={d.z0,d.z1,d.z1,d.z0};
                float[] us={d.u0,d.u0,d.u1,d.u1}, vs={d.v0,d.v1,d.v1,d.v0};
                for(int i=0;i<4;i++) buffer.addVertex(pose,xs[i],d.y,zs[i]).setColor(d.color).setUv(us[i],vs[i])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(d.light).setNormal(pose,0,1,0);
            }
        });
    }
}
