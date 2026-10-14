package io.github.mcheliceport.weapon.guidance;

/** Upstream {@code MCH_IGuidanceSystem}: something that designates a point to fly to. */
public interface GuidanceSystem {

    double getLockPosX();

    double getLockPosY();

    double getLockPosZ();

    void update();
}
