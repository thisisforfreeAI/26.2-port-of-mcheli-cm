package io.github.mcheliceport.content.emitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.ContentTestSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code /mcheli dumpyaml}: TXT content is written as YAML, content loaded from YAML is skipped. */
class YamlDumpTest {

    @TempDir
    Path out;

    @Test
    void dumpsTxtContentOnly() throws IOException {
        ContentRegistries.Snapshot legacy = ContentTestSupport.load(ContentTestSupport.legacyPack());
        YamlDump.Result result = YamlDump.dump(legacy, this.out.resolve("txt"));
        assertEquals(new YamlDump.Result(372, 0), result);
        assertTrue(Files.isRegularFile(this.out.resolve("txt/helicopters/ah-64.yml")));
        assertTrue(Files.isRegularFile(this.out.resolve("txt/hud/heli.yml")));

        // The dump loads back as a pack with the same content.
        ContentRegistries.Snapshot reloaded = ContentTestSupport.load(this.dumpAsPack());
        assertEquals(legacy.totalSize(), reloaded.totalSize());

        ContentRegistries.Snapshot stock = ContentTestSupport.load(ContentTestSupport.stockPack());
        assertEquals(new YamlDump.Result(0, 0), YamlDump.dump(stock, this.out.resolve("yaml")));
    }

    /** Moves the dump under {@code assets/mcheli/}, where a pack keeps its content. */
    private Path dumpAsPack() throws IOException {
        Path pack = this.out.resolve("pack");
        Path content = pack.resolve("assets/mcheli");
        Files.createDirectories(content.getParent());
        Files.move(this.out.resolve("txt"), content);
        try (Stream<Path> files = Files.list(content)) {
            assertEquals(8, files.count(), "one folder per content type");
        }
        return pack;
    }
}
