package io.github.mcheliceport.client.model;

import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** Bounded, off-render-thread PNG alpha masks for mixed glass/frame canopy groups. */
public final class VehicleGlassAlpha {
    private static final Map<Identifier,CompletableFuture<Mask>> CACHE=new LinkedHashMap<>();
    private static final ExecutorService LOADER=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"mcheli-glass-alpha");t.setDaemon(true);return t;});
    private static final Mask OPAQUE=new Mask(1,1,new byte[]{(byte)255});
    private static int running;
    private VehicleGlassAlpha(){}
    public record Mask(int width,int height,byte[] pixels) {
        public int alpha(float u,float v){
            if(!Float.isFinite(u)||!Float.isFinite(v))return 255;
            int x=Math.min(width-1,(int)((u-Math.floor(u))*width)),y=Math.min(height-1,(int)((v-Math.floor(v))*height));
            return pixels[y*width+x]&255;
        }
    }
    public static void clear(){synchronized(CACHE){CACHE.clear();}}
    /** Null means a bounded decode is pending; callers retry their existing pending hit. */
    public static Integer alpha(Identifier texture,float u,float v){
        CompletableFuture<Mask> future;
        synchronized(CACHE){
            future=CACHE.get(texture);
            if(future==null){
                if(running>=2)return null;
                if(CACHE.size()>=16){var it=CACHE.entrySet().iterator();boolean removed=false;
                    while(it.hasNext()){if(it.next().getValue().isDone()){it.remove();removed=true;break;}}if(!removed)return null;}
                var manager=Minecraft.getInstance().getResourceManager();
                future=new CompletableFuture<>();CACHE.put(texture,future);running++;
                var result=future;
                LOADER.execute(()->{
                    Mask mask=OPAQUE;
                    try(var stream=manager.open(texture);var input=ImageIO.createImageInputStream(stream)){
                        var readers=ImageIO.getImageReaders(input);
                        if(readers.hasNext()){
                            var reader=readers.next();
                            try{
                                reader.setInput(input,true,true);int w=reader.getWidth(0),h=reader.getHeight(0);
                                if(w>0&&h>0&&w<=2048&&h<=2048&&(long)w*h<=1048576){
                                    var image=reader.read(0);var bytes=new byte[w*h];
                                    for(int y=0;y<h;y++)for(int x=0;x<w;x++)bytes[y*w+x]=(byte)(image.getRGB(x,y)>>>24);
                                    mask=new Mask(w,h,bytes);
                                }
                            }finally{reader.dispose();}
                        }
                    }catch(java.io.IOException|RuntimeException ignored){/* Unknown textures stay painted metal. */}
                    finally{result.complete(mask);synchronized(CACHE){running--;}}
                });
            }
        }
        var mask=future.getNow(null);return mask==null?null:mask.alpha(u,v);
    }
}
