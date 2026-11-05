package org.jtools.doccomparator.i18n;

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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;

/**
 * The texts a user reads, in the language in use: those of every jar there is, each providing its own (I18nTexts),
 * the same keys in each language. A text's arguments go in its {0}, {1}… as they are: no MessageFormat, whose quoting
 * would eat the apostrophes French is full of.
 */
public final class I18n {

    private static final List<I18nTexts> PROVIDERS = providers();

    private static volatile I18nLanguage language = I18nLanguage.DEFAULT;
    private static volatile Map<String, String> texts = load(I18nLanguage.DEFAULT);

    private I18n() {
    }

    /** Speaks this language from now on. */
    public static void use(I18nLanguage chosen) {
        texts = load(chosen);
        language = chosen;
    }

    public static I18nLanguage language() {
        return language;
    }

    /** The text of this key; the key itself for one no jar has (each jar's tests keep that from happening). */
    public static String text(String key) {
        String text = texts.get(key);
        return text != null ? text : key;
    }

    /** The text of this key, its {0}, {1}… replaced by these arguments. */
    public static String text(String key, Object... arguments) {
        String text = text(key);
        for (int i = 0; i < arguments.length; i++) {
            text = text.replace("{" + i + "}", String.valueOf(arguments[i]));
        }
        return text;
    }

    /** The jars' texts, as found (ServiceLoader): on the class path, or as modules that provide them. */
    public static List<I18nTexts> providers() {
        List<I18nTexts> found = new ArrayList<>();
        ServiceLoader.load(I18nTexts.class, I18nTexts.class.getClassLoader()).forEach(provider -> {
            if (found.stream().noneMatch(other -> other.getClass() == provider.getClass())) {
                found.add(provider);
            }
        });
        return List.copyOf(found);
    }

    /** One jar's texts in a language, as its file has them; none when it has no file for it. */
    public static Properties texts(I18nTexts provider, I18nLanguage language) {
        Properties texts = new Properties();
        try (InputStream in = provider.open(language)) {
            if (in != null) {
                texts.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return texts;
    }

    /** Every jar's texts in a language, those of the language asked for only: none of the machine's locale. */
    private static Map<String, String> load(I18nLanguage language) {
        Map<String, String> all = new HashMap<>();
        for (I18nTexts provider : PROVIDERS) {
            texts(provider, language).forEach((key, text) -> all.putIfAbsent((String) key, (String) text));
        }
        return Map.copyOf(all);
    }
}
