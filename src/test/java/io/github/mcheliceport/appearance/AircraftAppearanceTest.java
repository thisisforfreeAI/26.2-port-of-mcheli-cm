package io.github.mcheliceport.appearance;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AircraftAppearanceTest {
    @Test void everySupportedCombinationRoundTripsWithoutSharingReservedValues(){
        assertEquals(0,AircraftAppearance.DEFAULT.packed());
        for(int number=-1;number<=999;number++)for(int symbol=0;symbol<4;symbol++)for(int wear=0;wear<3;wear++)for(int ink=0;ink<3;ink++){
            var look=new AircraftAppearance(number,symbol,wear,ink);assertTrue(AircraftAppearance.valid(look.packed()));assertEquals(look,AircraftAppearance.unpack(look.packed()));
        }
    }
    @Test void malformedSavedOrNetworkSelectionsCannotIndexAnAtlasOrSelectArbitraryAssets(){
        for(int packed:new int[]{-1,65536,Integer.MAX_VALUE,1001,3<<12,3<<14}){
            assertFalse(AircraftAppearance.valid(packed));assertEquals(AircraftAppearance.DEFAULT,AircraftAppearance.unpack(packed));
        }
        assertThrows(IllegalArgumentException.class,()->new AircraftAppearance(1000,0,0,0));
        assertThrows(IllegalArgumentException.class,()->new AircraftAppearance(1,4,0,0));
    }
}
