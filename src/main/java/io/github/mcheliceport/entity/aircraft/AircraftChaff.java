package io.github.mcheliceport.entity.aircraft;

import io.github.mcheliceport.entity.flare.ChaffEntity;
import io.github.mcheliceport.net.ChaffUsePayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A vehicle's chaff dispenser: for {@code ChaffUseTime} ticks it drops two chaff clouds behind and
 * below the vehicle ten times, then waits {@code ChaffWaitTime}. While it runs, radar missiles will
 * not lock on to the vehicle. Ported from upstream {@code MCH_Chaff}.
 *
 * <p>Upstream told radar locks about chaff through a field only clients set and nothing counted
 * down, so on clients a vehicle that once used chaff could never be locked again, and on the
 * server it always could. Here {@link #useTick} (the time chaff is still in use) answers on both
 * sides; clients learn it from {@link ChaffUsePayload}.
 */
public final class AircraftChaff {

    public int tick;
    public int useTick;
    public int chaffUseTime;
    public int chaffWaitTime;
    private final Level world;
    private final AircraftEntity aircraft;
    private int spawnChaffEntityIntervalTick;

    public AircraftChaff(Level w, AircraftEntity ac) {
        this.world = w;
        this.aircraft = ac;
    }

    public boolean onUse() {
        if (this.world.isClientSide()) {
            if (this.tick == 0) {
                this.start(this.chaffUseTime);
                return true;
            }
            return false;
        }
        this.start(this.chaffUseTime);
        if (this.world instanceof ServerLevel server) {
            PacketDistributor.sendToPlayersInDimension(server, new ChaffUsePayload(this.aircraft.getId(), this.useTick));
        }
        return true;
    }

    /** Starts the dispenser for {@code useTicks} (another client's chaff, from the payload). */
    public void start(int useTicks) {
        this.tick = this.chaffWaitTime;
        this.useTick = useTicks;
        this.spawnChaffEntityIntervalTick = 0;
    }

    public void onUpdate() {
        if (!this.aircraft.isAlive()) {
            return;
        }
        if (this.tick > 0) {
            this.tick--;
        }
        if (this.useTick > 0) {
            this.useTick--;
        }
        if (this.useTick > 0) {
            this.onUsing();
        }
    }

    private void onUsing() {
        if (this.spawnChaffEntityIntervalTick == 0) {
            this.spawnChaffEntityIntervalTick = this.chaffUseTime / 10;
            if (!this.world.isClientSide()) {
                this.spawnChaffEntity();
            }
        }
        if (this.spawnChaffEntityIntervalTick > 0) {
            this.spawnChaffEntityIntervalTick--;
        }
    }

    private void spawnChaffEntity() {
        float rad = (float) Math.toRadians(this.aircraft.getYRot());
        double forwardX = -Mth.sin(rad);
        double forwardZ = Mth.cos(rad);
        double leftX = -Mth.sin(rad + (float) Math.PI / 2F);
        double leftZ = Mth.cos(rad + (float) Math.PI / 2F);
        double rightX = -Mth.sin(rad - (float) Math.PI / 2F);
        double rightZ = Mth.cos(rad - (float) Math.PI / 2F);
        double baseX = this.aircraft.xo - forwardX * 20.0;
        double baseY = this.aircraft.yo - 10.0;
        double baseZ = this.aircraft.zo - forwardZ * 20.0;
        double sideOffset = 1.5;
        double sideSpeed = 0.2;
        Vec3 m = this.aircraft.getDeltaMovement();
        this.world.addFreshEntity(new ChaffEntity(this.world, baseX + leftX * sideOffset, baseY,
                baseZ + leftZ * sideOffset, m.x + leftX * sideSpeed, m.y, m.z + leftZ * sideSpeed));
        this.world.addFreshEntity(new ChaffEntity(this.world, baseX + rightX * sideOffset, baseY,
                baseZ + rightZ * sideOffset, m.x + rightX * sideSpeed, m.y, m.z + rightZ * sideSpeed));
    }

    public boolean isInPreparation() {
        return this.tick != 0;
    }

    public boolean isUsing() {
        return this.useTick > 0;
    }
}
