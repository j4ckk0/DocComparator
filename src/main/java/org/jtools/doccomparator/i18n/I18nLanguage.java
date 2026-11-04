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

import java.util.Locale;

/**
 * A language the program speaks: its texts in i18n/messages_&lt;code&gt;.properties. French until another is chosen.
 */
public enum I18nLanguage {

    FRENCH("fr"),
    ENGLISH("en");

    /** The language until one is chosen. */
    public static final I18nLanguage DEFAULT = FRENCH;

    private final String code;

    I18nLanguage(String code) {
        this.code = code;
    }

    /** Its ISO 639 code, which names its file and is kept in the settings: "fr", "en". */
    public String code() {
        return code;
    }

    public Locale locale() {
        return Locale.of(code);
    }

    /** Its own name, as its speakers write it: "Français", "English". */
    public String autonym() {
        return I18n.text("language." + code);
    }

    /** The language of this code; the default one for a code the program does not speak. */
    public static I18nLanguage ofCode(String code) {
        for (I18nLanguage language : values()) {
            if (language.code.equals(code)) {
                return language;
            }
        }
        return DEFAULT;
    }
}
