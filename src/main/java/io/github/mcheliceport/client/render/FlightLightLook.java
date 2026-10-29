package io.github.mcheliceport.client.render;

/** Restrained brightness and two short strobe flashes; no light-engine simulation. */
public final class FlightLightLook {
    private FlightLightLook(){}
    public static float night(int skyDarken){return Math.clamp((skyDarken-2)/9F,0,1);}
    public static float strobe(long tick,int source,int lamp){long phase=Math.floorMod(tick+source*13L+lamp*7L,80);return phase<2||phase>=6&&phase<8?1:0;}
    public static float radius(float authored,double distance){return Float.isFinite(authored)&&Double.isFinite(distance)?(float)Math.clamp(Math.max(authored,distance*.0015),.025,.35):.05F;}
    public static int lampLimit(int quality,int pressure){return Math.max(16,(quality==0?32:quality==2?96:64)/(1+Math.clamp(pressure,0,3)));}
    public static float beamScatter(boolean optional,float rain){return optional?.03F+.09F*(Float.isFinite(rain)?Math.clamp(rain,0,1):0):1;}
}
