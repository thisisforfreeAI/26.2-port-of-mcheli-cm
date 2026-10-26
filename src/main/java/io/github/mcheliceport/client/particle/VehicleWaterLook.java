package io.github.mcheliceport.client.particle;

public final class VehicleWaterLook {
    private VehicleWaterLook(){}
    public static boolean moving(double speedSquared){return Double.isFinite(speedSquared)&&speedSquared>.0009;}
    public static float width(double visualWidth){return (float)Math.clamp(visualWidth*.4,.35,2.5);}
    public static float alpha(float age){return .55F*Math.clamp(age/3,0,1)*Math.clamp((40-age)/14,0,1);}
}
