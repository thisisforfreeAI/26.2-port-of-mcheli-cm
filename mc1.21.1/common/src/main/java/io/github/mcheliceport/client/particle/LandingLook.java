package io.github.mcheliceport.client.particle;

/** Damped cosmetic gear travel; first observation and discontinuities cannot invent a touchdown. */
public final class LandingLook {
    private boolean primed,contact;
    private float compression,previous,pulse;
    private double downward;
    private int quiet;
    public record Impact(boolean touchdown,boolean hard,double speed){public static final Impact NONE=new Impact(false,false,0);}
    public void reset(){primed=contact=false;compression=previous=pulse=0;downward=0;quiet=0;}
    public float compression(float partial){return previous+(compression-previous)*Math.clamp(partial,0,1);}
    public Impact tick(boolean touching,double vertical,double horizontal,double penetration,boolean continuous){
        if(!continuous||!Double.isFinite(vertical+horizontal+penetration)){reset();return Impact.NONE;}
        previous=compression;Impact event=Impact.NONE;
        double down=Math.clamp(-vertical,0,2);
        if(primed&&touching&&!contact){
            double speed=Math.max(down,downward);
            if(speed>.08){pulse=(float)Math.min(.1,.025+speed*.16);event=new Impact(true,speed>.5,speed);}
        }
        // A contact ray can be up to four ticks after physics zeroed vertical velocity.
        if(!touching){if(down>.01){downward=down;quiet=0;}else if(++quiet>4)downward*=.7;}
        else{downward=0;quiet=0;}
        contact=touching;primed=true;
        float target=touching?(float)Math.clamp(.03+Math.max(0,penetration)*.45+pulse,.02,.12):0;
        compression+=(target-compression)*(touching?.5F:.3F);pulse*=.84F;
        return event;
    }
}
