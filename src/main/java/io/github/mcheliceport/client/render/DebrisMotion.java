package io.github.mcheliceport.client.render;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Rigid-body motion for one cosmetic fragment. Pure state, no world access: callers sweep
 * {@link #sweepFrom()}..{@link #sweepTo()} and report the first block hit with {@link #contact}.
 * A fragment tears about its root hinge, flies with the aircraft's velocity and spin, falls
 * with flat-plate drag and span-wise autorotation, bounces off its lowest corner, then slides
 * and lies flat.
 */
public final class DebrisMotion {
    public enum Phase {RIP, FREE, SLIDE, REST, SUNK}
    public enum Event {NONE, BOUNCE, LAND, SPLASH}
    public static final double GRAVITY=.035;
    static final float MAX_SPIN=.55F;
    private static final Vec3 UP=new Vec3(0,1,0);

    public Vec3 position, previous, sampled, velocity;
    public final Quaternionf orientation=new Quaternionf(), previousOrientation=new Quaternionf();
    final Vector3f spin=new Vector3f();
    private final Vector3f inherited=new Vector3f();
    /** Half extents along the fragment's own axes; {@link #axes} maps them to the world at spawn. */
    private final Vector3f half;
    private final Quaternionf axes;
    private final int thin, longest;
    private final float plate, seed;
    private Vec3 hinge, hingeVelocity, arm;
    private final Vector3f tear=new Vector3f();
    private int ripTicks, age, bounces;
    private double groundY;
    public Phase phase;
    /** Speed of the last contact, for effect strength. */
    public double impact;

    public DebrisMotion(Vec3 center,Vec3 velocity,Vector3f inheritedSpin,Quaternionf axes,Vector3f size,int seed) {
        position=previous=sampled=center;this.velocity=finite(velocity)?velocity:Vec3.ZERO;
        this.axes=new Quaternionf(axes);
        half=new Vector3f(Math.max(.04F,Math.abs(size.x)*.5F),Math.max(.04F,Math.abs(size.y)*.5F),Math.max(.04F,Math.abs(size.z)*.5F));
        int t=0,l=0;for(int i=1;i<3;i++){if(half.get(i)<half.get(t))t=i;if(half.get(i)>half.get(l))l=i;}
        thin=t;longest=l==t?(t+1)%3:l;
        float middle=half.x+half.y+half.z-half.get(thin)-half.get(longest);
        plate=Math.clamp(1-half.get(thin)/Math.max(.05F,middle),0,1);
        this.seed=(seed&1023)*.6180339F;
        if(finite(inheritedSpin))spin.set(inheritedSpin);clampSpin();inherited.set(spin);
        phase=Phase.FREE;
    }
    /** The aircraft's own rotation at the moment of release, kept on top of the tear fold. */
    public void inherit(Vector3f rate){if(age>0||!finite(rate))return;spin.set(rate);clampSpin();inherited.set(spin);}
    /** Before release, the section folds about its torn root (an offset from the centre) for a few ticks. */
    public void tear(Vec3 hingeOffset) {
        if(phase!=Phase.FREE||age>0||hingeOffset==null||!finite(hingeOffset)||hingeOffset.lengthSqr()<=.01)return;
        hinge=position.add(hingeOffset);hingeVelocity=velocity;arm=hingeOffset.scale(-1);
        ripTicks=Math.clamp((int)Math.round(2+Math.sqrt(arm.length())),3,6);
        tearAxis();phase=Phase.RIP;
    }
    /** Lift and drag fold the section about its root; on a parked plane it simply droops. */
    private void tearAxis() {
        Vec3 up=world(axes,1);double speed=velocity.length();
        Vec3 load=velocity.scale(-.8).add(up.scale(.5*speed)).add(0,-.04,0)
                .add(world(axes,0).scale(Math.sin(seed*7)*.1*speed));
        Vec3 torque=arm.cross(load);
        if(torque.lengthSqr()<1.0E-10)torque=arm.cross(new Vec3(0,-1,0));
        if(torque.lengthSqr()<1.0E-10)torque=new Vec3(1,0,0);
        torque=torque.normalize();tear.set((float)torque.x,(float)torque.y,(float)torque.z);
    }

    /** One game tick of free flight. Contacts are applied separately, after the caller's sweep. */
    public void step() {
        previous=position;previousOrientation.set(orientation);age++;
        switch(phase) {
            case RIP -> rip();
            case FREE -> fly();
            case SLIDE -> slide();
            case SUNK -> {velocity=velocity.multiply(.8,.9,.8).add(0,-.004,0);position=position.add(velocity);spin.mul(.85F);rotate();}
            case REST -> {}
        }
    }
    private void rip() {
        float rate=.07F*Math.min(1,(float)(.4+velocity.length()))*(age/(float)ripTicks);
        spin.set(tear).mul(rate*ripTicks*.5F).add(inherited);
        rotate();hinge=hinge.add(hingeVelocity);
        Vec3 r=rotated(arm);position=hinge.add(r);
        if(age>=ripTicks) {
            var w=new Vec3(spin.x,spin.y,spin.z);
            velocity=hingeVelocity.add(w.cross(r));
            // A torn section keeps most of the fold on top of the aircraft's own rotation.
            spin.sub(inherited).mul(.75F).add(inherited);clampSpin();phase=Phase.FREE;sampled=position;
        }
    }
    private void fly() {
        double speed=velocity.length();
        Vec3 n=normal();
        // Flat plates resist motion through their face far more than edge-on travel.
        double cn=.012+.05*plate,ct=.006;
        double vn=velocity.dot(n);Vec3 normalPart=n.scale(vn),edge=velocity.subtract(normalPart);
        normalPart=normalPart.scale(1/(1+cn*speed));edge=edge.scale(1/(1+ct*speed));
        velocity=normalPart.add(edge);
        // Span-wise autorotation (a falling strip of paper) bends the fall into a fluttering glide.
        Vec3 span=world(longest);
        Vec3 cross=velocity.subtract(span.scale(velocity.dot(span)));
        float chord=Math.max(.3F,half.x+half.y+half.z-half.get(thin)-half.get(longest));
        float current=spin.dot((float)span.x,(float)span.y,(float)span.z);
        float direction=current!=0?Math.signum(current):(Math.sin(seed*13)>=0?1:-1);
        float target=direction*(float)Math.min(.4,.18*plate*cross.length()/chord);
        spin.fma((target-current)*.08F,new Vector3f((float)span.x,(float)span.y,(float)span.z));
        Vec3 glide=span.cross(cross).scale(.06*plate*current*chord);
        if(glide.length()>GRAVITY*.6)glide=glide.normalize().scale(GRAVITY*.6);
        velocity=velocity.add(glide).add(0,-GRAVITY,0);
        // Broadside travel makes plates pitch and wobble; compact chunks simply tumble.
        float gust=.006F*(float)Math.min(1,speed)*(.3F+plate);
        spin.add(gust*(float)Math.sin(age*.37+seed),gust*(float)Math.sin(age*.53+seed*2),gust*(float)Math.sin(age*.29+seed*3));
        spin.mul(1-.01F-.02F*plate*(float)Math.min(1,speed));clampSpin();
        position=position.add(velocity);rotate();
    }
    private void slide() {
        Vec3 flat=new Vec3(velocity.x,0,velocity.z);double s=flat.length();
        flat=s<.012?Vec3.ZERO:flat.scale(Math.max(0,s-.03-.12*s)/s);
        velocity=flat;spin.mul(.6F);rotate();
        // Settle onto the broadest face; the centre lowers as the fragment flattens.
        Vec3 a=normal();var to=new Quaternionf().rotationTo((float)a.x,(float)a.y,(float)a.z,0,a.y>=0?1:-1,0);
        orientation.set(new Quaternionf().slerp(to,.22F).mul(orientation)).normalize();
        position=new Vec3(position.x+flat.x,groundY+height(),position.z+flat.z);
        if(flat==Vec3.ZERO&&Math.abs(normal().y)>.98) {
            a=normal();orientation.premul(new Quaternionf().rotationTo((float)a.x,(float)a.y,(float)a.z,0,a.y>=0?1:-1,0)).normalize();
            position=new Vec3(position.x,groundY+height(),position.z);phase=Phase.REST;spin.zero();velocity=Vec3.ZERO;
        }
    }

    public boolean moving(){return phase==Phase.FREE||phase==Phase.SLIDE||phase==Phase.SUNK;}
    /** Actual cosmetic fragment span, for contact dust and sound sizing. */
    public float span(){return 2*Math.max(half.x,Math.max(half.y,half.z));}
    /** A resting fragment checks the ground under it now and then, in case it was blown away. */
    public boolean checkSupport(){return phase==Phase.REST&&age%10==0;}
    /**
     * The ray reaches the lowest corner, so landings do not sink into terrain. On the ground it is a
     * straight drop through the fragment: a ray from where it last touched down ran diagonally across
     * steps and crater rims and caught their side faces, bumping the fragment up and down.
     */
    public Vec3 sweepFrom(){return grounded()?position.add(0,.35,0):sampled;}
    public Vec3 sweepTo(){return position.add(0,-height()-(grounded()?.45:0),0);}
    private boolean grounded(){return phase==Phase.SLIDE||phase==Phase.REST;}
    /** No block under a sliding or resting fragment (it went over an edge, or the ground was blown away): it falls again. */
    public void clear(){if(grounded()){phase=Phase.FREE;velocity=new Vec3(velocity.x,0,velocity.z);}sampled=position;}
    /** The sweep started inside a block: a slide ran into a step and stops where it was; a resting fragment stays. */
    public void blocked(){
        if(phase==Phase.SLIDE){position=new Vec3(previous.x,position.y,previous.z);velocity=Vec3.ZERO;sampled=position;}
        else if(phase!=Phase.REST)clear();
    }

    public Event contact(Vec3 hit,Vec3 normal,boolean water) {
        sampled=position;
        if(phase==Phase.REST) {
            // The ground under a resting fragment moved (a block was placed or dug): it sits on the new top.
            if(!water&&normal.y>.6&&Math.abs(hit.y-groundY)>.01){groundY=hit.y;position=previous=new Vec3(position.x,groundY+height(),position.z);sampled=position;}
            return Event.NONE;
        }
        if(phase==Phase.SUNK||phase==Phase.RIP)return Event.NONE;
        double vn=velocity.dot(normal);impact=velocity.length();
        if(water) {
            position=hit.add(0,-.1,0);previous=position;sampled=position;
            velocity=new Vec3(velocity.x*.2,-.03,velocity.z*.2);spin.mul(.3F);phase=Phase.SUNK;return Event.SPLASH;
        }
        if(phase==Phase.SLIDE) {
            if(normal.y>.6){groundY=hit.y;position=new Vec3(position.x,groundY+height(),position.z);return Event.NONE;}
            velocity=velocity.subtract(normal.scale(1.4*Math.min(0,vn)));
            position=hit.add(normal.scale(height()+.02));return Event.NONE;
        }
        if(vn>=0) {position=hit.add(normal.scale(normal.y>.6?height()+.02:.05));return Event.NONE;}
        // Impulse at the lowest corner: restitution along the normal, Coulomb friction along the ground.
        Vec3 corner=corner(normal);
        double e=Math.clamp(.42-.25*plate-.06*bounces,.08,.42);
        Vec3 normalV=normal.scale(vn),tangent=velocity.subtract(normalV);
        double j=(1+e)*-vn,friction=Math.min(.3*j,tangent.length());
        Vec3 frictionV=tangent.lengthSqr()>1.0E-8?tangent.normalize().scale(-friction):Vec3.ZERO;
        velocity=tangent.add(frictionV).add(normal.scale(-vn*e));
        double inertia=Math.max(.15,(half.lengthSquared())/3);
        Vec3 dw=corner.cross(normal.scale(j).add(frictionV)).scale(.6/inertia);
        spin.mul(.55F).add((float)dw.x,(float)dw.y,(float)dw.z);clampSpin();
        position=hit.add(normal.scale(height()+.02));previous=previous.lerp(position,.5);
        bounces++;
        if(normal.y>.6&&(-vn<.13||bounces>=5)) {
            groundY=hit.y;phase=Phase.SLIDE;velocity=new Vec3(velocity.x,0,velocity.z);return bounces==1?Event.BOUNCE:Event.LAND;
        }
        return Event.BOUNCE;
    }

    /** Fraction of a rotation since the previous tick, about the fragment centre. */
    public Quaternionf rotation(float partial){return new Quaternionf(previousOrientation).slerp(orientation,partial);}
    public Vec3 at(float partial){return previous.lerp(position,partial);}
    /** World offset of a fragment-local point given relative to the centre at spawn. */
    public Vec3 rotated(Vec3 offset){var v=orientation.transform(new Vector3f((float)offset.x,(float)offset.y,(float)offset.z));return new Vec3(v.x,v.y,v.z);}
    public int bounces(){return bounces;}
    public int age(){return age;}
    public double spinRate(){return spin.length();}

    private void rotate() {
        float w=spin.length();if(w<1.0E-6F)return;
        orientation.set(new Quaternionf().rotationAxis(w,spin.x/w,spin.y/w,spin.z/w).mul(orientation)).normalize();
    }
    private void clampSpin(){float w=spin.length();if(!Float.isFinite(w))spin.zero();else if(w>MAX_SPIN)spin.mul(MAX_SPIN/w);}
    private Vec3 world(int axis){return rotated(world(axes,axis));}
    private static Vec3 world(Quaternionf q,int axis){var v=q.transform(new Vector3f(axis==0?1:0,axis==1?1:0,axis==2?1:0));return new Vec3(v.x,v.y,v.z);}
    private Vec3 normal(){return world(thin);}
    /** Vertical half-size of the oriented box. */
    double height(){double h=0;for(int i=0;i<3;i++)h+=Math.abs(world(i).y)*half.get(i);return h;}
    private Vec3 corner(Vec3 normal) {
        Vec3 best=Vec3.ZERO;double low=Double.POSITIVE_INFINITY;
        for(int c=0;c<8;c++) {
            Vec3 p=world(0).scale((c&1)==0?-half.x:half.x).add(world(1).scale((c&2)==0?-half.y:half.y)).add(world(2).scale((c&4)==0?-half.z:half.z));
            double d=p.dot(normal);if(d<low){low=d;best=p;}
        }
        return best;
    }
    private static boolean finite(Vec3 v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z)&&v.lengthSqr()<=256;}
    private static boolean finite(Vector3f v){return v!=null&&v.isFinite();}

    /** Angular velocity (radians per tick, world axes) between two attitudes. */
    public static Vector3f angularVelocity(Quaternionf before,Quaternionf after) {
        var delta=new Quaternionf(after).mul(new Quaternionf(before).conjugate()).normalize();
        if(delta.w<0)delta.set(-delta.x,-delta.y,-delta.z,-delta.w);
        float angle=2*(float)Math.acos(Math.min(1,delta.w)),s=(float)Math.sqrt(Math.max(0,1-delta.w*delta.w));
        if(s<1.0E-5F||!Float.isFinite(angle))return new Vector3f();
        var w=new Vector3f(delta.x/s,delta.y/s,delta.z/s).mul(angle);
        return w.length()>MAX_SPIN?w.mul(MAX_SPIN/w.length()):w;
    }
    /** Aircraft attitude as used by the renderer (yaw, pitch and roll in degrees). */
    public static Quaternionf attitude(float yaw,float pitch,float roll) {
        return new Quaternionf().rotateY((float)Math.toRadians(-yaw)).rotateX((float)Math.toRadians(pitch)).rotateZ((float)Math.toRadians(roll));
    }
}
