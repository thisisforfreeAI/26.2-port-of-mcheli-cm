package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.model.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

/** Small inserts built off-thread. Existing cockpits are left open and visible. */
public final class PanelInteriors {
    private static final Map<McheliModel.Group,CompletableFuture<PanelInteriorMesh.Parts>> CACHE=new LinkedHashMap<>();
    private static final ExecutorService LOADER=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"mcheli-panel-interiors");t.setDaemon(true);return t;});
    private static final Identifier DARK=Identifier.withDefaultNamespace("textures/block/polished_deepslate.png"),METAL=Identifier.withDefaultNamespace("textures/block/iron_block.png");
    private static int pending,renderedVertices;
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
        VehicleDetailLayers.add(render,pose,DARK,List.of(mesh.dark()),0xFF25292C);
        if(VehicleDetailLayers.add(render,pose,METAL,List.of(mesh.metal()),0xFFB8B0A0))renderedVertices=mesh.dark().vertexCount()+mesh.metal().vertexCount();
    }
    public static int renderedVertices(){return renderedVertices;}
    public static void clear(){renderedVertices=0;synchronized(CACHE){CACHE.clear();}}
}
