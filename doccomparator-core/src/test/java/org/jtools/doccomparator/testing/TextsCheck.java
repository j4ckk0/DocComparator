package org.jtools.doccomparator.testing;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jtools.doccomparator.i18n.I18n;
import org.jtools.doccomparator.i18n.I18nLanguage;
import org.jtools.doccomparator.i18n.I18nTexts;

/**
 * What a jar's texts must be (I18nTexts), checked by each jar's tests on its own: the same keys in every language,
 * none blank, none another jar's; every key its sources name is there, in its texts or a jar's below it, and every
 * key of its texts is named by its sources — whole, or by its prefix for a key made as it runs ("aspect." + name).
 */
public final class TextsCheck {

    /** A key as the program writes it: area.name…, or a prefix of keys made as it runs, ending with a dot. */
    private static final Pattern KEY = Pattern.compile("[a-z][a-zA-Z]*(\\.[a-zA-Z0-9_]+)*\\.?");

    private TextsCheck() {
    }

    /** Checks these texts, those of the jar whose Java sources are there (src/main/java). */
    public static void check(I18nTexts own, Path sources) throws IOException {
        Set<String> keys = I18n.texts(own, I18nLanguage.ENGLISH).stringPropertyNames();
        assertFalse(keys.isEmpty(), own.getClass().getSimpleName() + " has its texts");
        for (I18nLanguage language : I18nLanguage.values()) {
            var texts = I18n.texts(own, language);
            assertEquals(new TreeSet<>(keys), new TreeSet<>(texts.stringPropertyNames()),
                    language + " has the keys English has");
            for (String key : texts.stringPropertyNames()) {
                assertFalse(texts.getProperty(key).isBlank(), language + ": " + key + " is blank");
            }
        }
        Set<String> others = new TreeSet<>();
        for (I18nTexts provider : I18n.providers()) {
            if (provider.getClass() != own.getClass()) {
                others.addAll(I18n.texts(provider, I18nLanguage.ENGLISH).stringPropertyNames());
            }
        }
        for (String key : keys) {
            assertFalse(others.contains(key), key + " is another jar's key too");
        }
        Set<String> all = new TreeSet<>(keys);
        all.addAll(others);
        Set<String> areas = new TreeSet<>();
        all.forEach(key -> areas.add(key.substring(0, key.indexOf('.'))));
        Set<String> named = new TreeSet<>();
        for (String literal : literals(sources)) {
            if (KEY.matcher(literal).matches() && literal.contains(".")
                    && areas.contains(literal.substring(0, literal.indexOf('.')))) {
                named.add(literal);
            }
        }
        for (String name : named) {
            if (name.endsWith(".")) {
                assertTrue(all.stream().anyMatch(key -> key.startsWith(name)), "no key starts with " + name);
            } else {
                assertTrue(all.contains(name), "no text for " + name);
            }
        }
        for (String key : keys) {
            assertTrue(named.contains(key) || named.stream().anyMatch(name -> name.endsWith(".") && key.startsWith(name)),
                    key + " is named nowhere in " + sources);
        }
    }

    /** The string literals of the sources there, their comments left out. */
    private static List<String> literals(Path sources) throws IOException {
        List<String> literals = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sources)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                literals.addAll(literals(Files.readString(file)));
            }
        }
        return literals;
    }

    public static List<String> literals(String source) {
        List<String> literals = new ArrayList<>();
        int i = 0;
        while (i < source.length()) {
            if (source.startsWith("//", i)) {
                i = source.indexOf('\n', i) < 0 ? source.length() : source.indexOf('\n', i);
            } else if (source.startsWith("/*", i)) {
                i = source.indexOf("*/", i + 2) + 2;
            } else if (source.startsWith("\"\"\"", i)) {
                i = source.indexOf("\"\"\"", i + 3) + 3;
            } else if (source.charAt(i) == '"' || source.charAt(i) == '\'') {
                char quote = source.charAt(i);
                StringBuilder literal = new StringBuilder();
                for (i++; source.charAt(i) != quote; i++) {
                    if (source.charAt(i) == '\\') {
                        i++;
                    }
                    literal.append(source.charAt(i));
                }
                i++;
                if (quote == '"') {
                    literals.add(literal.toString());
                }
            } else {
                i++;
            }
        }
        return literals;
    }

}
