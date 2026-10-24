package io.github.mcheliceport.client.particle;
import static org.junit.jupiter.api.Assertions.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
class ModernExplosionTextureTest {
    @Test void modernSheetHasSixteenNonEmptyFramesAndRealTransparency() throws Exception {
        var stream=getClass().getResourceAsStream("/assets/mcheli_ce_port/textures/particle/m8_modern_blast_cloud.png");
        assertNotNull(stream);
        try(stream) {
            var image=ImageIO.read(stream);assertTrue(image.getColorModel().hasAlpha());
            assertEquals(image.getWidth(),image.getHeight());assertTrue(image.getWidth()>=512);
            assertEquals(0,image.getRGB(0,0)>>>24);
            for(int row=0;row<4;row++)for(int col=0;col<4;col++) {
                int occupied=0,clear=0;
                for(int y=row*image.getHeight()/4;y<(row+1)*image.getHeight()/4;y+=8)
                    for(int x=col*image.getWidth()/4;x<(col+1)*image.getWidth()/4;x+=8) {
                        int alpha=image.getRGB(x,y)>>>24;if(alpha>128)occupied++;if(alpha<8)clear++;
                    }
                assertTrue(occupied>10,"Empty frame "+row+","+col);
                assertTrue(clear>10,"Missing transparent padding "+row+","+col);
            }
        }
    }
}
