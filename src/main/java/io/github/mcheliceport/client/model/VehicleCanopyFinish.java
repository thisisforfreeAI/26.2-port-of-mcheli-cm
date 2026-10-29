package io.github.mcheliceport.client.model;

import io.github.mcheliceport.content.aircraft.AircraftInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/** Loader-only alpha evidence for mixed glass/frame canopies; never guesses from paint color. */
final class VehicleCanopyFinish {
    private VehicleCanopyFinish() {}
    static boolean canopy(String name) { return name.toLowerCase(Locale.ROOT).replace("$", "").startsWith("canopy"); }

    static List<VehicleGlassAlpha.Mask> load(ResourceManager resources, String dir, AircraftInfo info) {
        var textures=info.getTextureNames();
        // Shared model colors must be correct for every selectable livery. Unsupported packs stay paint.
        if(textures.isEmpty() || textures.size()>8)return List.of();
        List<VehicleGlassAlpha.Mask> result=new ArrayList<>(textures.size());
        for(String name:textures){
            var id=Identifier.tryParse("mcheli:textures/"+dir+"/"+name+".png");
            if(id==null)return List.of();
            try(var stream=resources.open(id);var input=ImageIO.createImageInputStream(stream)){
                var readers=ImageIO.getImageReaders(input);
                if(!readers.hasNext())return List.of();
                var reader=readers.next();
                try{
                    reader.setInput(input,true,true);int w=reader.getWidth(0),h=reader.getHeight(0);
                    if(w<=0||h<=0||w>2048||h>2048||(long)w*h>1048576)return List.of();
                    var image=reader.read(0);byte[] alpha=new byte[w*h];
                    for(int y=0;y<h;y++)for(int x=0;x<w;x++)alpha[y*w+x]=(byte)(image.getRGB(x,y)>>>24);
                    result.add(new VehicleGlassAlpha.Mask(w,h,alpha));
                }finally{reader.dispose();}
            }catch(java.io.IOException|RuntimeException ignored){return List.of();}
        }
        return List.copyOf(result);
    }

    static boolean glass(float[] data,int triangle,List<VehicleGlassAlpha.Mask> masks){
        if(masks.isEmpty()||triangle<0||triangle+24>data.length)return false;
        float u=(data[triangle+3]+data[triangle+11]+data[triangle+19])/3;
        float v=(data[triangle+4]+data[triangle+12]+data[triangle+20])/3;
        for(var mask:masks)for(int corner=0;corner<3;corner++){
            int i=triangle+corner*8;
            // Sample inside each triangle corner, leaving solid frames and alpha-cut holes alone.
            int alpha=mask.alpha(u*.2F+data[i+3]*.8F,v*.2F+data[i+4]*.8F);
            if(alpha<16||alpha>=240)return false;
        }
        return true;
    }
}
