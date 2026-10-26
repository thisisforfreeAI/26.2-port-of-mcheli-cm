package io.github.mcheliceport.client.particle;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class CookoffLookTest {
    @Test void emptyVehiclesNeverPopAndLargeStoresStillHaveOnlyFourPops(){
        for(int id:new int[]{1,7,970004}){
            int count=0;for(int age=-1;age<1500;age++){
                assertFalse(CookoffLook.pulse(age,id,0));if(CookoffLook.pulse(age,id,10000)){count++;assertTrue(age<200);}
            }assertEquals(4,count);
        }
    }
    @Test void oldWrecksDoNotReplayAndEachFreshAgeIsConsumedOnce(){
        var old=new WreckCoolingLook();old.tick(true);
        for(int i=0;i<1000;i++){assertFalse(old.cookoff(7,200));old.tick(true);}
        var fresh=new WreckCoolingLook();fresh.tick(false);fresh.tick(true);int count=0;
        for(int i=0;i<250;i++){if(fresh.cookoff(7,200)){count++;assertFalse(fresh.cookoff(7,200));}fresh.tick(true);}
        assertEquals(4,count);
    }
}
