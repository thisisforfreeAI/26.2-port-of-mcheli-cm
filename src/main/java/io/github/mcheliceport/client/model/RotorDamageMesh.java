package io.github.mcheliceport.client.model;

import java.util.Arrays;
import org.joml.Vector3f;

/** Bounded loader-built damage variants. The original mesh is never modified. */
public final class RotorDamageMesh {
    private RotorDamageMesh(){}
    public record Variant(McheliModel.Group group,int bentVertices,int chippedTriangles){}
    public static Variant build(McheliModel.Group source,Vector3f hub,Vector3f axis,boolean severe){
        if(source.vertexCount()>24000||source.vertexCount()<3||!hub.isFinite()||!axis.isFinite()||axis.lengthSquared()<.01F)return null;
        axis=new Vector3f(axis).normalize();var d=source.data();float radius=0;var arm=new Vector3f();
        for(int i=0;i+7<d.length;i+=8){var radial=new Vector3f(d[i],d[i+1],d[i+2]).sub(hub);radial.fma(-radial.dot(axis),axis);
            if(radial.lengthSquared()>radius*radius){radius=radial.length();arm.set(radial).normalize();}}
        if(radius<.4F)return null;
        float[] out=new float[d.length];int at=0,bent=0,chipped=0;
        for(int t=0;t+23<d.length;t+=24){
            var center=new Vector3f((d[t]+d[t+8]+d[t+16])/3,(d[t+1]+d[t+9]+d[t+17])/3,(d[t+2]+d[t+10]+d[t+18])/3).sub(hub);
            center.fma(-center.dot(axis),axis);float along=center.dot(arm);
            if(severe&&along>radius*.9F&&t/24%3!=0){chipped++;continue;}
            int start=at;boolean changed=false,trimmed=false;
            for(int k=0;k<3;k++){
                int i=t+k*8;System.arraycopy(d,i,out,at,8);
                var radial=new Vector3f(d[i],d[i+1],d[i+2]).sub(hub);float reach=radial.dot(arm)/radius;
                float weight=Math.clamp((reach-.52F)/.48F,0,1);weight*=weight;
                if(severe&&reach>.88F){float cut=(reach-.88F)*radius;out[at]-=arm.x*cut;out[at+1]-=arm.y*cut;out[at+2]-=arm.z*cut;trimmed=true;changed=true;}
                if(weight>0){float bend=radius*(severe?.055F:.018F)*weight;out[at]-=axis.x*bend;out[at+1]-=axis.y*bend;out[at+2]-=axis.z*bend;bent++;changed=true;}
                at+=8;
            }
            if(trimmed)chipped++;
            if(changed){
                var normal=new Vector3f(out[start+8]-out[start],out[start+9]-out[start+1],out[start+10]-out[start+2])
                        .cross(out[start+16]-out[start],out[start+17]-out[start+1],out[start+18]-out[start+2]);
                if(normal.lengthSquared()>1e-12F){normal.normalize();for(int k=0;k<3;k++){int i=start+k*8;out[i+5]=normal.x;out[i+6]=normal.y;out[i+7]=normal.z;}}
            }
        }
        return new Variant(new McheliModel.Group(source.name()+"_m8_rotor_"+(severe?2:1),Arrays.copyOf(out,at)),bent,chipped);
    }
    public static int stage(float damage){return damage>.8F?2:damage>.55F?1:0;}
    public static float wobble(float damage,float time,int id){return stage(damage)==0?0:(float)Math.sin(time*.8+id)*.008F*Math.clamp(damage,0,1);}
}
