package org.jtools.doccomparator.i18n;

import java.util.Locale;

/** A language the program speaks: its texts in i18n/messages_&lt;code&gt;.properties. French until another is chosen. */
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
