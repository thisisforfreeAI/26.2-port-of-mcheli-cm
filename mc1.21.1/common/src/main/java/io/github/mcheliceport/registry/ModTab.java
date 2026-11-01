package io.github.mcheliceport.registry;

/**
 * The seven creative tabs, in upstream's order. {@code title} is the English name upstream showed
 * (its tab labels); the port's lang files carry it under {@code itemGroup.mcheli_ce_port.<id>}.
 */
public enum ModTab {
    ITEMS("items", "MCHeli CE Items", "fuel"),
    HELICOPTERS("helicopters", "MCHeli CE Helicopters", "ah-64"),
    PLANES("planes", "MCHeli CE Planes", "f22a"),
    SHIPS("ships", "MCHeli CE Ships", "project1204"),
    TANKS("tanks", "MCHeli CE Tanks", "merkava_mk4"),
    VEHICLES("vehicles", "MCHeli CE Vehicles", "mk15"),
    /** Upstream's tab for its unported custom item content; stays empty, so it is not shown. */
    RECIPE_ITEMS("recipe_items", "MCHeli CE Recipe Items", "");

    public final String id;
    public final String title;
    /** Upstream's default icon item ({@code CreativeTabIcon*} in mcheli.cfg); empty for none. */
    public final String defaultIcon;

    ModTab(String id, String title, String defaultIcon) {
        this.id = id;
        this.title = title;
        this.defaultIcon = defaultIcon;
    }

    public String translationKey() {
        return "itemGroup.mcheli_ce_port." + this.id;
    }
}
