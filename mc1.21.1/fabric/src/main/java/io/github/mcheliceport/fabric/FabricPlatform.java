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
    public void visitModFiles(String folder, ModFileVisitor visitor) throws IOException {
        Optional<Path> root = FabricLoader.getInstance().getModContainer(McheliCePort.MOD_ID)
                .flatMap(c -> c.findPath(folder));
        if (root.isEmpty() || !Files.isDirectory(root.get())) {
            return;
        }
        try (Stream<Path> files = Files.walk(root.get())) {
            for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                String relative = root.get().relativize(file).toString()
                        .replace(file.getFileSystem().getSeparator(), "/");
                visitor.visit(relative, () -> Files.newInputStream(file));
            }
        }
    }
}
