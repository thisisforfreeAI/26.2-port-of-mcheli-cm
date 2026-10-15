package io.github.mcheliceport.client.render;

import static org.junit.jupiter.api.Assertions.*;

import io.github.mcheliceport.client.model.PlaneExhaustTextures;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.PlaneInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlaneExhaustTest {
    @TempDir Path directory;

    @Test
    void exhaustPoseMatchesUpstreamGlTransforms() {
        PlaneInfo.ExhaustFlame part = new PlaneInfo.ExhaustFlame("flame", "jet", 2, 1, -4, 0, 1, 0, 25, 2);
        Matrix4f actual = PlaneExhaustPose.of(part, 35, -20, 15, 0.6F, 0.75F);
        Matrix4d expected = new Matrix4d().translation(0, AircraftInfo.GLOBAL_Y_OFFSET, 0)
                .rotateY(Math.toRadians(-35)).rotateX(Math.toRadians(-20)).rotateZ(Math.toRadians(15))
                .translate(2, 1, -4).rotateY(Math.toRadians(-15)).scale(1, 1, 0.75);
        float[] a = actual.get(new float[16]);
        double[] e = expected.get(new double[16]);
        for (int i = 0; i < 16; i++) {
            assertEquals(e[i], a[i], 2.0E-5, "upstream transform element " + i);
        }
    }

    @Test
    void textureSequenceStopsAtFirstGapAndCacheReloadFindsNewFrames() throws Exception {
        Path textures = directory.resolve("assets/mcheli/textures/exhaustflames");
        Files.createDirectories(textures);
        // The scanner checks resource presence; image decoding belongs to the renderer.
        Files.write(textures.resolve("jet0.png"), new byte[0]);
        Files.write(textures.resolve("jet2.png"), new byte[0]);
        try (var resources = resources()) {
            PlaneExhaustTextures.clear();
            assertEquals(1, PlaneExhaustTextures.get(resources, "jet").size());
            assertTrue(PlaneExhaustTextures.get(resources, "missing").isEmpty());
            assertTrue(PlaneExhaustTextures.discover(resources, "invalid prefix ").isEmpty());
            Files.write(textures.resolve("jet1.png"), new byte[0]);
            assertEquals(1, PlaneExhaustTextures.get(resources, "jet").size(), "cached until reload");
            PlaneExhaustTextures.clear();
            assertEquals(3, PlaneExhaustTextures.get(resources, "jet").size(), "reload discovers changed frames");
        } finally {
            PlaneExhaustTextures.clear();
        }
    }

    @Test
    void negativeAddonYawDoesNotThrowAndPreservesUpstreamClampOrder() {
        PlaneInfo.ExhaustFlame part = new PlaneInfo.ExhaustFlame("flame", "jet", 0, 0, 0, 0, 1, 0, -25, 1);
        Matrix4f pose = assertDoesNotThrow(() -> PlaneExhaustPose.of(part, 0, 0, 0, 0.6F, 1));
        assertEquals(Math.cos(Math.toRadians(-25)), pose.m00(), 1.0E-6);
        assertEquals(-Math.sin(Math.toRadians(-25)), pose.m02(), 1.0E-6);
    }

    private MultiPackResourceManager resources() {
        return new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(new PathPackResources(
                new PackLocationInfo("test_exhaust", Component.empty(), PackSource.DEFAULT, Optional.empty()), directory)));
    }
}
