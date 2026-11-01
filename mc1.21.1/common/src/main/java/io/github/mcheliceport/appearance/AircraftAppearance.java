package io.github.mcheliceport.appearance;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.menu.AircraftMenu;
import net.minecraft.world.entity.player.Player;

/** Bounded cosmetic selection. Zero is the legacy appearance; no new assets are selected by path. */
public record AircraftAppearance(int number,int symbol,int wear,int ink) {
    public static final AircraftAppearance DEFAULT=new AircraftAppearance(-1,0,0,0);
    public AircraftAppearance {
        if(number< -1||number>999||symbol<0||symbol>3||wear<0||wear>2||ink<0||ink>2)
            throw new IllegalArgumentException("Invalid aircraft appearance");
    }
    public int packed(){return number+1|(symbol<<10)|(wear<<12)|(ink<<14);}
    public static boolean valid(int value){return value>=0&&value<65536&&(value&1023)<=1000&&((value>>12)&3)<=2&&((value>>14)&3)<=2;}
    public static AircraftAppearance unpack(int value){return valid(value)?new AircraftAppearance((value&1023)-1,(value>>10)&3,(value>>12)&3,(value>>14)&3):DEFAULT;}
    public int color(){return switch(ink){case 1->0x777F83;case 2->0x8F985E;default->0xC4C8BF;};}
    /** The same open, nearby vehicle menu is required at application time, including after moving away. */
    public static boolean apply(Player player,int aircraftId,String texture,int packed){
        if(player.level().isClientSide()||!valid(packed)||!(player.containerMenu instanceof AircraftMenu menu))return false;
        AircraftEntity ac=menu.getAircraft();
        if(ac==null||ac.getId()!=aircraftId||ac.isDestroyed()||ac.getAcInfo()==null
                ||player.distanceToSqr(ac)>64||ac.notOnSameTeam(player)
                ||ac.getCurrentThrottle()>.05||ac.getDeltaMovement().lengthSqr()>.04
                ||!ac.getAcInfo().getTextureNames().contains(texture))return false;
        ac.setTextureName(texture);ac.setAppearance(unpack(packed));return true;
    }
}
