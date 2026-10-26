package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.client.model.*;
import io.github.mcheliceport.client.render.*;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.effect.VehicleImpactLook;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

/** Up to 32 warm mounts; no new packets, firing logic, ammunition costs or barrel glow. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class VehicleWeaponHeat {
    private record Key(AircraftEntity vehicle,String weapon,int mount){}
    private static final class State {
        final WeaponHeatLook look=new WeaponHeatLook();final Map<McheliModel.Group,Vec3> groups=new IdentityHashMap<>();
        Vec3 muzzle,renderedMuzzle;long seen,renderedTick;State(Vec3 p){muzzle=p;}
    }
    private static final Map<Key,State> STATES=new LinkedHashMap<>();private static ClientLevel world;
    private VehicleWeaponHeat(){}
    public static void clear(){STATES.clear();world=null;}
    private static boolean enabled(){return EffectQuality.enhanced()&&ClientConfig.get(ClientConfig.WEAPON_HEAT);}
    public static void shot(Entity shooter,int mount,WeaponInfo info,Vec3 position,float yaw,float pitch){
        if(!enabled()||!(shooter instanceof AircraftEntity ac)||!(ac.level() instanceof ClientLevel level)||info==null||info.useHBM
                ||!(info.type.equals("machinegun1")||info.type.equals("machinegun2")))return;
        if(Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(position)>48*48)return;
        if(world!=level){clear();world=level;}
        float distance=0;if(info.listMuzzleFlash!=null)for(var flash:info.listMuzzleFlash)distance=Math.max(distance,flash.dist);
        position=position.add(Vec3.directionFromRotation(pitch,yaw).scale(Math.clamp(distance,0,12)));
        String family=info.name;for(var part:ac.getAcInfo().partWeapon)if(Arrays.asList(part.name).contains(info.name)){family=part.modelName;break;}
        var key=new Key(ac,family,mount);if(STATES.size()>=32&&!STATES.containsKey(key))return;
        var local=VehicleImpactLook.body(ac.getYaw(),ac.getPitch(),ac.getRoll()).invert().transformPosition(new Vector3f((float)(position.x-ac.getX()),(float)(position.y-ac.getY()),(float)(position.z-ac.getZ())));
        var state=STATES.computeIfAbsent(key,k->new State(Vec3.ZERO));state.muzzle=new Vec3(local.x,local.y,local.z);state.seen=level.getGameTime();
        state.look.shot(state.seen,info.power);
        if(state.groups.isEmpty()){
            var models=AircraftModels.peek(ac.getAcInfo());if(models!=null)for(var part:ac.getAcInfo().partWeapon){
                if(!Arrays.asList(part.name).contains(info.name))continue;
                for(var group:models.part(part.modelName))if(slender(group))state.groups.put(group,tip(group));
                for(var child:part.child)for(var group:models.part(child.modelName))if(slender(group))state.groups.put(group,tip(group));
            }
        }
    }
    private static Vec3 tip(McheliModel.Group group){
        var d=group.data();float z=-Float.MAX_VALUE;Vec3 tip=Vec3.ZERO;
        for(int i=0;i+7<d.length;i+=8)if(d[i+2]>z){z=d[i+2];tip=new Vec3(d[i],d[i+1],d[i+2]);}return tip;
    }
    public static Vec3 hotPoint(AircraftEntity vehicle){
        for(var entry:STATES.entrySet())if(entry.getKey().vehicle==vehicle&&entry.getValue().renderedMuzzle!=null)return entry.getValue().renderedMuzzle;
        return vehicle.position().add(0,1.5,2);
    }
    private static boolean slender(McheliModel.Group group){
        if(group.vertexCount()>6000)return false;String n=group.name().toLowerCase(Locale.ROOT);
        if(n.contains("barrel")||n.contains("cannon")||n.contains("gun"))return true;
        var d=group.data();float x0=999,y0=999,z0=999,x1=-999,y1=-999,z1=-999;
        for(int i=0;i+7<d.length;i+=8){x0=Math.min(x0,d[i]);y0=Math.min(y0,d[i+1]);z0=Math.min(z0,d[i+2]);x1=Math.max(x1,d[i]);y1=Math.max(y1,d[i+1]);z1=Math.max(z1,d[i+2]);}
        float[] span={x1-x0,y1-y0,z1-z0};Arrays.sort(span);return span[2]>.35F&&span[2]<9&&span[2]>span[1]*2.4F;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(world!=mc.level||!enabled()){clear();return;}if(world==null||mc.isPaused())return;
        long now=world.getGameTime();int emitted=0;
        var it=STATES.entrySet().iterator();while(it.hasNext()){
            var entry=it.next();var key=entry.getKey();var state=entry.getValue();var ac=key.vehicle;
            state.look.tick(now);if(ac.isRemoved()||ac.isDestroyed()||now-state.seen>1200||state.look.heat()<.01F){it.remove();continue;}
            if(mc.gameRenderer.mainCamera().position().distanceToSqr(ac.position())>48*48)continue;
            var p=VehicleImpactLook.body(ac.getYaw(),ac.getPitch(),ac.getRoll()).transformPosition(new Vector3f((float)state.muzzle.x,(float)state.muzzle.y,(float)state.muzzle.z));
            Vec3 at=state.renderedMuzzle!=null&&now-state.renderedTick<4?state.renderedMuzzle:ac.position().add(p.x,p.y,p.z);
            Vec3 forward=io.github.mcheliceport.util.McheliMath.rotVec3(0,1,0,-ac.getYaw(),-ac.getPitch(),-ac.getRoll());
            if(state.look.heat()>.6F)EngineHeat.add(world,Long.MIN_VALUE|((long)ac.getId()<<8)|key.mount,at,forward,ac.getDeltaMovement(),.55F,state.look.heat(),false);
            if(emitted<4&&state.look.cooling(now)&&Math.floorMod(now+ac.getId(),12*(1+EffectPerformance.pressure()))==0&&VehicleAtmosphere.admit(world,at,.24F)){
                var smoke=new SmokeParticle(world,at.x,at.y,at.z,0,.012,0);smoke.enhanced=true;smoke.coherentDrift=true;smoke.disableCollision();
                smoke.setColor(.6F,.62F,.64F);smoke.setAlphaF(.28F*state.look.heat());smoke.setParticleMaxAge(20);smoke.diffusible=true;smoke.particleScale=.8F;smoke.particleMaxScale=2.4F;
                mc.particleEngine.add(smoke);emitted++;
            }
        }
    }
    public static void extract(AircraftEntity vehicle,AircraftRenderState render){
        if(!enabled()||render.thermal||Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(vehicle.position())>40*40)return;
        for(var entry:STATES.entrySet())if(entry.getKey().vehicle==vehicle&&entry.getValue().look.heat()>.15F){
            var state=entry.getValue();for(var draw:render.draws)for(var group:draw.groups())if(state.groups.containsKey(group)){
                var end=state.groups.get(group);var tip=draw.pose().transformPosition(new Vector3f((float)end.x,(float)end.y,(float)end.z));state.renderedMuzzle=vehicle.position().add(tip.x,tip.y,tip.z);state.renderedTick=vehicle.level().getGameTime();
                var skin=VehicleSkinMeshes.get(group);if(skin!=null)VehicleDetailLayers.add(render,draw.pose(),render.texture,List.of(skin.raised()),state.look.color());
            }
        }
    }
    public static float hottest(AircraftEntity vehicle){return STATES.entrySet().stream().filter(e->e.getKey().vehicle==vehicle).map(e->e.getValue().look.heat()).max(Float::compare).orElse(0F);}
}
