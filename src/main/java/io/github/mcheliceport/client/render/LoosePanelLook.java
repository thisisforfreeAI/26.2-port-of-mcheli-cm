package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.model.McheliModel;
import org.joml.Vector3f;

/** Only small, explicitly authored removable parts qualify; never slice the body mesh. */
public final class LoosePanelLook {
    private LoosePanelLook() {}
    public record Spec(Vector3f center, float span, int rank) {}
    public static Spec spec(McheliModel.Group group) {
        String name=group.name().toLowerCase(java.util.Locale.ROOT);
        if(!name.matches("\\$(hatch|canopy|panel|wb)[0-9]*") || group.vertexCount()<3 || group.vertexCount()>600) return null;
        var lo=new Vector3f(Float.POSITIVE_INFINITY);var hi=new Vector3f(Float.NEGATIVE_INFINITY);
        var data=group.data();
        for(int i=0;i+7<data.length;i+=8){var p=new Vector3f(data[i],data[i+1],data[i+2]);if(!p.isFinite())return null;lo.min(p);hi.max(p);}
        float span=Math.max(hi.x-lo.x,Math.max(hi.y-lo.y,hi.z-lo.z));
        if(span<=.05F||span>4.5F)return null;
        return new Spec(lo.add(hi).mul(.5F),span,name.startsWith("$hatch")?0:name.startsWith("$canopy")?1:2);
    }
    public static final class Trigger {
        private boolean initialized; private int previous;
        public boolean tick(int damage,int maxHp) {
            boolean crossed=initialized&&maxHp>0&&damage>maxHp*.9&&previous<=maxHp*.9;
            initialized=true;previous=damage;return crossed;
        }
    }
    public static float rattle(float damage,long tick,int seed) {
        return (float)Math.sin(tick*2.7+seed)*Math.clamp((damage-.65F)/.35F,0,1)*.035F;
    }
}
