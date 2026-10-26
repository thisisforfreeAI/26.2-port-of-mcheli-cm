package io.github.mcheliceport.client.particle;

/** Sparse, finite cosmetic ammunition pops. No ammunition consumption or damage. */
public final class CookoffLook {
    private static final int[] AGES={32,86,138,188};
    private CookoffLook(){}
    public static boolean candidate(int age,int id){
        for(int i=0;i<AGES.length;i++)if(age==AGES[i]+Math.floorMod((long)id*13+i*17,7))return true;
        return false;
    }
    public static boolean pulse(int age,int id,int ammunition){
        int count=ammunition<=0?0:ammunition<4?1:ammunition<32?2:ammunition<128?3:4;
        for(int i=0;i<count;i++)if(age==AGES[i]+Math.floorMod((long)id*13+i*17,7))return true;
        return false;
    }
}
