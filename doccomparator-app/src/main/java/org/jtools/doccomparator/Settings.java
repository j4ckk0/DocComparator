package org.jtools.doccomparator;

/*-
 * #%L
 * DocComparator
 * %%
 * Copyright (C) 2026 jtools.org
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * The program's settings — its look, its language, the ribbon open or collapsed, each side's recent documents —, kept
 * in doccomparator.properties, beside the program (doccomparator.jar): in its folder, as a portable program keeps
 * them, never in the registry or the user's folder. Written only when a setting changes; a folder that cannot be
 * written in keeps nothing, and the program goes on.
 */
final class Settings {

    static final String LOOK = "look";
    static final String LANGUAGE = "language";
    /** Whether the ribbon is open: "true", or "false" when it is collapsed. */
    static final String RIBBON = "ribbon";
    /**
     * The documents last opened on the left, and on the right: lists ("recentLeft.1", "recentLeft.2"…), the latest
     * first.
     */
    static final String RECENT_LEFT = "recentLeft";
    static final String RECENT_RIGHT = "recentRight";
    private static final String FILE = "doccomparator.properties";

    private final Path file;
    private final Properties values = new Properties();

    private Settings(Path file) {
        this.file = file;
        if (file != null && Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                values.load(in);
            } catch (IOException | IllegalArgumentException _) {
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
        store();
    }

    /** A list kept under this key, in its order: its values under key.1, key.2… up to the first missing. */
    List<String> getList(String key) {
        List<String> list = new ArrayList<>();
        for (int i = 1; values.getProperty(key + "." + i) != null; i++) {
            list.add(values.getProperty(key + "." + i));
        }
        return list;
    }

    /** Sets a list under this key in place of the one there, all of it kept at once. */
    void setList(String key, List<String> list) {
        values.stringPropertyNames().stream().filter(name -> name.startsWith(key + "."))
                .forEach(values::remove);
        for (int i = 0; i < list.size(); i++) {
            values.setProperty(key + "." + (i + 1), list.get(i));
        }
        store();
    }

    /** Writes the settings, if the program's folder can be written in. */
    private void store() {
        if (file == null) {
            return;
        }
        try (OutputStream out = Files.newOutputStream(file)) {
            values.store(out, "DocComparator's settings");
        } catch (IOException _) {
            // a folder that cannot be written in: the setting holds until the program ends
        }
    }
}
