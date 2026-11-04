package org.jtools.doccomparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsTest {

    @TempDir
    Path folder;

    @Test
    void aSettingIsKeptBesideTheProgramForTheNextTime() {
        Settings.in(folder).set(Settings.LOOK, "javax.swing.plaf.metal.MetalLookAndFeel");
        assertTrue(Files.isRegularFile(folder.resolve("doccomparator.properties")));
        assertEquals("javax.swing.plaf.metal.MetalLookAndFeel", Settings.in(folder).get(Settings.LOOK, "none"));
    }

    @Test
    void untilOneIsSetNothingIsWritten() {
        assertEquals("the default", Settings.in(folder).get(Settings.LOOK, "the default"));
        assertFalse(Files.exists(folder.resolve("doccomparator.properties")));
    }

    @Test
    void aFolderThatCannotBeWrittenInKeepsNothingAndIsNoError() throws IOException {
        Path locked = Files.createDirectory(folder.resolve("locked"));
        if (!Files.getFileStore(locked).supportsFileAttributeView("posix")) {
            return;   // Windows: no POSIX permissions to take away
        }
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            Settings settings = Settings.in(locked);
            settings.set(Settings.LOOK, "javax.swing.plaf.metal.MetalLookAndFeel");
            assertEquals("javax.swing.plaf.metal.MetalLookAndFeel", settings.get(Settings.LOOK, "none"), "held");
            assertFalse(Files.exists(locked.resolve("doccomparator.properties")));
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }
}
