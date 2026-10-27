package io.github.mcheliceport.client.particle;

/** A finite cosmetic burn; observing an existing wreck never replays its fresh fire. */
public final class WreckCoolingLook {
    private boolean initialized, destroyed;
    private int age = -1, lastCookoffAge = -1;
    public void tick(boolean wreck) {
        if (!wreck) { age = -1; lastCookoffAge = -1; }
        else if (!initialized) age = 400;
        else if (!destroyed) age = 0;
        else age = Math.min(1200, age + 1);
        initialized = true; destroyed = wreck;
    }
    public boolean cookoff(int id,int ammunition){
        if(age==lastCookoffAge||!CookoffLook.pulse(age,id,ammunition))return false;
        lastCookoffAge=age;return true;
    }
    public int age() { return age; }
    public float fire() { return age < 0 ? 0 : Math.max(0, 1 - age / 360F); }
    public float smoke() { return age < 0 ? 0 : Math.max(0, 1 - age / 1200F); }
}
