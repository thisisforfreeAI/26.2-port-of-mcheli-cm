package io.github.mcheliceport.resources;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.flag.FeatureFlagSet;
import org.jspecify.annotations.Nullable;

/**
 * A pack whose files are generated in memory each time the game opens it, that is at every
 * resource or data reload. It replaces upstream's {@code mcheli_addons/generated/} folder, which
 * upstream wrote to disk at startup.
 */
public final class GeneratedPack implements PackResources {

    private final PackLocationInfo location;
    private final PackType packType;
    private final Map<Identifier, byte[]> files;

    private GeneratedPack(PackLocationInfo location, PackType packType, Map<Identifier, byte[]> files) {
        this.location = location;
        this.packType = packType;
        this.files = new TreeMap<>(files);
    }

    /**
     * A hidden pack that is always enabled.
     *
     * @param contents builds the files of the pack, keyed by resource location, for example
     *                 {@code mcheli_ce_port:lang/en_us.json} for {@code assets/mcheli_ce_port/lang/en_us.json}
     */
    public static Pack create(String id, Component title, PackType packType, Pack.Position position,
            Supplier<Map<Identifier, byte[]>> contents) {
        PackLocationInfo location = new PackLocationInfo(id, title, PackSource.BUILT_IN, Optional.empty());
        Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo info) {
                return new GeneratedPack(info, packType, contents.get());
            }

            @Override
            public PackResources openFull(PackLocationInfo info, Pack.Metadata metadata) {
                return this.openPrimary(info);
            }
        };
        Pack.Metadata metadata = new Pack.Metadata(title, PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of(), true);
        return new Pack(location, supplier, metadata, new PackSelectionConfig(true, position, false));
    }

    @Override
    public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
        return null;
    }

    @Override
    public @Nullable IoSupplier<InputStream> getResource(PackType type, Identifier location) {
        byte[] data = type == this.packType ? this.files.get(location) : null;
        return data == null ? null : () -> new ByteArrayInputStream(data);
    }

    @Override
    public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
        if (type != this.packType) {
            return;
        }
        String prefix = directory.endsWith("/") ? directory : directory + "/";
        for (Map.Entry<Identifier, byte[]> entry : this.files.entrySet()) {
            Identifier id = entry.getKey();
            if (id.getNamespace().equals(namespace) && id.getPath().startsWith(prefix)) {
                byte[] data = entry.getValue();
                output.accept(id, () -> new ByteArrayInputStream(data));
            }
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        Set<String> namespaces = new TreeSet<>();
        if (type == this.packType) {
            this.files.keySet().forEach(id -> namespaces.add(id.getNamespace()));
        }
        return namespaces;
    }

    @Override
    public <T> @Nullable T getMetadataSection(MetadataSectionType<T> type) {
        return null;
    }

    @Override
    public PackLocationInfo location() {
        return this.location;
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    @Override
    public void close() {
    }

    /** The generated files, for tests and the dump command. */
    public Map<Identifier, byte[]> files() {
        return this.files;
    }
}
