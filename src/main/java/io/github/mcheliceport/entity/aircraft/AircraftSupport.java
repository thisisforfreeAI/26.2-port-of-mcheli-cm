package io.github.mcheliceport.entity.aircraft;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** Nearby fuel/repair and medical crew effects from upstream MCH_EntityAircraft. */
final class AircraftSupport {
    private final AircraftEntity ac;
    private int fuelSuppliedCount;

    AircraftSupport(AircraftEntity ac) {
        this.ac = ac;
    }

    void supplyFuel() {
        var info = this.ac.getAcInfo();
        if (info == null || info.fuelSupplyRange <= 0 || this.ac.level().isClientSide()
                || this.ac.getCountOnUpdate() % 10 != 0) {
            return;
        }
        String type = info.fuelSupplyInfinite ? info.fuelSupplyType : this.ac.getFuelType();
        if (type.isBlank() || !info.fuelSupplyInfinite && (info.electric || this.ac.getFuel() <= 0)) {
            return;
        }
        for (var other : this.ac.level().getEntitiesOfClass(AircraftEntity.class,
                this.ac.getBoundingBox().inflate(info.fuelSupplyRange))) {
            var target = other.getAcInfo();
            if (other == this.ac || target == null || !target.isFuelValid(type)
                    || other.getFuel() > 0 && !target.electric && !other.getFuelType().isBlank()
                    && !other.getFuelType().equals(type)) {
                continue;
            }
            if ((!this.ac.onGround() || other.canSupply()) && other.getFuel() < other.getMaxFuel()) {
                int amount = Math.min(other.getMaxFuel() - other.getFuel(), 30);
                if (other.getFuel() <= 0) {
                    other.setFuelType(type);
                }
                other.setFuel(other.getFuel() + amount);
                // Upstream clamps the source after transfer; it does not cap the amount to its fuel.
                if (!info.fuelSupplyInfinite) {
                    this.ac.setFuel(this.ac.getFuel() - amount);
                }
            }
            // Even a full or airborne compatible recipient gets the upstream consumption grace.
            other.support().fuelSuppliedCount = 40;
        }
    }

    /** Called only while updateFuel is active, immediately before the consumption check. */
    boolean pauseFuelConsumption() {
        if (this.fuelSuppliedCount > 0) {
            this.fuelSuppliedCount--;
        }
        return this.fuelSuppliedCount > 0;
    }

    void repairOtherAircraft() {
        var info = this.ac.getAcInfo();
        if (info == null || info.repairOtherVehiclesRange <= 0 || this.ac.level().isClientSide()
                || this.ac.getCountOnUpdate() % 20 != 0) {
            return;
        }
        for (var other : this.ac.level().getEntitiesOfClass(AircraftEntity.class,
                this.ac.getBoundingBox().inflate(info.repairOtherVehiclesRange))) {
            if (other != this.ac && other.getHP() < other.getMaxHP()) {
                other.setDamageTaken(other.getDamageTaken() - info.repairOtherVehiclesValue);
            }
        }
    }

    void regenerateCrew() {
        var info = this.ac.getAcInfo();
        if (this.ac.level().isClientSide() || this.ac.isDestroyed() || info == null
                || !info.regeneration || this.ac.getPilot() == null) {
            return;
        }
        // Upstream visits secondary seats only; the pilot enables the effect but does not receive it.
        for (var seat : this.ac.getSeats()) {
            if (seat != null && !seat.isRemoved() && seat.getFirstPassenger() instanceof LivingEntity rider
                    && rider.isAlive()) {
                var effect = rider.getEffect(MobEffects.REGENERATION);
                if (effect == null || effect.getDuration() < 500) {
                    rider.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 250, 0, true, true));
                }
            }
        }
    }
}
