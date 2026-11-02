package io.github.mcheliceport.effectrange;

/** The displayed countdown is an estimate; only a server notice releases the cooling gate. */
public final class RangeReadiness {
    private boolean ready,cooling;private long until;
    public void notice(int state,int cooldown,long tick){
        if(state<0||state>4)return;
        if(state==0)ready=true;else if(state==1)ready=false;
        int bounded=Math.clamp(cooldown,0,2400);cooling=state!=4&&bounded>0;until=tick+bounded;
    }
    public boolean ready(){return ready;}
    public boolean cooling(){return cooling;}
    public int countdown(long tick){return (int)Math.clamp(until-tick,0,2400);}
}
