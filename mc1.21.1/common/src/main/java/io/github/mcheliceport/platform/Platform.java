package io.github.mcheliceport.platform;

import java.nio.file.Path;

/**
 * Everything the shared code needs from the mod loader. NeoForge and Fabric each provide one
 * implementation, found through {@link java.util.ServiceLoader} (see {@link Services}).
 */
public interface Platform {

    /** "NeoForge" or "Fabric". */
    String name();

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();

    boolean isClient();

    /** The game directory ({@code .minecraft} or the server folder). */
    Path gameDir();

    /** The config directory. */
    Path configDir();

    /**
     * The entity's saved mod data, kept across saves (and, for players, across death): NeoForge's
     * {@code getPersistentData()}, a Fabric data attachment.
     */
    net.minecraft.nbt.CompoundTag persistentData(net.minecraft.world.entity.Entity entity);

    /** The running server, or null (NeoForge's {@code io.github.mcheliceport.platform.Services.PLATFORM.currentServer()}). */
    net.minecraft.server.@org.jspecify.annotations.Nullable MinecraftServer currentServer();

    /**
     * Adds a source of always-on packs to the client resource or the server data pack repository
     * (only the untrusted one on NeoForge).
     */
    void addPackSource(net.minecraft.server.packs.PackType type,
            java.util.function.Consumer<java.util.function.Consumer<net.minecraft.server.packs.repository.Pack>> source);

    /** A fake player for using items on blocks (NeoForge's or Fabric API's {@code FakePlayer}). */
    net.minecraft.server.level.ServerPlayer fakePlayer(net.minecraft.server.level.ServerLevel level,
            com.mojang.authlib.GameProfile profile);

    /** NeoForge's {@code EventHooks.onExplosionStart}: true when a mod cancelled the explosion. */
    boolean explosionStart(net.minecraft.world.level.Level level, net.minecraft.world.level.Explosion explosion);

    /** NeoForge's {@code EventHooks.onExplosionDetonate}: mods may filter the lists. */
    void explosionDetonate(net.minecraft.world.level.Level level, net.minecraft.world.level.Explosion explosion,
            java.util.List<net.minecraft.world.entity.Entity> entities, java.util.List<net.minecraft.core.BlockPos> blocks);

    /** Every loaded mod, in the loader's order. */
    java.util.List<ModDescriptor> mods();

    /** This mod's version string from its metadata. */
    String modVersion();

    /**
     * Visits the files under {@code folder} inside this mod's jar (or its build output folders
     * in development). Paths given to the visitor are relative to the mod's root and use '/'.
     */
    void visitModFiles(String folder, ModFileVisitor visitor) throws java.io.IOException;

    /** Whether the mod's jar has a file at {@code path} (relative to its root, '/'-separated). */
    boolean modFileExists(String path);

    @FunctionalInterface
    interface ModFileVisitor {
        void visit(String relativePath, IoSupplier opener) throws java.io.IOException;
    }

    @FunctionalInterface
    interface IoSupplier {
        java.io.InputStream open() throws java.io.IOException;
    }
}
