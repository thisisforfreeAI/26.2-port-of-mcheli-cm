package io.github.mcheliceport.client.model;

import org.joml.Vector3f;

/** An insert only for small named canopies and reasonably flat top hatches. */
public final class PanelInteriorMesh {
    private PanelInteriorMesh(){}
    public record Parts(McheliModel.Group dark,McheliModel.Group metal){}
    public static Parts build(McheliModel.Group panel){
        String name=panel.name().toLowerCase(java.util.Locale.ROOT);boolean canopy=name.startsWith("$canopy");
        if(!canopy&&!name.startsWith("$hatch")&&!name.startsWith("$panel")&&!name.startsWith("$enginecover")&&!name.startsWith("$cover"))return null;
        if(panel.vertexCount()<3||panel.vertexCount()>600)return null;
        var lo=new Vector3f(Float.POSITIVE_INFINITY);var hi=new Vector3f(Float.NEGATIVE_INFINITY);
        var d=panel.data();for(int i=0;i+7<d.length;i+=8){var p=new Vector3f(d[i],d[i+1],d[i+2]);if(!p.isFinite())return null;lo.min(p);hi.max(p);}
        float w=hi.x-lo.x,l=hi.z-lo.z,h=hi.y-lo.y;
        if(w<.3F||l<.3F||w>4.5F||l>4.5F||!canopy&&h>Math.max(w,l)*.3F)return null;
        var dark=new DetailBoxes();var metal=new DetailBoxes();float e=.035F,base=lo.y+.015F,top=canopy?base+.045F:Math.max(base+.08F,hi.y-.02F);
        if(!canopy){
            dark.box(lo.x,base,lo.z,hi.x,base+.025F,hi.z);
            dark.box(lo.x,base,lo.z,lo.x+e,top,hi.z);dark.box(hi.x-e,base,lo.z,hi.x,top,hi.z);
            dark.box(lo.x,base,lo.z,hi.x,top,lo.z+e);dark.box(lo.x,base,hi.z-e,hi.x,top,hi.z);
            metal.box(lo.x+w*.35F,base+.035F,lo.z+l*.25F,lo.x+w*.60F,base+.14F,lo.z+l*.65F);
            for(int i=1;i<=3;i++){float z=lo.z+l*i/4;metal.box(lo.x+w*.2F,base+.028F,z-.03F,hi.x-w*.2F,base+.07F,z+.03F);}
        }
        metal.box(lo.x,top,lo.z,lo.x+e,top+.025F,hi.z);metal.box(hi.x-e,top,lo.z,hi.x,top+.025F,hi.z);
        for(int i=0;i<3;i++){
            float x=lo.x+w*i/3;metal.box(x,top,lo.z,x+w*.22F,top+.03F+(i%2)*.018F,lo.z+e);
        }
        metal.box(lo.x+e,base,lo.z+.04F,lo.x+.13F,base+.1F,lo.z+.19F);
        metal.box(hi.x-.13F,base,lo.z+.04F,hi.x-e,base+.1F,lo.z+.19F);
        return new Parts(dark.group("$m8_interior"),metal.group("$m8_torn_edges"));
    }
}
