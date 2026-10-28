package io.github.mcheliceport.client.particle;

import net.minecraft.resources.Identifier;

/** Three original 4x4 sheets: 16 age-ordered frames each, sampled without extra quads. */
final class SmokeAtlas {
    static final Identifier EXPLOSION=id("explosion"),WRECK=id("wreck"),WISPS=id("wisps");
    private SmokeAtlas(){}
    private static Identifier id(String family){return Identifier.fromNamespaceAndPath("mcheli_ce_port","m8_billow_"+family);}
    static int frame(float age,int life){return Math.clamp((int)(Math.max(0,age)*16/Math.max(1,life)),0,15);}
}
