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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The widths of the characters of the standard fonts a PDF may use without giving them — Helvetica (and Arial, made
 * to its metrics), Times (and Times New Roman), Courier —, so that where a word ends is known (standard-widths.txt,
 * written by scripts/standard-widths.py).
 */
final class StandardWidths {

    private static final Charset WIN_ANSI = Charset.forName("windows-1252");
    private static final Map<String, int[]> FONTS = load();

    private StandardWidths() {
    }

    private static Map<String, int[]> load() {
        Map<String, int[]> fonts = new HashMap<>();
        try (InputStream in = StandardWidths.class.getResourceAsStream("standard-widths.txt")) {
            if (in == null) {
                throw new IllegalStateException("standard-widths.txt is part of the program");
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.trim().split(" ");
                int[] widths = new int[fields.length - 1];
                for (int i = 1; i < fields.length; i++) {
                    widths[i - 1] = Integer.parseInt(fields[i]);
                }
                fonts.put(fields[0], widths);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return fonts;
    }

    /**
     * The width of a character in a standard font of this family and style, in thousandths of the size; 0 when the
     * family is not a standard one, or the character not one of its.
     */
    static int of(String family, boolean bold, boolean italic, String character) {
        String name = family.replace(" ", "").toLowerCase(Locale.ROOT);
        if (name.equals("courier") || name.equals("couriernew")) {
            return 600;
        }
        String font;
        if (name.equals("helvetica") || name.equals("arial")) {
            font = "Helvetica" + (bold && italic ? "-BoldOblique" : bold ? "-Bold" : italic ? "-Oblique" : "");
        } else if (name.equals("times") || name.equals("timesnewroman")) {
            font = "Times-" + (bold && italic ? "BoldItalic" : bold ? "Bold" : italic ? "Italic" : "Roman");
        } else {
            return 0;
        }
        int[] widths = FONTS.get(font);
        if (character == null || character.length() != 1 || widths == null) {
            return 0;
        }
        byte[] code = character.getBytes(WIN_ANSI);
        int index = code.length == 1 ? (code[0] & 0xFF) - 32 : -1;
        return index >= 0 && index < widths.length && !(code[0] == '?' && character.charAt(0) != '?')
                ? widths[index] : 0;
    }
}
