package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class EffectTextureTest {
    private record Sprite(int width, int height, Identifier name) implements Stitcher.Entry {}

    @Test
    void actualEffectSheetsSurvivePinnedAtlasPacking() throws Exception {
        List<Sprite> sprites = new ArrayList<>();
        // The pinned stitcher can silently lose a wide, short sprite during Y expansion.
        // Exercise the real texture dimensions alongside the original flash, not a mock packer.
        sprites.add(new Sprite(63, 32, Identifier.fromNamespaceAndPath("mcheli", "flash")));
        for (String effect : List.of("smoke", "splash", "flash")) {
            String path = "/assets/mcheli_ce_port/textures/particle/m8_" + effect + ".png";
            try (var stream = getClass().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                var image = ImageIO.read(stream);
                assertEquals(128, image.getWidth());
                assertEquals(64, image.getHeight());
                sprites.add(new Sprite(image.getWidth(), image.getHeight(),
                        Identifier.fromNamespaceAndPath("mcheli_ce_port", "m8_" + effect)));
            }
        }
        for (String effect : List.of("wash", "wash_ripple")) {
            String path = "/assets/mcheli_ce_port/textures/particle/m8_" + effect + ".png";
            try (var stream = getClass().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                var image = ImageIO.read(stream);
                int size = effect.equals("wash") ? 64 : 32;
                assertEquals(size, image.getWidth());
                assertEquals(size, image.getHeight());
                sprites.add(new Sprite(size, size, Identifier.fromNamespaceAndPath("mcheli_ce_port", "m8_" + effect)));
            }
        }
        Stitcher<Sprite> stitcher = new Stitcher<>(4096, 4096, 0, 0);
        sprites.forEach(stitcher::registerSprite);
        stitcher.stitch();
        var stitched = new HashSet<Identifier>();
        stitcher.gatherSprites((sprite, x, y, padding) -> stitched.add(sprite.name()));
        assertEquals(new HashSet<>(sprites.stream().map(Sprite::name).toList()), stitched);
    }
}
