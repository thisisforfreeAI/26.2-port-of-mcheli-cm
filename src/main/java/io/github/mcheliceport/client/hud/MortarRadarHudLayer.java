package io.github.mcheliceport.client.hud;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.client.AircraftClientEvents;
import io.github.mcheliceport.content.aircraft.RwrType;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.radar.EntityInfo;
import io.github.mcheliceport.radar.EntityInfoClientTracker;
import io.github.mcheliceport.weapon.WeaponSet;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Upstream's {@code HudMortarRadar}: while the vehicle's radar is switched on, a top-down scope
 * turned with the current weapon shows the tracked players and vehicles around it, with their
 * names and distances, and a cross where the weapon would land, red when someone is within six
 * blocks of it. The scope's range is the weapon's {@code MortarRadarMaxDist}, else 300 blocks.
 */
public final class MortarRadarHudLayer {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "mortar_radar");
    private static final Identifier RADAR = texture("mortar_radar");
    private static final Identifier CROSS = texture("mortar_cross");
    private static final Identifier TARGET = texture("mortar_target");
    private static final double SCREEN_HEIGHT = 520.0;
    private static final double SIZE = 250.0;
    private static final double CENTER_X = 150.0;
    private static final double CENTER_Y = 280.0;
    private static final double CROSS_SIZE = 21.0;
    private static final double MIN_DISTANCE = 20.0;
    private static final double MAX_DISTANCE = 300.0;

    private MortarRadarHudLayer() {
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath("mcheli", "textures/gui/" + name + ".png");
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(AircraftHudLayer.ID, ID, MortarRadarHudLayer::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gui.hud.isHidden()) {
            return;
        }
        AircraftEntity ac = AircraftClientEvents.riddenAircraft(player);
        if (ac == null || ac.getAcInfo() == null || !ac.isRadarEnabledRuntime()) {
            return;
        }
        float pt = deltaTracker.getGameTimeDeltaPartialTick(false);
        double maxDist = MAX_DISTANCE;
        WeaponSet ws = ac.getCurrentWeapon(player);
        if (ws != null && ws.getInfo() != null) {
            WeaponInfo wi = ws.getInfo();
            if (wi.mortarRadarMaxDist > 0) {
                maxDist = wi.mortarRadarMaxDist;
            }
            ac.weapons().getLandInDistance(player);
        }
        double pax = Mth.lerp(pt, ac.xOld, ac.getX());
        double pay = Mth.lerp(pt, ac.yOld, ac.getY());
        double paz = Mth.lerp(pt, ac.zOld, ac.getZ());
        float yaw = Mth.lerp(pt, ac.yRotO, ac.getYRot());
        if (ws != null && ws.getCurrentWeapon() != null) {
            yaw += Mth.lerp(pt, ws.getPrevYaw(), ws.getYaw()) + ws.getCurrentWeapon().fixRotationYaw;
        }
        float radYaw = (float) Math.toRadians(yaw);
        Vec3 lookH = new Vec3(Mth.sin(-radYaw), 0, Mth.cos(radYaw));

        Vec3 impact = ac.weapons().impactPos();
        Vec3 prevImpact = ac.weapons().prevImpactPos();
        boolean hasImpact = impact != null && prevImpact != null;
        Vec3 impactAt = hasImpact ? prevImpact.lerp(impact, pt) : Vec3.ZERO;

        double h = graphics.guiHeight();
        double sx = h * (CENTER_X / SCREEN_HEIGHT);
        double sy = h * (CENTER_Y / SCREEN_HEIGHT);
        double circleRadius = h * (SIZE / SCREEN_HEIGHT) / 2.0;
        GuiHudCanvas canvas = new GuiHudCanvas(graphics);
        canvas.texture(RADAR, sx - circleRadius, sy - circleRadius, circleRadius * 2.0, circleRadius * 2.0);

        boolean deadOn = false;
        double targetHalf = h * (2.0 / SCREEN_HEIGHT) / 2.0;
        for (EntityInfo entity : EntityInfoClientTracker.all()) {
            if (entity.kind() == EntityInfo.Kind.CHAFF || entity.kind() == EntityInfo.Kind.FLARE) {
                continue;
            }
            double ex = entity.x() - ac.getX();
            double ez = entity.z() - ac.getZ();
            if (ex * ex + ez * ez < MIN_DISTANCE * MIN_DISTANCE) {
                continue;
            }
            Vec3 at = entity.position(pt);
            double dx = at.x - pax;
            double dz = at.z - paz;
            double angle = signedAngle(lookH, new Vec3(dx, 0, dz).normalize());
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (hasImpact) {
                double ix = at.x - impactAt.x;
                double iz = at.z - impactAt.z;
                if (ix * ix + iz * iz < 36.0) {
                    deadOn = true;
                }
            }
            double radius = circleRadius * Mth.clamp((distance - MIN_DISTANCE) / (maxDist - MIN_DISTANCE), 0.0, 1.0);
            double rad = Math.toRadians(angle);
            double markerX = sx + radius * Math.sin(-rad);
            double markerY = sy - radius * Math.cos(rad);
            canvas.texture(TARGET, markerX - targetHalf, markerY - targetHalf, targetHalf * 2.0, targetHalf * 2.0);
            String text = nameOnRadar(entity, ac) + "[" + (int) distance + "]";
            int fw = mc.font.width(text);
            graphics.text(mc.font, text, (int) (markerX - fw / 2.0), (int) (markerY + 10.0), 0xFFFFFFFF, true);
        }

        if (hasImpact) {
            double ix = impactAt.x - pax;
            double iz = impactAt.z - paz;
            double interpDist = Math.sqrt(ix * ix + iz * iz);
            if (interpDist >= MIN_DISTANCE) {
                double angle = signedAngle(lookH, new Vec3(ix, 0, iz).normalize());
                double radius = circleRadius
                        * Mth.clamp((interpDist - MIN_DISTANCE) / (maxDist - MIN_DISTANCE), 0.0, 1.0);
                double rad = Math.toRadians(angle);
                double markerX = sx + radius * Math.sin(-rad);
                double markerY = sy - radius * Math.cos(rad);
                double half = h * (CROSS_SIZE / SCREEN_HEIGHT) / 2.0;
                int color = deadOn ? ARGB.color(255, 255, 0, 0) : 0xFFFFFFFF;
                canvas.texture(CROSS, markerX - half, markerY - half, half * 2.0, half * 2.0, color);
            }
        }
    }

    /** Upstream's bearing: the angle from the scope's heading, negative to the right. */
    private static double signedAngle(Vec3 lookH, Vec3 dirH) {
        double angle = Math.toDegrees(Math.acos(Mth.clamp(lookH.dot(dirH), -1.0, 1.0)));
        return lookH.x * dirH.z - lookH.z * dirH.x > 0 ? -angle : angle;
    }

    /**
     * Upstream {@code getTargetTypeOnRadar}: a digital RWR names vehicles by this vehicle's radar.
     * Upstream looked for planes under their pre-CE class name and so showed them as "?"; they are
     * named here like the other vehicles.
     */
    private static String nameOnRadar(EntityInfo entity, AircraftEntity ac) {
        boolean vehicle = switch (entity.kind()) {
            case HELI, PLANE, TANK, VEHICLE -> true;
            default -> false;
        };
        return ac.getAcInfo().rwrType == RwrType.DIGITAL && vehicle ? ac.getNameOnMyRadar(entity) : "?";
    }
}
