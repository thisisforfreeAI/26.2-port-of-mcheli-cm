package io.github.mcheliceport.fabric;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.platform.Platform;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

public final class FabricPlatform implements Platform {

    @Override
    public String name() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public String modVersion() {
        return FabricLoader.getInstance().getModContainer(McheliCePort.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
    }

    @Override
    public java.util.List<io.github.mcheliceport.platform.ModDescriptor> mods() {
        return FabricLoader.getInstance().getAllMods().stream().map(c -> new io.github.mcheliceport.platform.ModDescriptor(
                c.getMetadata().getId(), c.getMetadata().getName(), c.getMetadata().getVersion().getFriendlyString(),
                c.getOrigin().getKind() == net.fabricmc.loader.api.metadata.ModOrigin.Kind.PATH
                        ? c.getOrigin().getPaths().get(0).getFileName().toString() : "")).toList();
    }

    @Override
    public boolean modFileExists(String path) {
        return FabricLoader.getInstance().getModContainer(McheliCePort.MOD_ID).flatMap(c -> c.findPath(path))
                .map(Files::isRegularFile).orElse(false);
    }

    @Override
    public net.minecraft.nbt.CompoundTag persistentData(net.minecraft.world.entity.Entity entity) {
        return FabricAttachments.persistentData(entity);
    }

    @Override
    public net.minecraft.server.MinecraftServer currentServer() {
        return FabricEvents.server;
    }

    @Override
    public void visitModFiles(String folder, ModFileVisitor visitor) throws IOException {
        Optional<Path> root = FabricLoader.getInstance().getModContainer(McheliCePort.MOD_ID)
                .flatMap(c -> c.findPath(folder));
        if (root.isEmpty() || !Files.isDirectory(root.get())) {
            return;
        }
        try (Stream<Path> files = Files.walk(root.get())) {
            for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                String relative = folder + "/" + root.get().relativize(file).toString()
                        .replace(file.getFileSystem().getSeparator(), "/");
                visitor.visit(relative, () -> Files.newInputStream(file));
            }
        }
    }

    public static final java.util.Map<net.minecraft.server.packs.PackType,
            java.util.List<java.util.function.Consumer<java.util.function.Consumer<net.minecraft.server.packs.repository.Pack>>>>
            PACK_SOURCES = new java.util.EnumMap<>(net.minecraft.server.packs.PackType.class);

    @Override
    public void addPackSource(net.minecraft.server.packs.PackType type,
            java.util.function.Consumer<java.util.function.Consumer<net.minecraft.server.packs.repository.Pack>> source) {
        PACK_SOURCES.computeIfAbsent(type, t -> new java.util.ArrayList<>()).add(source);
    }

    @Override
    public net.minecraft.server.level.ServerPlayer fakePlayer(net.minecraft.server.level.ServerLevel level,
            com.mojang.authlib.GameProfile profile) {
        return net.fabricmc.fabric.api.entity.FakePlayer.get(level, profile);
    }

    @Override
    public boolean explosionStart(net.minecraft.world.level.Level level, net.minecraft.world.level.Explosion explosion) {
        return false;
    }

    @Override
    public void explosionDetonate(net.minecraft.world.level.Level level, net.minecraft.world.level.Explosion explosion,
            java.util.List<net.minecraft.world.entity.Entity> entities, java.util.List<net.minecraft.core.BlockPos> blocks) {
    }
}
