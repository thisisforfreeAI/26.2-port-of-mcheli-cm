package io.github.mcheliceport.client.effect;

import java.util.List;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Two small geometric imprint pieces share the existing vanilla concrete grain texture. */
public final class TrackLook {
    public enum Surface { SOIL, SAND, SNOW, HARD, WOOD }
    private TrackLook() {}

    public static Surface surface(BlockState state) {
        if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) return Surface.SNOW;
        if (state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL)) return Surface.SAND;
        if (state.is(BlockTags.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.MUD) || state.is(Blocks.FARMLAND) || state.is(Blocks.DIRT_PATH)) return Surface.SOIL;
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)) return Surface.WOOD;
        return Surface.HARD;
    }

    /** Reuse the existing grain texture, breaking up repeated clipped tiles at birth. */
    public static float grain(double x,double z) {
        if(!Double.isFinite(x+z))return 1;
        return (float)(.88+.12*(.5+.5*Math.sin(x*2.17+z*.73)*Math.cos(z*1.43-x*.31)));
    }
    public static int color(Surface surface) {
        return switch (surface) {
            case SOIL -> 0x4C3526; case SAND -> 0x896A42; case SNOW -> 0x9AADB9;
            case HARD -> 0x353331; case WOOD -> 0x5D4734;
        };
    }

    public static float opacity(Surface surface, TrackMarks.Kind kind, float strength) {
        if (!Float.isFinite(strength) || strength <= 0) return 0;
        float s = Math.clamp(strength, 0, 1);
        return switch (surface) {
            case SOIL -> 0.48F * (0.45F + 0.55F * s);
            case SAND -> 0.40F * (0.45F + 0.55F * s);
            case SNOW -> 0.34F * (0.45F + 0.55F * s);
            case WOOD -> 0.20F * s;
            case HARD -> (kind == TrackMarks.Kind.TIRE ? 0.16F : 0.10F) * s * s;
        };
    }

    public static List<List<TrackClip.Point>> pattern(double x, double z, double dx, double dz, float width, TrackMarks.Kind kind) {
        double norm = Math.hypot(dx, dz);
        if (!Double.isFinite(norm + x + z) || norm < 1.0E-6 || !Float.isFinite(width)) return List.of();
        dx /= norm; dz /= norm; width = Math.clamp(width, 0.15F, 1.2F);
        double sx = dz, sz = -dx;
        if (kind == TrackMarks.Kind.TIRE) {
            return List.of(TrackClip.rectangle(x - sx * width * 0.3, z - sz * width * 0.3, dx, dz, width * 0.32, 0.45),
                    TrackClip.rectangle(x + sx * width * 0.3, z + sz * width * 0.3, dx, dz, width * 0.32, 0.45));
        }
        // Opposed short bars make a broken chevron; the centre gap and grain avoid a solid stripe.
        double turn = 0.28, c = Math.cos(turn), s = Math.sin(turn);
        return List.of(TrackClip.rectangle(x - sx * width * 0.24, z - sz * width * 0.24,
                        dx * c - dz * s, dx * s + dz * c, width * 0.52, 0.18),
                TrackClip.rectangle(x + sx * width * 0.24, z + sz * width * 0.24,
                        dx * c + dz * s, -dx * s + dz * c, width * 0.52, 0.18));
    }
}
