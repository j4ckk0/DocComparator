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

import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * The texts a user reads, in the language in use: one file per language, i18n/messages_&lt;code&gt;.properties, the
 * same keys in each (I18nTest). A text's arguments go in its {0}, {1}… as they are: no MessageFormat, whose quoting
 * would eat the apostrophes French is full of.
 */
public final class I18n {

    private static final String BUNDLE = "org.jtools.doccomparator.i18n.messages";
    /** Only the file of the language asked for: none taken from the machine's locale instead. */
    private static final ResourceBundle.Control EXACTLY = ResourceBundle.Control.getNoFallbackControl(
            ResourceBundle.Control.FORMAT_PROPERTIES);

    private static volatile I18nLanguage language = I18nLanguage.DEFAULT;
    private static volatile ResourceBundle texts = load(I18nLanguage.DEFAULT);

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

    /** The text of this key; the key itself for one the file has not (I18nTest keeps that from happening). */
    public static String text(String key) {
        try {
            return texts.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    /** The text of this key, its {0}, {1}… replaced by these arguments. */
    public static String text(String key, Object... arguments) {
        String text = text(key);
        for (int i = 0; i < arguments.length; i++) {
            text = text.replace("{" + i + "}", String.valueOf(arguments[i]));
        }
        return text;
    }

    private static ResourceBundle load(I18nLanguage language) {
        return ResourceBundle.getBundle(BUNDLE, language.locale(), I18n.class.getClassLoader(), EXACTLY);
    }
}
