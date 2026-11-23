package io.github.mcheliceport.radar;

import java.util.Locale;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * One line of a radar warning receiver's threat table: who is painting the vehicle, how, and from
 * where. Upstream {@code MCH_RWRThreatEvent}, except that the emitter's world position is sent
 * instead of a bearing, so the client works out the direction against the vehicle's current
 * heading every frame rather than the heading it had when the server looked.
 *
 * @param emitterId the radar's or missile's entity ID
 * @param kind      {@link #AIRCRAFT}, {@link #MISSILE}, {@link #GROUND} or {@link #INFANTRY}
 * @param mode      {@link #SEARCH}, {@link #LOCK} or {@link #LAUNCH}
 * @param name      the symbol drawn on the scope
 */
public record RwrThreat(int emitterId, byte kind, byte mode, String name, double x, double y, double z) {

    public static final byte AIRCRAFT = 1;
    public static final byte MISSILE = 2;
    /** A tank, ground vehicle or ship: upstream's SAM site. */
    public static final byte GROUND = 3;
    /** A player on foot with a hand-held launcher. */
    public static final byte INFANTRY = 4;

    /** A search radar sweeping over the vehicle. */
    public static final byte SEARCH = 0;
    /** A seeker or fire-control radar locking or locked on the vehicle (upstream's STT). */
    public static final byte LOCK = 1;
    /** A missile in flight homing on the vehicle. */
    public static final byte LAUNCH = 3;

    public static final StreamCodec<FriendlyByteBuf, RwrThreat> CODEC = StreamCodec.of(
            (buf, t) -> {
                buf.writeVarInt(t.emitterId);
                buf.writeByte(t.kind);
                buf.writeByte(t.mode);
                buf.writeUtf(t.name, 16);
                buf.writeDouble(t.x);
                buf.writeDouble(t.y);
                buf.writeDouble(t.z);
            },
            buf -> new RwrThreat(buf.readVarInt(), buf.readByte(), buf.readByte(), buf.readUtf(16),
                    buf.readDouble(), buf.readDouble(), buf.readDouble()));

    public RwrThreat {
        name = name == null || name.isBlank() ? "?" : name.length() > 16 ? name.substring(0, 16) : name;
    }

    public boolean isLock() {
        return this.mode == LOCK;
    }

    public boolean isLaunch() {
        return this.mode == LAUNCH;
    }

    /**
     * The symbol for a vehicle whose content gives none ({@code NameOnRWR} unset): the number in its
     * name, as real warning receivers show "29" for a MiG-29, or else its first three letters.
     */
    public static String symbolFor(String contentName) {
        if (contentName == null || contentName.isBlank()) {
            return "?";
        }
        StringBuilder digits = new StringBuilder();
        for (char c : contentName.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            } else if (!digits.isEmpty()) {
                break;
            }
        }
        if (!digits.isEmpty()) {
            return digits.length() > 4 ? digits.substring(0, 4) : digits.toString();
        }
        String letters = contentName.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
        return letters.isEmpty() ? "?" : letters.substring(0, Math.min(3, letters.length()));
    }

    /**
     * Upstream {@code getRelativeBearing}: the direction of an emitter {@code dx}, {@code dz} away,
     * from the nose of a vehicle heading {@code yaw}, in degrees, positive to the right, -180 to 180.
     */
    public static double relativeBearing(double dx, double dz, float yaw) {
        double rel = Math.toDegrees(Math.atan2(-dx, dz)) - yaw;
        return ((rel + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
    }
}
