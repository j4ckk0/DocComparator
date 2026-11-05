package org.jtools.doccomparator.pdf;

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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;

/**
 * What the codes of a simple font stand for: its base encoding (Standard, WinAnsi, MacRoman), each code's
 * character; and the glyph names a font's /Differences give, as Unicode: Adobe's names of the Latin characters, a
 * letter's accented forms, uniXXXX and uXXXX, ligatures joined by "_".
 */
final class Encodings {

    /** A code no character is known for. */
    static final String UNKNOWN = "�";

    private static final String[] WIN_ANSI = table(Charset.forName("windows-1252"));
    private static final String[] MAC_ROMAN = table(macRoman());
    private static final String[] STANDARD = standard();

    private static final Map<String, String> NAMES = new HashMap<>();
    private static final Map<String, Character> ACCENTS = Map.ofEntries(
            Map.entry("grave", '̀'), Map.entry("acute", '́'), Map.entry("circumflex", '̂'),
            Map.entry("tilde", '̃'), Map.entry("macron", '̄'), Map.entry("breve", '̆'),
            Map.entry("dotaccent", '̇'), Map.entry("dieresis", '̈'), Map.entry("ring", '̊'),
            Map.entry("hungarumlaut", '̋'), Map.entry("caron", '̌'), Map.entry("cedilla", '̧'),
            Map.entry("ogonek", '̨'), Map.entry("commaaccent", '̦'));

    static {
        String[][] names = {
            {"space", " "}, {"exclam", "!"}, {"quotedbl", "\""}, {"numbersign", "#"}, {"dollar", "$"},
            {"percent", "%"}, {"ampersand", "&"}, {"quotesingle", "'"}, {"quoteright", "’"},
            {"parenleft", "("}, {"parenright", ")"}, {"asterisk", "*"}, {"plus", "+"}, {"comma", ","},
            {"hyphen", "-"}, {"period", "."}, {"slash", "/"}, {"colon", ":"}, {"semicolon", ";"}, {"less", "<"},
            {"equal", "="}, {"greater", ">"}, {"question", "?"}, {"at", "@"}, {"bracketleft", "["},
            {"backslash", "\\"}, {"bracketright", "]"}, {"asciicircum", "^"}, {"underscore", "_"},
            {"grave", "`"}, {"quoteleft", "‘"}, {"braceleft", "{"}, {"bar", "|"}, {"braceright", "}"},
            {"asciitilde", "~"}, {"zero", "0"}, {"one", "1"}, {"two", "2"}, {"three", "3"}, {"four", "4"},
            {"five", "5"}, {"six", "6"}, {"seven", "7"}, {"eight", "8"}, {"nine", "9"},
            {"exclamdown", "¡"}, {"cent", "¢"}, {"sterling", "£"}, {"currency", "¤"}, {"yen", "¥"},
            {"brokenbar", "¦"}, {"section", "§"}, {"dieresis", "¨"}, {"copyright", "©"}, {"ordfeminine", "ª"},
            {"guillemotleft", "«"}, {"guillemetleft", "«"}, {"logicalnot", "¬"}, {"registered", "®"},
            {"macron", "¯"}, {"degree", "°"}, {"plusminus", "±"}, {"twosuperior", "²"}, {"threesuperior", "³"},
            {"acute", "´"}, {"mu", "µ"}, {"paragraph", "¶"}, {"periodcentered", "·"}, {"cedilla", "¸"},
            {"onesuperior", "¹"}, {"ordmasculine", "º"}, {"guillemotright", "»"}, {"guillemetright", "»"},
            {"onequarter", "¼"}, {"onehalf", "½"}, {"threequarters", "¾"}, {"questiondown", "¿"},
            {"multiply", "×"}, {"divide", "÷"}, {"Eth", "Ð"}, {"eth", "ð"}, {"Thorn", "Þ"}, {"thorn", "þ"},
            {"germandbls", "ß"}, {"AE", "Æ"}, {"ae", "æ"}, {"Oslash", "Ø"}, {"oslash", "ø"}, {"OE", "Œ"},
            {"oe", "œ"}, {"Lslash", "Ł"}, {"lslash", "ł"}, {"dotlessi", "ı"}, {"dotlessj", "ȷ"},
            {"Euro", "€"}, {"quotesinglbase", "‚"}, {"florin", "ƒ"}, {"quotedblbase", "„"},
            {"ellipsis", "…"}, {"dagger", "†"}, {"daggerdbl", "‡"}, {"circumflex", "ˆ"}, {"perthousand", "‰"},
            {"guilsinglleft", "‹"}, {"guilsinglright", "›"}, {"quotedblleft", "“"}, {"quotedblright", "”"},
            {"bullet", "•"}, {"endash", "–"}, {"emdash", "—"}, {"tilde", "˜"}, {"trademark", "™"},
            {"fi", "ﬁ"}, {"fl", "ﬂ"}, {"ff", "ﬀ"}, {"ffi", "ﬃ"}, {"ffl", "ﬄ"}, {"fraction", "⁄"},
            {"breve", "˘"}, {"dotaccent", "˙"}, {"ring", "˚"}, {"ogonek", "˛"}, {"caron", "ˇ"},
            {"hungarumlaut", "˝"}, {"minus", "−"}, {"nbspace", " "}, {"nonbreakingspace", " "},
            {"sfthyphen", "­"}, {"softhyphen", "­"}, {"middot", "·"}, {"lozenge", "◊"},
            {"arrowleft", "←"}, {"arrowright", "→"}, {"arrowup", "↑"}, {"arrowdown", "↓"},
            {"notequal", "≠"}, {"lessequal", "≤"}, {"greaterequal", "≥"}, {"infinity", "∞"},
            {"approxequal", "≈"}, {"checkmark", "✓"}, {"square", "□"}, {"filledbox", "■"},
            {"circle", "○"}, {"blackcircle", "●"}, {"triagrt", "►"}, {"Omega", "Ω"}, {"pi", "π"},
            {"Delta", "∆"}, {"summation", "∑"}, {"product", "∏"}, {"radical", "√"}, {"partialdiff", "∂"},
            {"integral", "∫"}, {"numero", "№"}, {"estimated", "℮"}, {"afii61352", "№"},
            {"thinspace", "\u2009"}, {"hairspace", "\u200A"}, {"enspace", "\u2002"}, {"emspace", "\u2003"},
            {"figurespace", "\u2007"}, {"zerowidthspace", "\u200B"}, {"nbhyphen", "\u2011"},
            {"figuredash", "\u2012"}, {"quotereversed", "\u201B"}, {"apostrophe", "'"},
        };
        for (String[] name : names) {
            NAMES.put(name[0], name[1]);
        }
    }

    private Encodings() {
    }

    private static Charset macRoman() {
        try {
            return Charset.forName("x-MacRoman");
        } catch (IllegalArgumentException _) {
            return StandardCharsets.ISO_8859_1;
        }
    }

    private static String[] table(Charset charset) {
        String[] table = new String[256];
        for (int code = 0; code < 256; code++) {
            String character = new String(new byte[] {(byte) code}, charset);
            table[code] = code < 32 || character.equals(UNKNOWN) ? null : character;
        }
        return table;
    }

    private static String[] standard() {
        String[] table = new String[256];
        for (int code = 32; code < 127; code++) {
            table[code] = String.valueOf((char) code);
        }
        table[0x27] = "’";
        table[0x60] = "‘";
        String high = "¡¢£⁄¥ƒ§¤'“«‹›ﬁﬂ";
        for (int i = 0; i < high.length(); i++) {
            table[0xA1 + i] = String.valueOf(high.charAt(i));
        }
        String[][] rest = {{"B1", "–"}, {"B2", "†"}, {"B3", "‡"}, {"B4", "·"}, {"B6", "¶"}, {"B7", "•"},
            {"B8", "‚"}, {"B9", "„"}, {"BA", "”"}, {"BB", "»"}, {"BC", "…"}, {"BD", "‰"}, {"BF", "¿"},
            {"C1", "`"}, {"C2", "´"}, {"C3", "ˆ"}, {"C4", "˜"}, {"C5", "¯"}, {"C6", "˘"}, {"C7", "˙"},
            {"C8", "¨"}, {"CA", "˚"}, {"CB", "¸"}, {"CD", "˝"}, {"CE", "˛"}, {"CF", "ˇ"}, {"D0", "—"},
            {"E1", "Æ"}, {"E3", "ª"}, {"E8", "Ł"}, {"E9", "Ø"}, {"EA", "Œ"}, {"EB", "º"}, {"F1", "æ"},
            {"F5", "ı"}, {"F8", "ł"}, {"F9", "ø"}, {"FA", "œ"}, {"FB", "ß"}};
        for (String[] entry : rest) {
            table[Integer.parseInt(entry[0], 16)] = entry[1];
        }
        return table;
    }

    /** The characters of a base encoding by its name, StandardEncoding's for a name it does not know. */
    static String[] base(String name) {
        String[] table = switch (name == null ? "" : name) {
            case "WinAnsiEncoding" -> WIN_ANSI;
            case "MacRomanEncoding", "MacExpertEncoding" -> MAC_ROMAN;
            default -> STANDARD;
        };
        return table.clone();
    }

    /** The characters a glyph name stands for, null when it says none (g12, cid345, a name of the font's own). */
    static String glyph(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String known = NAMES.get(name);
        if (known != null) {
            return known;
        }
        if (name.length() == 1 && Character.isLetter(name.charAt(0)) && name.charAt(0) < 128) {
            return name;
        }
        int dot = name.indexOf('.');
        if (dot > 0) {
            return glyph(name.substring(0, dot));   // a variant: "a.sc", "one.oldstyle"
        }
        if (name.indexOf('_') > 0) {
            StringBuilder ligature = new StringBuilder();
            for (String part : name.split("_")) {
                String character = glyph(part);
                if (character == null) {
                    return null;
                }
                ligature.append(character);
            }
            return ligature.toString();
        }
        if (name.startsWith("uni") && name.length() >= 7 && (name.length() - 3) % 4 == 0) {
            StringBuilder characters = new StringBuilder();
            for (int i = 3; i < name.length(); i += 4) {
                Integer code = hex(name.substring(i, i + 4));
                if (code == null) {
                    return null;
                }
                characters.append((char) (int) code);
            }
            return characters.toString();
        }
        if (name.startsWith("u") && name.length() >= 5 && name.length() <= 7) {
            Integer code = hex(name.substring(1));
            if (code != null && Character.isValidCodePoint(code)) {
                return Character.toString(code);
            }
        }
        for (Map.Entry<String, Character> accent : ACCENTS.entrySet()) {
            String base = name.length() > accent.getKey().length() && name.endsWith(accent.getKey())
                    ? name.substring(0, name.length() - accent.getKey().length()) : null;
            if (base != null && base.length() == 1 && Character.isLetter(base.charAt(0))) {
                String composed = Normalizer.normalize(base + accent.getValue(), Normalizer.Form.NFC);
                if (composed.length() == 1) {
                    return composed;
                }
            }
        }
        return null;
    }

    private static Integer hex(String digits) {
        if (digits.isEmpty() || digits.length() > 6) {
            return null;
        }
        for (int i = 0; i < digits.length(); i++) {
            if (Character.digit(digits.charAt(i), 16) < 0) {
                return null;
            }
        }
        return Integer.parseInt(digits, 16);
    }
}
