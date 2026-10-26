package io.github.mcheliceport.client.model;

import java.util.*;
import org.joml.Vector3f;

/** Audited stock whips only. Unrelated triangles and all source UVs remain intact. */
public final class SmallMotionMesh {
    public record Bounds(float x0,float y0,float z0,float x1,float y1,float z1){
        boolean contains(float[] d,int i){return d[i]>=x0&&d[i]<=x1&&d[i+1]>=y0&&d[i+1]<=y1&&d[i+2]>=z0&&d[i+2]<=z1;}
    }
    public record Whip(McheliModel.Group lower,McheliModel.Group upper,Vector3f base,Vector3f middle){}
    public record Parts(McheliModel.Group fixed,List<Whip> whips){}
    private SmallMotionMesh(){}
    public static List<Bounds> profile(String name){return switch(name){
        case "m1a2"->List.of(new Bounds(1.37F,2.70F,-3.23F,1.44F,5.26F,-3.16F),new Bounds(-1.48F,2.70F,-3.23F,-1.42F,5.26F,-3.16F));
        case "t-90"->List.of(new Bounds(.42F,1.98F,-.98F,.50F,2.87F,-.90F));default->List.of();};}
    public static Parts build(String name,McheliModel.Group source){
        var bounds=profile(name);if(bounds.isEmpty()||!source.name().equals("$weapon0")||source.vertexCount()>60000)return null;
        var fixed=new ArrayList<Float>();var selected=new ArrayList<List<float[]>>();for(var b:bounds)selected.add(new ArrayList<>());
        var d=source.data();
        for(int t=0;t+23<d.length;t+=24){int match=-1;
            for(int b=0;b<bounds.size();b++)if(bounds.get(b).contains(d,t)&&bounds.get(b).contains(d,t+8)&&bounds.get(b).contains(d,t+16)){match=b;break;}
            if(match<0){for(int i=t;i<t+24;i++)fixed.add(d[i]);continue;}
            for(int k=0;k<3;k++)selected.get(match).add(Arrays.copyOfRange(d,t+k*8,t+k*8+8));
        }
        var whips=new ArrayList<Whip>();
        for(int b=0;b<bounds.size();b++){
            var vertices=selected.get(b);if(vertices.isEmpty())continue;var box=bounds.get(b);float middle=(box.y0+box.y1)*.5F;
            var lower=new ArrayList<Float>();var upper=new ArrayList<Float>();
            for(int t=0;t+2<vertices.size();t+=3){var tri=vertices.subList(t,t+3);polygon(clip(tri,middle,false),lower);polygon(clip(tri,middle,true),upper);}
            whips.add(new Whip(group("$m8_whip_base",lower),group("$m8_whip_tip",upper),new Vector3f((box.x0+box.x1)*.5F,box.y0,(box.z0+box.z1)*.5F),new Vector3f((box.x0+box.x1)*.5F,middle,(box.z0+box.z1)*.5F)));
        }
        return whips.isEmpty()?null:new Parts(group(source.name(),fixed),List.copyOf(whips));
    }
    private static List<float[]> clip(List<float[]> input,float y,boolean above){
        var out=new ArrayList<float[]>();if(input.isEmpty())return out;var prev=input.getLast();boolean pin=above?prev[1]>=y:prev[1]<=y;
        for(var next:input){boolean nin=above?next[1]>=y:next[1]<=y;
            if(pin!=nin){float f=(y-prev[1])/(next[1]-prev[1]);var edge=new float[8];for(int i=0;i<8;i++)edge[i]=prev[i]+(next[i]-prev[i])*f;out.add(edge);}
            if(nin)out.add(next);prev=next;pin=nin;
        }return out;
    }
    private static void polygon(List<float[]> p,List<Float> out){for(int i=1;i+1<p.size();i++)for(var v:new float[][]{p.getFirst(),p.get(i),p.get(i+1)})for(float f:v)out.add(f);}
    private static McheliModel.Group group(String n,List<Float> a){float[] d=new float[a.size()];for(int i=0;i<d.length;i++)d[i]=a.get(i);return new McheliModel.Group(n,d);}
    public static float sway(float time,int seed,boolean moving){return (float)(Math.sin(time*.16+seed)*.7+Math.sin(time*.39+seed*.3)*.3)*(moving?.025F:.006F);}
}
