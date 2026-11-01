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

    /** This mod's version string from its metadata. */
    String modVersion();

    /**
     * Visits the files under {@code folder} inside this mod's jar (or its build output folders
     * in development). Paths given to the visitor are relative to {@code folder} and use '/'.
     */
    void visitModFiles(String folder, ModFileVisitor visitor) throws java.io.IOException;

    @FunctionalInterface
    interface ModFileVisitor {
        void visit(String relativePath, IoSupplier opener) throws java.io.IOException;
    }

    @FunctionalInterface
    interface IoSupplier {
        java.io.InputStream open() throws java.io.IOException;
    }
}
