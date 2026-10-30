package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.model.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

/** Small inserts built off-thread. Existing cockpits are left open and visible. */
public final class PanelInteriors {
    private static final Map<McheliModel.Group,CompletableFuture<PanelInteriorMesh.Parts>> CACHE=new LinkedHashMap<>(16,.75F,true);
    private static final ExecutorService LOADER=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"mcheli-panel-interiors");t.setDaemon(true);return t;});
    private static final Identifier DARK=Identifier.withDefaultNamespace("textures/block/polished_deepslate.png"),METAL=Identifier.withDefaultNamespace("textures/block/iron_block.png");
    private static final McheliModel.Group CABLE;
    static {var box=new DetailBoxes();box.box(-.009F,0,-.009F,.009F,1,.009F);CABLE=box.group("$m8_cable");}
    private static int pending,renderedVertices,cables;

    private PanelInteriors(){}
    private static PanelInteriorMesh.Parts get(McheliModel.Group group){
        CompletableFuture<PanelInteriorMesh.Parts> future;
        synchronized(CACHE){future=CACHE.get(group);if(future==null){
            if(pending>=4)return null;
            if(CACHE.size()>=32){var it=CACHE.entrySet().iterator();boolean removed=false;while(it.hasNext())if(it.next().getValue().isDone()){it.remove();removed=true;break;}if(!removed)return null;}
            future=new CompletableFuture<>();CACHE.put(group,future);pending++;var result=future;
            LOADER.execute(()->{PanelInteriorMesh.Parts mesh=null;try{mesh=PanelInteriorMesh.build(group);}finally{result.complete(mesh);synchronized(CACHE){pending--;}}});
        }}return future.getNow(null);
    }
    public static void add(AircraftRenderState render,Matrix4f pose,McheliModel.Group panel){
        var mesh=get(panel);if(mesh==null)return;
        if(io.github.mcheliceport.config.ClientConfig.get(io.github.mcheliceport.config.ClientConfig.VEHICLE_SMALL_MOTION))addCables(render,pose,panel);

        VehicleDetailLayers.add(render,pose,DARK,List.of(mesh.dark()),0xFF25292C);
        if(VehicleDetailLayers.add(render,pose,METAL,List.of(mesh.metal()),0xFFB8B0A0))renderedVertices=mesh.dark().vertexCount()+mesh.metal().vertexCount();
    }
    private static void addCables(AircraftRenderState render,Matrix4f pose,McheliModel.Group panel){
        // Bounds scan occurs on the loader through the insert; the rails give a cached anchor.
        var mesh=get(panel);if(mesh==null||mesh.metal().vertexCount()==0)return;
        var d=mesh.metal().data();float x=d[0],y=d[1],z=d[2];
        var mc=net.minecraft.client.Minecraft.getInstance();if(mc.level==null)return;
        float time=mc.level.getGameTime()+mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        cables=0;
        for(int wire=0;wire<2;wire++){
            var start=new org.joml.Vector3f(x+wire*.09F,y+.02F,z+.08F);var previous=new org.joml.Vector3f(start);
            for(int segment=1;segment<=4;segment++){
                float f=segment/4F;var next=new org.joml.Vector3f(start).add((float)Math.sin(time*.12+wire+panel.name().hashCode())*.035F*f,-.48F*f,.12F*f*f);
                var delta=new org.joml.Vector3f(next).sub(previous);float length=delta.length();
                var rotation=new org.joml.Quaternionf().rotationTo(new org.joml.Vector3f(0,1,0),new org.joml.Vector3f(delta).normalize());
                var transform=new Matrix4f(pose).translate(previous).rotate(rotation).scale(1,length,1);
                if(VehicleDetailLayers.add(render,transform,DARK,List.of(CABLE),0xFF181B1D))cables++;
                previous=next;
            }
        }
    }
    public static int cables(){return cables;}
    public static int renderedVertices(){return renderedVertices;}
    public static void clear(){renderedVertices=cables=0;synchronized(CACHE){CACHE.clear();}}
}
