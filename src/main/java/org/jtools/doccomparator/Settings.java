package org.jtools.doccomparator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * The program's settings — its look, its language —, kept in doccomparator.properties, beside the program (doccomparator.jar): in
 * its folder, as a portable program keeps them, never in the registry or the user's folder. Written only when a
 * setting changes; a folder that cannot be written in keeps nothing, and the program goes on.
 */
final class Settings {

    static final String LOOK = "look";
    static final String LANGUAGE = "language";
    private static final String FILE = "doccomparator.properties";

    private final Path file;
    private final Properties values = new Properties();

    private Settings(Path file) {
        this.file = file;
        if (file != null && Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                values.load(in);
            } catch (IOException | IllegalArgumentException e) {
                // unreadable: the defaults
            }
        }
    }

    /** The settings beside the program's jar; none kept when the program runs from its classes. */
    static Settings load() {
        String classPath = System.getProperty("java.class.path", "");
        if (!classPath.endsWith(".jar") || classPath.contains(File.pathSeparator)) {
            return new Settings(null);
        }
        Path folder = Path.of(classPath).toAbsolutePath().getParent();
        return folder == null ? new Settings(null) : in(folder);
    }

    /** The settings kept in this folder. */
    static Settings in(Path folder) {
        return new Settings(folder.resolve(FILE));
    }

    String get(String key, String otherwise) {
        return values.getProperty(key, otherwise);
    }

    /** Sets a value, and keeps it if the program's folder can be written in. */
    void set(String key, String value) {
        values.setProperty(key, value);
        if (file == null) {
            return;
        }
        try (OutputStream out = Files.newOutputStream(file)) {
            values.store(out, "DocComparator's settings");
        } catch (IOException e) {
            // a folder that cannot be written in: the setting holds until the program ends
        }
    }
}
