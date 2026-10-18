package io.github.mcheliceport.radar;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.flare.ChaffEntity;
import io.github.mcheliceport.entity.flare.FlareEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.entity.tank.TankEntity;
import io.github.mcheliceport.entity.vehicle.VehicleEntity;
import io.github.mcheliceport.entity.weapon.AAMissileEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Upstream's {@code EntityInfo}: where a tracked entity was on the server's last two ticks. Upstream
 * sent the entity's class name and the HUDs searched it; here the kind of entity they looked for is
 * sent instead.
 */
public record EntityInfo(int entityId, Kind kind, String name, double x, double y, double z, double prevX,
        double prevY, double prevZ) {

    /** The kinds of entity upstream's radar HUDs told apart by class name. */
    public enum Kind {
        PLAYER, PLANE, HELI, TANK, VEHICLE, OTHER_AIRCRAFT, CHAFF, FLARE, AA_MISSILE;

        public boolean isAircraft() {
            return this == PLANE || this == HELI || this == TANK || this == VEHICLE || this == OTHER_AIRCRAFT;
        }

        static Kind of(Entity entity) {
            return switch (entity) {
                case PlaneEntity ignored -> PLANE;
                case HeliEntity ignored -> HELI;
                case TankEntity ignored -> TANK;
                case VehicleEntity ignored -> VEHICLE;
                case AircraftEntity ignored -> OTHER_AIRCRAFT;
                case ChaffEntity ignored -> CHAFF;
                case FlareEntity ignored -> FLARE;
                case AAMissileEntity ignored -> AA_MISSILE;
                default -> PLAYER;
            };
        }
    }

    public static final StreamCodec<FriendlyByteBuf, EntityInfo> CODEC = StreamCodec.of(
            (buf, info) -> {
                buf.writeVarInt(info.entityId);
                buf.writeByte(info.kind.ordinal());
                buf.writeUtf(info.name);
                buf.writeDouble(info.x);
                buf.writeDouble(info.y);
                buf.writeDouble(info.z);
                buf.writeDouble(info.prevX);
                buf.writeDouble(info.prevY);
                buf.writeDouble(info.prevZ);
            },
            buf -> new EntityInfo(buf.readVarInt(), Kind.values()[Math.floorMod(buf.readByte(), Kind.values().length)],
                    buf.readUtf(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble()));

    /** Upstream {@code createInfo}: an aircraft goes by its content name, anything else by its own name. */
    public static EntityInfo of(Entity entity) {
        String name = entity instanceof AircraftEntity ac && ac.getAcInfo() != null ? ac.getAcInfo().name
                : entity.getName().getString();
        return new EntityInfo(entity.getId(), entity instanceof Player ? Kind.PLAYER : Kind.of(entity), name,
                entity.getX(), entity.getY(), entity.getZ(), entity.xOld, entity.yOld, entity.zOld);
    }

    public Vec3 position(float partialTicks) {
        return new Vec3(this.prevX + (this.x - this.prevX) * partialTicks,
                this.prevY + (this.y - this.prevY) * partialTicks, this.prevZ + (this.z - this.prevZ) * partialTicks);
    }

    public double distanceToSqr(Entity entity) {
        return entity.distanceToSqr(this.x, this.y, this.z);
    }
}
