package io.github.mcheliceport.client.tool;

import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.ServerSettings;
import io.github.mcheliceport.multiplay.SpotClientHooks;
import io.github.mcheliceport.multiplay.Spotting;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.jspecify.annotations.Nullable;

/**
 * What this client marks: upstream {@code MCH_GuiTargetMarker}'s spotted entities, counted down
 * in seconds, and {@code MCH_EntityParticleMarkPoint}, the team's mark point, which lasts until
 * the next mark or until the player changes team. (Upstream's mark point also vanished for a
 * player on no team at all, so it never showed outside teams; here it shows to its marker.)
 */
public final class TargetMarkers implements SpotClientHooks.Hooks {

    public static final TargetMarkers INSTANCE = new TargetMarkers();

    private final Map<Integer, Integer> spotted = new HashMap<>();
    private int countdown;
    private @Nullable Vec3 markPoint;
    private @Nullable String markTeam;

    private TargetMarkers() {
    }

    @Override
    public void spotted(int count, int[] entityIds) {
        for (int id : entityIds) {
            this.spotted.merge(id, count, Math::max);
        }
    }

    @Override
    public void markPoint(int x, int y, int z) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || y >= Spotting.NO_POINT_Y) {
            this.markPoint = null;
            return;
        }
        this.markPoint = new Vec3(0.5 + x, 1.0 + y, 0.5 + z);
        this.markTeam = teamName(player);
    }

    public boolean isSpotted(@Nullable Entity entity) {
        return entity != null && this.spotted.containsKey(entity.getId());
    }

    public @Nullable Vec3 markPoint() {
        return this.markPoint;
    }

    /** Upstream {@code onClientTick}: each second, every mark counts down and expired ones go. */
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isPaused()) {
            this.countdown++;
        }
        if (this.countdown >= 20) {
            this.countdown = 0;
            this.spotted.replaceAll((id, count) -> count - 1);
            this.spotted.values().removeIf(count -> count <= 0);
        }
        if (this.markPoint != null && (mc.player == null || !Objects.equals(teamName(mc.player), this.markTeam))) {
            this.markPoint = null;
        }
    }

    public void reset() {
        this.spotted.clear();
        this.markPoint = null;
        this.countdown = 0;
    }

    /**
     * Upstream {@code isEnableEntityMarker}: this client shows markers, the server allows them (or
     * this is single player) and the marker size is above 0.
     */
    public static boolean enabled() {
        return ClientConfig.get(ClientConfig.DISPLAY_ENTITY_MARKER)
                && (Minecraft.getInstance().hasSingleplayerServer() || ServerSettings.client().enableEntityMarker())
                && ClientConfig.get(ClientConfig.ENTITY_MARKER_SIZE) > 0.0;
    }

    private static @Nullable String teamName(LocalPlayer player) {
        PlayerTeam team = player.getTeam();
        return team == null ? null : team.getName();
    }
}
