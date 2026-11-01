package io.github.mcheliceport.platform;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Config registration: NeoForge's own config system, or Forge Config API Port on Fabric. */
public interface Configs {

    enum Type { COMMON, SERVER, CLIENT }

    void register(Type type, ModConfigSpec spec);

    /** Runs {@code listener} each time {@code spec} is loaded or reloaded from its file. */
    void onLoadOrReload(ModConfigSpec spec, Runnable listener);

    /** Runs {@code listener} each time {@code spec} is reloaded (not on the first load). */
    void onReload(ModConfigSpec spec, Runnable listener);
}
