package io.github.mcheliceport.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McheliCommandTest {

    @Test
    void dumpFolderMustStayInTheGameFolder() {
        assertTrue(McheliCommand.isSafeFolderName(McheliCommand.DEFAULT_DUMP_FOLDER));
        assertTrue(McheliCommand.isSafeFolderName("my-dump.2"));
        assertFalse(McheliCommand.isSafeFolderName(".."));
        assertFalse(McheliCommand.isSafeFolderName("..."));
        assertFalse(McheliCommand.isSafeFolderName("../config"));
        assertFalse(McheliCommand.isSafeFolderName("a/b"));
        assertFalse(McheliCommand.isSafeFolderName("C:"));
        assertFalse(McheliCommand.isSafeFolderName(""));
    }

    /** Upstream's {@code CommandPermission} entries: {@code name: player, player}, in any case. */
    @Test
    void permissionEntries() {
        List<String> entries = List.of("title: Alice, bob", "FILL:carol", "broken", "status: a: b");
        assertTrue(McheliCommand.isListed(entries, "title", "alice"));
        assertTrue(McheliCommand.isListed(entries, "title", "Bob"));
        assertTrue(McheliCommand.isListed(entries, "fill", "Carol"));
        assertFalse(McheliCommand.isListed(entries, "fill", "Alice"));
        assertFalse(McheliCommand.isListed(entries, "status", "a"));
        assertFalse(McheliCommand.isListed(List.of(), "title", "Alice"));
    }

    @Test
    void statusCountsByClassInOrder() {
        Map<String, Integer> counts = McheliCommand.countByClass(List.of("a", 1, "b", 2L, 3));
        assertEquals(List.of("java.lang.Integer", "java.lang.Long", "java.lang.String"), List.copyOf(counts.keySet()));
        assertEquals(2, counts.get("java.lang.Integer"));
        assertEquals(2, counts.get("java.lang.String"));
    }
}
