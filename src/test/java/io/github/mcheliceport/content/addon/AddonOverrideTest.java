package io.github.mcheliceport.content.addon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.ContentTestSupport;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.content.aircraft.PlaneInfo;
import io.github.mcheliceport.content.parser.txt.TxtParser;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The {@code mcheli_addons} folder as the game uses it: the stock pack extracted to
 * {@code default/}, and addon packs (a folder with YAML, a zip with TXT) that override stock
 * content and add new content.
 */
class AddonOverrideTest {

    private static final int STOCK_FILES = 372;

    @TempDir
    Path addonDir;

    @Test
    void addonPacksOverrideAndExtendStockContent() throws IOException {
        assertTrue(BuiltinAddonPack.ensureExtracted(this.addonDir, stockSource(), "1.0"));

        // A folder pack with YAML: replaces ah-64 and adds a helicopter.
        Path folderPack = this.addonDir.resolve("My Pack");
        writePackMeta(folderPack, "mypack");
        String ah64 = Files.readString(ContentTestSupport.stockPack().resolve("assets/mcheli/helicopters/ah-64.yml"));
        String changed = ah64.replace("MaxHP: 180", "MaxHP: 999").replace("Author: EMB4\n", "");
        write(folderPack.resolve("assets/mcheli/helicopters/ah-64.yml"), changed);
        write(folderPack.resolve("assets/mcheli/helicopters/Test_Heli.yml"),
                ah64.replace("AH-64D Apache Longbow", "Test Heli"));

        // A zip pack with TXT, loaded after the folder pack: replaces f22a.
        String f22a = Files.readString(
                ContentTestSupport.legacyPack().resolve("assets/mcheli/planes/f22a.txt"), StandardCharsets.UTF_8);
        writeZip(this.addonDir.resolve("zz_legacy.zip"), Map.of(
                "pack.mcmeta", packMeta("legacy"),
                "assets/mcheli/planes/f22a.txt", f22a.replace("MaxHp = 230", "MaxHp = 321")));

        List<AddonPack> packs = AddonManager.loadAddons(this.addonDir);
        assertEquals(List.of("@builtin", "mypack", "legacy"), packs.stream().map(AddonPack::getDomain).toList());

        ContentRegistries.Snapshot content = ContentRegistries.loadContents(packs);
        assertEquals(35, content.heli().size(), "34 stock helicopters plus one new");
        assertEquals(46, content.plane().size());
        assertEquals(234, content.weapon().size());

        HeliInfo overridden = content.heli().get("ah-64");
        assertNotNull(overridden);
        assertEquals(999, overridden.maxHp);
        assertEquals("mypack", overridden.getLocation().addonDomain());
        assertEquals("EMB4", overridden.author, "an override without an author keeps the replaced author");

        HeliInfo added = content.heli().get("test_heli");
        assertNotNull(added, "content names are lower case");
        assertEquals("Test Heli", added.displayName);

        PlaneInfo plane = content.plane().get("f22a");
        assertNotNull(plane);
        assertEquals(321, plane.maxHp);
        assertEquals(TxtParser.IDENTIFIER, plane.parserIdentifier);
        assertEquals("legacy", plane.getLocation().addonDomain());
    }

    @Test
    void stockPackIsExtractedOncePerModVersion() throws IOException {
        assertTrue(BuiltinAddonPack.ensureExtracted(this.addonDir, stockSource(), "1.0"));
        Path folder = BuiltinAddonPack.getFolder(this.addonDir);
        assertEquals(STOCK_FILES, countContentFiles(folder));
        assertTrue(Files.isRegularFile(folder.resolve("pack.mcmeta")));
        assertFalse(Files.exists(folder.resolve("assets/mcheli/textures")), "only content definitions are extracted");

        Path edited = folder.resolve("assets/mcheli/helicopters/ah-64.yml");
        Files.writeString(edited, "edited");
        assertFalse(BuiltinAddonPack.ensureExtracted(this.addonDir, stockSource(), "1.0"));
        assertEquals("edited", Files.readString(edited), "same version: left alone");

        assertTrue(BuiltinAddonPack.ensureExtracted(this.addonDir, stockSource(), "1.1"));
        assertTrue(Files.readString(edited).startsWith("Author: EMB4"), "new version: extracted again");
        assertEquals(STOCK_FILES, countContentFiles(folder));
    }

    private static BuiltinAddonPack.Source stockSource() {
        return BuiltinAddonPack.Source.ofFolder(ContentTestSupport.stockPack());
    }

    @Test
    void stockRevisionRefreshesExistingMilestoneInstallsWithoutTouchingOverrides() throws IOException {
        assertTrue(BuiltinAddonPack.ensureExtracted(addonDir, stockSource(), "0.5.1"));
        Path stock = BuiltinAddonPack.getFolder(addonDir);
        Path meta = stock.resolve("pack.mcmeta");
        var json = com.google.gson.JsonParser.parseString(Files.readString(meta)).getAsJsonObject();
        json.getAsJsonObject("addon").remove("port_content_revision");
        Files.writeString(meta, json.toString());
        Path lamp = stock.resolve("assets/mcheli/helicopters/bell207.yml");
        Files.writeString(lamp, "old stock");
        Path override = addonDir.resolve("custom/assets/mcheli/helicopters/bell207.yml");
        write(override, "custom override");
        assertTrue(BuiltinAddonPack.ensureExtracted(addonDir, stockSource(), "0.5.1"));
        assertTrue(Files.readString(lamp).contains("'#50FFFFFF'"));
        assertEquals("custom override", Files.readString(override));
        assertFalse(BuiltinAddonPack.ensureExtracted(addonDir, stockSource(), "0.5.1"));
        // A cached 0.5.1 revision-one pack predates the Viper/Chinook outlet definitions.
        json = com.google.gson.JsonParser.parseString(Files.readString(meta)).getAsJsonObject();
        json.getAsJsonObject("addon").addProperty("port_content_revision", 1);
        Files.writeString(meta, json.toString());
        Path viper = stock.resolve("assets/mcheli/helicopters/ah-1z.yml");
        Files.writeString(viper, "old stock without outlets");
        assertTrue(BuiltinAddonPack.ensureExtracted(addonDir, stockSource(), "0.5.1"));
        assertTrue(Files.readString(viper).contains("EngineOutlets:"));
        assertEquals("custom override", Files.readString(override));
    }

    private static long countContentFiles(Path pack) throws IOException {
        try (Stream<Path> files = Files.walk(pack.resolve("assets/mcheli"))) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    private static String packMeta(String domain) {
        return "{\"pack\": {\"pack_format\": 3, \"description\": \"" + domain + " test pack\"},"
                + " \"addon\": {\"domain\": \"" + domain + "\", \"version\": \"1.0\"}}";
    }

    private static void writePackMeta(Path pack, String domain) throws IOException {
        write(pack.resolve("pack.mcmeta"), packMeta(domain));
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static void writeZip(Path zip, Map<String, String> entries) throws IOException {
        try (FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + zip.toUri()), Map.of("create", "true"))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                Path file = fs.getPath(entry.getKey());
                if (file.getParent() != null) {
                    Files.createDirectories(file.getParent());
                }
                try (OutputStream out = Files.newOutputStream(file)) {
                    out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }
}
