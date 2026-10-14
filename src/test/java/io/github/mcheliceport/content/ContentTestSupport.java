package io.github.mcheliceport.content;

import io.github.mcheliceport.content.addon.AddonPack;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Locates the content packs the tests load and loads them. */
final class ContentTestSupport {

    private ContentTestSupport() {
    }

    /** The repository root; set by build.gradle because the test JVM may run in another directory. */
    static Path projectDir() {
        String dir = System.getProperty("mcheli.projectDir");
        Path path = dir != null ? Path.of(dir) : Path.of("").toAbsolutePath();
        if (!Files.isDirectory(path.resolve("src/main/resources/assets/mcheli"))) {
            throw new IllegalStateException("Cannot find the project directory (mcheli.projectDir=" + dir + ")");
        }
        return path;
    }

    /** The stock YAML content that ships in the mod jar, laid out as a pack. */
    static Path stockPack() {
        return projectDir().resolve("src/main/resources");
    }

    /** Upstream's legacy TXT versions of the stock content, used as test fixtures. */
    static Path legacyPack() {
        return projectDir().resolve("src/test/resources/legacy");
    }

    static ContentRegistries.Snapshot load(Path... packs) {
        return ContentRegistries.loadContents(java.util.Arrays.stream(packs).map(AddonPack::create).toList());
    }

    static ContentRegistries.Snapshot load(List<Path> packs) {
        return ContentRegistries.loadContents(packs.stream().map(AddonPack::create).toList());
    }
}
