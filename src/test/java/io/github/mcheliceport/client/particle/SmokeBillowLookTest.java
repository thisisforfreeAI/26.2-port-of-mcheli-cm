package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SmokeBillowLookTest {
    @Test void upperPlumeWidensSlowsAndBendsWhileWispsDisperseFirst(){
        for(float fire:new float[]{0,.4F,1}){
            int life=SmokeBillowLook.wreckLife(false,fire);
            assertTrue(life>=68&&life<=88);assertTrue(SmokeBillowLook.wreckLife(true,fire)<life);
            float size=0;
            for(int age=0;age<=life;age++){
                float next=SmokeBillowLook.growth(age,life,false);
                assertTrue(next>=size&&next<=1);size=next;
                assertTrue(SmokeBillowLook.lift(fire,age,false)>0);
            }
            assertTrue(SmokeBillowLook.lift(fire,0,false)>SmokeBillowLook.lift(fire,life,false));
        }
        assertTrue(SmokeBillowLook.windShare(8)>SmokeBillowLook.windShare(0));
        assertEquals(SmokeBillowLook.windShare(8),SmokeBillowLook.windShare(80));
    }
    @Test void hotWrecksStayConnectedWhileColdSmokeHasUnevenGaps(){
        int quiet=0;
        for(int tick=0;tick<2400;tick++){
            float pulse=SmokeBillowLook.surge(tick,31);assertTrue(pulse>=.47F&&pulse<=1.01F);
            assertFalse(SmokeBillowLook.quiet(1,tick,31));
            if(SmokeBillowLook.quiet(.2F,tick,31))quiet++;
            assertTrue(Math.abs(SmokeBillowLook.blastCurl(tick,.5F,4,20,false))<=.001201);
        }
        assertTrue(quiet>0&&quiet<2400);
        assertEquals(SmokeBillowLook.surge(14,1),SmokeBillowLook.surge(24014,1));
    }
    @Test void amberStaysNearFreshFireAndCoolSmokeNeverEmitsItsOwnLight(){
        assertTrue(SmokeBillowLook.amber(1,0,0)>0);
        assertEquals(0,SmokeBillowLook.amber(1,24,0));
        assertEquals(0,SmokeBillowLook.amber(1,0,3));
        assertEquals(0,SmokeBillowLook.amber(0,0,0));
        for(int age=0;age<100;age++)for(float fire:new float[]{0,.5F,1}){
            float gray=SmokeBillowLook.gray(fire,age,88,.5F);assertTrue(gray>=.74F&&gray<=.99F);
        }
    }
    @Test void everyFamilyUsesAllSixteenFramesWithoutLoopingBackToDenseSmoke(){
        for(int life:new int[]{38,68,88,116}){
            var seen=new java.util.HashSet<Integer>();int last=-1;
            for(int tick=0;tick<=life;tick++){
                int frame=SmokeAtlas.frame(tick,life);assertTrue(frame>=last&&frame>=0&&frame<=15);seen.add(frame);last=frame;
            }
            assertEquals(16,seen.size());assertEquals(15,SmokeAtlas.frame(life*2,life));assertEquals(0,SmokeAtlas.frame(-1,life));
        }
    }

    @Test void allFortyEightAtlasCellsContainDistinctTransparentArt() throws Exception {
        for(String family:new String[]{"explosion","wreck","wisps"}){
            try(var in=SmokeBillowLookTest.class.getResourceAsStream("/assets/mcheli_ce_port/textures/particle/m8_billow_"+family+".png")){
                assertNotNull(in);var image=javax.imageio.ImageIO.read(in);assertNotNull(image);assertTrue(image.getColorModel().hasAlpha());
                var distinct=new java.util.HashSet<Integer>();
                for(int row=0;row<4;row++)for(int col=0;col<4;col++){
                    int x0=Math.round(col*image.getWidth()/4F),y0=Math.round(row*image.getHeight()/4F);
                    int x1=Math.round((col+1)*image.getWidth()/4F),y1=Math.round((row+1)*image.getHeight()/4F);
                    var pixels=image.getRGB(x0,y0,x1-x0,y1-y0,null,0,x1-x0);int visible=0,transparent=0;
                    for(int pixel:pixels){if((pixel>>>24)>128)visible++;if((pixel>>>24)<10)transparent++;}
                    assertTrue(visible>10&&transparent>10,family+" cell "+row+","+col);distinct.add(java.util.Arrays.hashCode(pixels));
                }
                assertEquals(16,distinct.size(),family);
            }
        }
    }

}
