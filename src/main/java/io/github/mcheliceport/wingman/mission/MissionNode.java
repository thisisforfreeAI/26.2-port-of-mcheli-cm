package io.github.mcheliceport.wingman.mission;

import java.util.Locale;

/**
 * Upstream's {@code MissionNode}: one step of a mission planner route. Routes are saved and sent as
 * the node's text, {@code flyto:x,y,z}, {@code takeoff:id}, {@code land:id}, {@code attack:radius},
 * {@code loiter:ticks} or {@code park:id}.
 *
 * @param x             a fly-to node's point
 * @param runwayId      a take-off or landing node's base or runway marker ID
 * @param attackRadius  how far around the aircraft an attack node looks for hostile mobs
 * @param durationTicks how long a loiter node circles
 * @param parkingId     a park node's parking marker ID
 */
public record MissionNode(Type type, double x, double y, double z, String runwayId, double attackRadius,
        int durationTicks, String parkingId) {

    public enum Type {
        /** Fly to (x, y, z). */
        FLY_TO,
        /** Taxi to the runway and take off. */
        TAKEOFF,
        /** Fly the landing circuit and land on the runway. */
        LAND,
        /** Attack the hostile mobs around until none are left. */
        ATTACK,
        /** Circle where it is for a while. */
        LOITER,
        /** Taxi to a parking spot and stop. */
        PARK
    }

    /** Upstream's attack radius when a node gives none. */
    public static final double DEFAULT_ATTACK_RADIUS = 200.0;
    /** Upstream's loiter time when a node gives none. */
    public static final int DEFAULT_LOITER_TICKS = 600;

    public static MissionNode flyTo(double x, double y, double z) {
        return new MissionNode(Type.FLY_TO, x, y, z, "", DEFAULT_ATTACK_RADIUS, 0, "");
    }

    public static MissionNode takeoff(String runwayId) {
        return new MissionNode(Type.TAKEOFF, 0, 0, 0, runwayId, 0, 0, "");
    }

    public static MissionNode land(String runwayId) {
        return new MissionNode(Type.LAND, 0, 0, 0, runwayId, 0, 0, "");
    }

    public static MissionNode attack(double radius) {
        return new MissionNode(Type.ATTACK, 0, 0, 0, "", radius, 0, "");
    }

    public static MissionNode loiter(int ticks) {
        return new MissionNode(Type.LOITER, 0, 0, 0, "", 0, ticks, "");
    }

    public static MissionNode park(String parkingId) {
        return new MissionNode(Type.PARK, 0, 0, 0, "", 0, 0, parkingId);
    }

    /**
     * Upstream {@code parse}: one node from its text. Upstream also took numbers that are not
     * finite, a negative radius or time and coordinates past the world border; the port refuses them.
     *
     * @throws IllegalArgumentException for an unknown node type or a bad number
     */
    public static MissionNode parse(String token) {
        String[] parts = token.split(":", 2);
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        String arg = parts.length > 1 ? parts[1] : "";
        return switch (cmd) {
            case "flyto" -> {
                String[] xyz = arg.split(",");
                if (xyz.length != 3) {
                    throw new IllegalArgumentException("flyto needs x,y,z: " + arg);
                }
                yield flyTo(coordinate(xyz[0]), coordinate(xyz[1]), coordinate(xyz[2]));
            }
            case "takeoff" -> takeoff(arg);
            case "land" -> land(arg);
            case "attack" -> attack(arg.isEmpty() ? DEFAULT_ATTACK_RADIUS : number(arg, 0, 10000));
            case "loiter" -> loiter(arg.isEmpty() ? DEFAULT_LOITER_TICKS : (int) number(arg, 0, 1728000));
            case "park" -> park(arg);
            default -> throw new IllegalArgumentException("Unknown node type: " + cmd);
        };
    }

    private static double coordinate(String s) {
        return number(s, -3.0E7, 3.0E7);
    }

    private static double number(String s, double min, double max) {
        double v = Double.parseDouble(s.trim());
        if (!Double.isFinite(v) || v < min || v > max) {
            throw new IllegalArgumentException("Out of range: " + s);
        }
        return v;
    }

    /** Upstream {@code serialize}: the node's text, coordinates and radius as whole numbers. */
    public String serialize() {
        return switch (this.type) {
            case FLY_TO -> "flyto:" + (int) this.x + "," + (int) this.y + "," + (int) this.z;
            case TAKEOFF -> "takeoff:" + this.runwayId;
            case LAND -> "land:" + this.runwayId;
            case ATTACK -> "attack:" + (int) this.attackRadius;
            case LOITER -> "loiter:" + this.durationTicks;
            case PARK -> "park:" + this.parkingId;
        };
    }

    @Override
    public String toString() {
        return serialize();
    }
}
