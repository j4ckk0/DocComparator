package org.jtools.doccomparator.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The texts of every language: the same keys in each file, none blank; every key the program names is in them, and
 * every key in them is named by the program — whole, or by its prefix for a key made as it runs ("aspect." + name).
 */
class I18nTest {

    private static final Path SOURCES = Path.of("src", "main", "java");
    /** A key as the program writes it: area.name…, or a prefix of keys made as it runs, ending with a dot. */
    private static final Pattern KEY = Pattern.compile("[a-z][a-zA-Z]*(\\.[a-zA-Z0-9_]+)*\\.?");

    @Test
    void everyLanguageHasTheSameKeysNoneBlank() throws IOException {
        Properties english = texts(I18nLanguage.ENGLISH);
        for (I18nLanguage language : I18nLanguage.values()) {
            Properties texts = texts(language);
            assertEquals(new TreeSet<>(english.stringPropertyNames()), new TreeSet<>(texts.stringPropertyNames()),
                    language + " has the keys English has");
            for (String key : texts.stringPropertyNames()) {
                assertFalse(texts.getProperty(key).isBlank(), language + ": " + key + " is blank");
            }
        }
    }

    @Test
    void everyKeyNamedIsThereAndEveryKeyThereIsNamed() throws IOException {
        Set<String> keys = texts(I18nLanguage.ENGLISH).stringPropertyNames();
        Set<String> areas = new TreeSet<>();
        keys.forEach(key -> areas.add(key.substring(0, key.indexOf('.'))));
        Set<String> named = new TreeSet<>();
        for (String literal : literals()) {
            if (KEY.matcher(literal).matches() && literal.contains(".")
                    && areas.contains(literal.substring(0, literal.indexOf('.')))) {
                named.add(literal);
            }
        }
        for (String name : named) {
            if (name.endsWith(".")) {
                assertTrue(keys.stream().anyMatch(key -> key.startsWith(name)), "no key starts with " + name);
            } else {
                assertTrue(keys.contains(name), "no text for " + name);
            }
        }
        for (String key : keys) {
            assertTrue(named.contains(key) || named.stream().anyMatch(name -> name.endsWith(".") && key.startsWith(name)),
                    key + " is named nowhere");
        }
    }

    /** I18nEnglishInTests is found and run: the tests' expected texts are English ones. */
    @Test
    void testsSpeakEnglish() {
        assertEquals(I18nLanguage.ENGLISH, I18n.language());
    }

    @Test
    void eachLanguageIsNamedInItselfAnUnknownOneIsTheDefault() {
        I18n.use(I18nLanguage.FRENCH);
        assertEquals("Français", I18nLanguage.FRENCH.autonym());
        assertEquals("English", I18nLanguage.ENGLISH.autonym());
        assertEquals(I18nLanguage.FRENCH, I18nLanguage.ofCode("xx"), "unknown: the default");
        assertEquals("a.key.not.there", I18n.text("a.key.not.there"), "a key missing: itself, the program goes on");
    }

    private static Properties texts(I18nLanguage language) throws IOException {
        Properties texts = new Properties();
        try (InputStream in = I18n.class.getResourceAsStream("messages_" + language.code() + ".properties")) {
            assertTrue(in != null, language + " has its file");
            texts.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return texts;
    }

    /** The string literals of the program's sources, their comments left out. */
    private static List<String> literals() throws IOException {
        List<String> literals = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                literals.addAll(literals(Files.readString(file)));
            }
        }
        return literals;
    }

    static List<String> literals(String source) {
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

    @Test
    void literalsAreReadOutsideComments() {
        assertEquals(List.of("a.b", "c"), literals("x(\"a.b\"); // \"not\"\n/* \"nor\" */ y('\"', \"c\");"));
    }
}
