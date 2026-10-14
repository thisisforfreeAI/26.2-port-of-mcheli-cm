package io.github.mcheliceport.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
