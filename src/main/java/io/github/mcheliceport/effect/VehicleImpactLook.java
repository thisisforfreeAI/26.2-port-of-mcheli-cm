package io.github.mcheliceport.effect;

import org.joml.Matrix4f;

/** Shared cosmetic rules. No armour, component health or damage calculations. */
public final class VehicleImpactLook {
    private VehicleImpactLook() {}
    public static java.util.Optional<net.minecraft.world.phys.Vec3> contact(net.minecraft.world.phys.Vec3 origin,
            net.minecraft.world.phys.Vec3 direction,net.minecraft.world.phys.AABB box){
        if(!Double.isFinite(direction.lengthSqr())||direction.lengthSqr()<1e-8)return java.util.Optional.empty();
        var unit=direction.normalize();
        return box.inflate(.3).clip(origin.subtract(unit.scale(2)),origin.add(unit.scale(64)));
    }
    /** Locate the historical hit, then return it in the vehicle's current world frame. */
    public static java.util.Optional<net.minecraft.world.phys.Vec3> contact(net.minecraft.world.phys.Vec3 origin,
            net.minecraft.world.phys.Vec3 direction,net.minecraft.world.phys.AABB currentBox,net.minecraft.world.phys.Vec3 rewindShift){
        return contact(origin,direction,currentBox.move(rewindShift)).map(point->point.subtract(rewindShift));
    }
    public static Matrix4f body(float yaw, float pitch, float roll) {
        return new Matrix4f().translation(0, io.github.mcheliceport.content.aircraft.AircraftInfo.GLOBAL_Y_OFFSET, 0)
                .rotateY((float)Math.toRadians(-yaw)).rotateX((float)Math.toRadians(pitch)).rotateZ((float)Math.toRadians(roll));
    }
    public static float radius(boolean cannon, float power) {
        return cannon ? Math.clamp(.22F + (float)Math.sqrt(Math.max(0,power))*.045F,.22F,.55F) : .085F;
    }
    public static float repaired(float opacity, int healed, int maxHp, boolean fullyRepaired) {
        return fullyRepaired ? 0 : Math.max(0,opacity-Math.max(0,healed)/(float)Math.max(1,maxHp*.15F));
    }
    public static final class Gate {
        private long start=Long.MIN_VALUE;
        private int count;
        public boolean admit(long tick,int window,int cap) {
            if(start==Long.MIN_VALUE || tick<start || tick-start>=window){start=tick;count=0;}
            return count<cap && ++count<=cap;
        }
    }
}
