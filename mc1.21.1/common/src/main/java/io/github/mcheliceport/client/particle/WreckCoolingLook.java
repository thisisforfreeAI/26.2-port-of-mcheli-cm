package io.github.mcheliceport.client.particle;

/** A finite cosmetic burn; observing an existing wreck never replays its fresh fire. */
public final class WreckCoolingLook {
    private boolean initialized, destroyed;
    /** After the smoke thins out, a faint wisp keeps rising from the hulk until this age. */
    public static final int LINGER = 4800;
    private int age = -1, lastCookoffAge = -1;
    public void tick(boolean wreck) {
        if (!wreck) { age = -1; lastCookoffAge = -1; }
        else if (!initialized) age = 400;
        else if (!destroyed) age = 0;
        else age = Math.min(LINGER, age + 1);
        initialized = true; destroyed = wreck;
    }
    public boolean cookoff(int id,int ammunition){
        if(age==lastCookoffAge||!CookoffLook.pulse(age,id,ammunition))return false;
        lastCookoffAge=age;return true;
    }
    public int age() { return age; }
    public float fire() { return age < 0 ? 0 : Math.max(0, 1 - age / 360F); }
    public float smoke() { return age < 0 ? 0 : Math.max(0, 1 - age / 1200F); }
    /** The thin wisp from a hulk that has stopped smoking properly, 0 before and after it. */
    public float lingering() { return age < 1200 ? 0 : Math.max(0, .16F * (1 - (age - 1200) / (float) (LINGER - 1200))); }
}
