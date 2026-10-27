package io.github.mcheliceport.damage;

import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.BoundingBoxInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Saved/synced structural health drives presentation, without changing lift, collision or weapons. */
public final class AirframeDamage {
    public static final int LEFT=1, RIGHT=2, TAIL=4;
    private AirframeDamage() {}
    static List<DamageZone> zones(AircraftInfo info, AABB hull) {
        var result=new ArrayList<DamageZone>();
        for(int sign:new int[]{-1,1}) {
            var side=info.extraBoundingBox.stream().filter(b->b.boundingBoxType==BoundingBoxInfo.Type.DEFAULT
                    && b.offsetX*sign>Math.max(.75,info.bodyWidth*.45))
                    .max(Comparator.comparingDouble(b->b.offsetX*sign)).orElse(null);
            AABB box;
            if(side!=null)box=side.getBoundingBox();
            else {
                double edge=sign<0?hull.minX:hull.maxX;
                double width=Math.max(.4,(hull.maxX-hull.minX)*.18);
                double x=edge-sign*width*.5,z=hull.minZ+hull.getZsize()*.55;
                box=new AABB(x-width*.5,hull.minY,z-hull.getZsize()*.12,x+width*.5,hull.maxY,z+hull.getZsize()*.12);
            }
            result.add(zone(ComponentKind.WING,sign<0?0:1,box));
        }
        var rear=info.extraBoundingBox.stream().filter(b->b.boundingBoxType==BoundingBoxInfo.Type.DEFAULT)
                .min(Comparator.comparingDouble(b->b.offsetZ)).orElse(null);
        AABB tail=rear==null?new AABB(hull.minX*.35,hull.minY,hull.minZ,hull.maxX*.35,hull.maxY,hull.minZ+hull.getZsize()*.2):rear.getBoundingBox();
        result.add(zone(ComponentKind.AIRFRAME_TAIL,0,tail));
        return result;
    }
    private static DamageZone zone(ComponentKind kind,int index,AABB box) {
        return new DamageZone(kind,index,box.getCenter(),new Vec3(Math.max(.2,box.getXsize()*.5),
                Math.max(.2,box.getYsize()*.5),Math.max(.2,box.getZsize()*.5)),"");
    }
    /** Remote aircraft often have zero simulation velocity after interpolation; use observed travel. */
    public static Vec3 fragmentMotion(Vec3 simulation, Vec3 observed) {
        Vec3 motion=Double.isFinite(simulation.lengthSqr())&&simulation.lengthSqr()>1.0E-8?simulation:observed;
        return Double.isFinite(motion.lengthSqr())&&motion.lengthSqr()<=256?motion:Vec3.ZERO;
    }
    public static int bit(ComponentKind kind,int index) {
        return kind==ComponentKind.WING?(index==0?LEFT:RIGHT):kind==ComponentKind.AIRFRAME_TAIL?TAIL:0;
    }
    public static int missing(AircraftEntity aircraft) {
        return aircraft.getAcInfo()==null?0:missing(DamageZones.of(aircraft.getAcInfo()),aircraft.getComponentHealth());
    }
    public static int missing(List<DamageZone> zones,long health) {
        int mask=0;
        for(int slot=0;slot<zones.size();slot++)if(ComponentHealth.level(health,slot)==0)
            mask|=bit(zones.get(slot).kind(),zones.get(slot).index());
        return mask;
    }
}
