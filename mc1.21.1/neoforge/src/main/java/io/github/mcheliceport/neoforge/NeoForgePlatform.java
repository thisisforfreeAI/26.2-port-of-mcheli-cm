package io.github.mcheliceport.neoforge;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.platform.Platform;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;

public final class NeoForgePlatform implements Platform {

    @Override
    public String name() {
        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLEnvironment.production;
    }

    @Override
    public boolean isClient() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }

    @Override
    public Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public String modVersion() {
        return ModList.get().getModContainerById(McheliCePort.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString()).orElse("unknown");
    }

    @Override
    public void visitModFiles(String folder, ModFileVisitor visitor) throws IOException {
        Path root = ModList.get().getModFileById(McheliCePort.MOD_ID).getFile().findResource(folder);
        walk(folder, root, visitor);
    }

    @Override
    public boolean modFileExists(String path) {
        return Files.isRegularFile(ModList.get().getModFileById(McheliCePort.MOD_ID).getFile().findResource(path));
    }

    @Override
    public net.minecraft.nbt.CompoundTag persistentData(net.minecraft.world.entity.Entity entity) {
        return entity.getPersistentData();
    }

    @Override
    public net.minecraft.server.MinecraftServer currentServer() {
        return net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
    }

    @Override
    public void addPackSource(net.minecraft.server.packs.PackType type,
            java.util.function.Consumer<java.util.function.Consumer<net.minecraft.server.packs.repository.Pack>> source) {
        NeoForgeEvents.PACK_SOURCES.add(new NeoForgeEvents.Source(type, source));
    }

    @Override
    public net.minecraft.server.level.ServerPlayer fakePlayer(net.minecraft.server.level.ServerLevel level,
            com.mojang.authlib.GameProfile profile) {
        return net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, profile);
    }

    @Override
    public boolean explosionStart(net.minecraft.world.level.Level level, net.minecraft.world.level.Explosion explosion) {
        return net.neoforged.neoforge.event.EventHooks.onExplosionStart(level, explosion);
    }

    @Override
    public void explosionDetonate(net.minecraft.world.level.Level level, net.minecraft.world.level.Explosion explosion,
            java.util.List<net.minecraft.world.entity.Entity> entities, java.util.List<net.minecraft.core.BlockPos> blocks) {
        net.neoforged.neoforge.event.EventHooks.onExplosionDetonate(level, explosion, entities,
                explosion.radius() * 2.0);
    }

    static void walk(String folder, Path root, ModFileVisitor visitor) throws IOException {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                String relative = folder + "/" + root.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/");
                visitor.visit(relative, () -> Files.newInputStream(file));
            }
        }
    }
}
