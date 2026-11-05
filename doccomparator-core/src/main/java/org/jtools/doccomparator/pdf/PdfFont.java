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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jtools.doccomparator.pdf.PdfObjects.Bytes;
import org.jtools.doccomparator.pdf.PdfObjects.Stream;

/**
 * A font of a page, as reading its text needs it: how a string's bytes split into codes, the characters each code
 * stands for (ToUnicode first, then the encoding), each code's width, and what the font says of itself — its family,
 * bold, italic. Its glyphs are never drawn, so the font program is not read (a Type 1's encoding aside).
 */
final class PdfFont {

    /** A code of a string: the characters it stands for (null when unknown), its width in text space, unscaled. */
    record Code(String characters, double width, boolean singleByteSpace) {
    }

    private static final Pattern SUBSET = Pattern.compile("^[A-Z]{6}\\+");
    private static final Pattern STYLE_WORDS = Pattern.compile(
            "(?i)(bold|black|heavy|italic|oblique|regular|roman|book|medium|light|semibold|demibold|extrabold)$");
    private static final Pattern TYPE1_ENCODING = Pattern.compile("dup\\s+(\\d+)\\s*/([^\\s/]+)\\s+put");

    final String family;
    final boolean bold;
    final boolean italic;
    /** Its whole name, without a subset's prefix: what tells it in a message. */
    final String name;
    private final boolean composite;
    private final CMap toUnicode;
    private final CMap encodingCMap;   // a composite font's own Encoding, null for Identity
    private final boolean unicodeCodes;   // a composite font whose codes are UCS-2 (Uni…-UCS2-H)
    private final String[] simpleEncoding;
    private final Map<Integer, Double> widths = new HashMap<>();
    private final double defaultWidth;
    private final double scale;   // glyph space to text space: 1/1000, or a Type 3 font's matrix
    /** Whether the characters of its codes cannot be known: a composite font without ToUnicode. */
    final boolean unreadable;

    private PdfFont(Map<String, Object> font, PdfFile file) {
        String subtype = PdfObjects.name(file.resolve(font.get("Subtype")));
        composite = "Type0".equals(subtype);
        Map<String, Object> descendant = null;
        if (composite) {
            List<?> descendants = PdfObjects.array(file.resolve(font.get("DescendantFonts")));
            descendant = descendants == null || descendants.isEmpty() ? null
                    : PdfObjects.dictionary(file.resolve(descendants.getFirst()));
        }
        Map<String, Object> described = descendant != null ? descendant : font;
        Map<String, Object> descriptor = PdfObjects.dictionary(file.resolve(described.get("FontDescriptor")));
        String baseFont = PdfObjects.name(file.resolve(font.get("BaseFont")));
        if (baseFont == null && descendant != null) {
            baseFont = PdfObjects.name(file.resolve(descendant.get("BaseFont")));
        }
        if (baseFont == null && descriptor != null) {
            baseFont = PdfObjects.name(file.resolve(descriptor.get("FontName")));
        }
        if (baseFont == null) {
            baseFont = "Type3".equals(subtype) ? "Type3" : "";
        }
        name = SUBSET.matcher(baseFont).replaceFirst("").replaceAll("-(Identity|UCS2|UTF16)-[HV]$", "");
        String[] familyAndStyle = familyAndStyle(name);
        String declared = descriptor != null && file.resolve(descriptor.get("FontFamily")) instanceof Bytes bytes
                ? text(bytes.value()) : null;
        family = declared != null && !declared.isBlank() ? declared.strip() : spaced(familyAndStyle[0]);
        String style = familyAndStyle[1].toLowerCase(Locale.ROOT);
        int flags = descriptor == null ? 0 : PdfObjects.integer(file.resolve(descriptor.get("Flags")), 0);
        double weight = descriptor == null ? 0 : PdfObjects.number(file.resolve(descriptor.get("FontWeight")), 0);
        double angle = descriptor == null ? 0 : PdfObjects.number(file.resolve(descriptor.get("ItalicAngle")), 0);
        bold = style.contains("bold") && !style.contains("semibold") && !style.contains("demibold")
                || style.contains("black") || style.contains("heavy") || weight >= 700
                || (flags & (1 << 18)) != 0;
        italic = style.contains("italic") || style.contains("oblique") || style.matches("(.*[^a-z])?it")
                || (flags & (1 << 6)) != 0 || Math.abs(angle) > 0.5;

        Object toUnicodeValue = file.resolve(font.get("ToUnicode"));
        CMap unicode = null;
        if (toUnicodeValue instanceof Stream stream) {
            try {
                byte[] bytes = file.decoded(stream);
                unicode = bytes == null ? null : CMap.parse(bytes);
            } catch (PdfException _) {
                unicode = null;
            }
        }
        toUnicode = unicode;

        double fontScale = 0.001;
        if ("Type3".equals(subtype)) {
            List<?> matrix = PdfObjects.array(file.resolve(font.get("FontMatrix")));
            if (matrix != null && !matrix.isEmpty()) {
                fontScale = PdfObjects.number(file.resolve(matrix.getFirst()), 0.001);
            }
        }
        scale = fontScale;

        if (composite) {
            Object encoding = file.resolve(font.get("Encoding"));
            String encodingName = PdfObjects.name(encoding);
            CMap own = null;
            if (encoding instanceof Stream stream) {
                try {
                    byte[] bytes = file.decoded(stream);
                    own = bytes == null ? null : CMap.parse(bytes);
                } catch (PdfException _) {
                    own = null;
                }
            }
            encodingCMap = own;
            unicodeCodes = encodingName != null && encodingName.matches("Uni.*-(UCS2|UTF16)-[HV]");
            boolean identity = encodingName == null || encodingName.startsWith("Identity-");
            unreadable = toUnicode == null && !unicodeCodes || !identity && !unicodeCodes && own == null;
            simpleEncoding = null;
            double dw = descendant == null ? 1000 : PdfObjects.number(file.resolve(descendant.get("DW")), 1000);
            defaultWidth = dw * 0.001;
            List<?> w = descendant == null ? null : PdfObjects.array(file.resolve(descendant.get("W")));
            if (w != null) {
                compositeWidths(w, file);
            }
        } else {
            encodingCMap = null;
            unicodeCodes = false;
            unreadable = false;
            simpleEncoding = simpleEncoding(font, descriptor, file, subtype, flags);
            int first = PdfObjects.integer(file.resolve(font.get("FirstChar")), 0);
            List<?> w = PdfObjects.array(file.resolve(font.get("Widths")));
            if (w != null) {
                for (int i = 0; i < w.size(); i++) {
                    widths.put(first + i, PdfObjects.number(file.resolve(w.get(i)), 0) * fontScale);
                }
            }
            double missing = descriptor == null ? 0 : PdfObjects.number(file.resolve(descriptor.get("MissingWidth")), 0);
            boolean monospaced = (flags & 1) != 0 || name.toLowerCase(Locale.ROOT).contains("courier");
            defaultWidth = missing > 0 ? missing * fontScale : monospaced ? 0.6 : 0.5;
            if (w == null) {
                // a standard font, its widths not given: those of its metrics
                for (int code = 0; code < 256; code++) {
                    String characters = toUnicode == null ? null : toUnicode.characters(1, code);
                    int width = StandardWidths.of(family, bold, italic,
                            characters != null ? characters : simpleEncoding[code]);
                    if (width > 0) {
                        widths.put(code, width * 0.001);
                    }
                }
            }
        }
    }

    static PdfFont of(Map<String, Object> font, PdfFile file) {
        return new PdfFont(font, file);
    }

    private void compositeWidths(List<?> w, PdfFile file) {
        int i = 0;
        int counted = 0;
        while (i < w.size() && counted < 1 << 20) {
            Object first = file.resolve(w.get(i));
            if (!(first instanceof Number start) || i + 1 >= w.size()) {
                break;
            }
            Object next = file.resolve(w.get(i + 1));
            if (next instanceof List<?> list) {
                for (int k = 0; k < list.size(); k++) {
                    widths.put(start.intValue() + k, PdfObjects.number(file.resolve(list.get(k)), 0) * 0.001);
                }
                counted += list.size();
                i += 2;
            } else if (next instanceof Number end && i + 2 < w.size()) {
                double width = PdfObjects.number(file.resolve(w.get(i + 2)), 0) * 0.001;
                long last = Math.min(end.longValue(), start.longValue() + (1 << 16));
                for (long cid = start.longValue(); cid <= last; cid++) {
                    widths.put((int) cid, width);
                }
                counted += (int) (last - start.longValue() + 1);
                i += 3;
            } else {
                break;
            }
        }
    }

    private static String[] simpleEncoding(Map<String, Object> font, Map<String, Object> descriptor, PdfFile file,
                                           String subtype, int flags) {
        Object encoding = file.resolve(font.get("Encoding"));
        String base = PdfObjects.name(encoding);
        Map<String, Object> differences = null;
        if (encoding instanceof Map<?, ?>) {
            differences = PdfObjects.dictionary(encoding);
            base = PdfObjects.name(file.resolve(differences.get("BaseEncoding")));
        }
        String[] table;
        if (base != null) {
            table = Encodings.base(base);
        } else if ("TrueType".equals(subtype)) {
            table = Encodings.base("WinAnsiEncoding");   // what TrueType fonts without one are written with
        } else {
            table = type1Encoding(descriptor, file);
            if (table == null) {
                table = Encodings.base("StandardEncoding");
            }
        }
        if (differences != null) {
            List<?> list = PdfObjects.array(file.resolve(differences.get("Differences")));
            if (list != null) {
                int code = 0;
                for (Object item : list) {
                    Object value = file.resolve(item);
                    if (value instanceof Number number) {
                        code = number.intValue();
                    } else if (value instanceof PdfObjects.Name glyph) {
                        if (code >= 0 && code < 256) {
                            String character = Encodings.glyph(glyph.value());
                            table[code] = character == null ? Encodings.UNKNOWN : character;
                        }
                        code++;
                    }
                }
            }
        }
        return table;
    }

    /** An embedded Type 1 font's own encoding, from the clear text before its program ("dup 65 /A put"). */
    private static String[] type1Encoding(Map<String, Object> descriptor, PdfFile file) {
        if (descriptor == null || !(file.resolve(descriptor.get("FontFile")) instanceof Stream stream)) {
            return null;
        }
        try {
            byte[] bytes = file.decoded(stream);
            if (bytes == null) {
                return null;
            }
            int clear = PdfObjects.integer(file.resolve(stream.dictionary().get("Length1")), bytes.length);
            String text = new String(bytes, 0, Math.min(Math.max(0, clear), bytes.length),
                    StandardCharsets.ISO_8859_1);
            if (text.contains("/Encoding StandardEncoding")) {
                return null;
            }
            Matcher matcher = TYPE1_ENCODING.matcher(text);
            String[] table = null;
            while (matcher.find()) {
                int code = Integer.parseInt(matcher.group(1));
                if (code < 256) {
                    if (table == null) {
                        table = new String[256];
                    }
                    String character = Encodings.glyph(matcher.group(2));
                    table[code] = character == null ? Encodings.UNKNOWN : character;
                }
            }
            return table;
        } catch (PdfException | NumberFormatException _) {
            return null;
        }
    }

    /** The codes of a string's bytes. */
    List<Code> codes(byte[] bytes) {
        List<Code> codes = new ArrayList<>(bytes.length);
        int at = 0;
        while (at < bytes.length) {
            if (!composite) {
                int code = bytes[at++] & 0xFF;
                String characters = toUnicode == null ? null : toUnicode.characters(1, code);
                if (characters == null) {
                    characters = simpleEncoding[code];
                }
                codes.add(new Code(characters, widths.getOrDefault(code, defaultWidth), code == 32));
                continue;
            }
            // Identity and UCS-2: two bytes a code, whatever a ToUnicode's code space says
            int length = encodingCMap != null && encodingCMap.hasCodeSpace() ? encodingCMap.codeLength(bytes, at) : 2;
            length = Math.min(length, bytes.length - at);
            long code = 0;
            for (int i = 0; i < length; i++) {
                code = (code << 8) | (bytes[at + i] & 0xFF);
            }
            at += length;
            int cid = encodingCMap == null ? (int) code : encodingCMap.cid(length, code);
            String characters = toUnicode == null ? null : toUnicode.characters(length, code);
            if (characters == null && unicodeCodes) {
                characters = String.valueOf((char) code);
            }
            codes.add(new Code(characters, widths.getOrDefault(cid, defaultWidth), false));
        }
        return codes;
    }

    /** The family a PostScript name says, its style left out: "TimesNewRomanPS-BoldMT" → "TimesNewRoman". */
    static String[] familyAndStyle(String name) {
        String family = name;
        String style = "";
        int comma = name.indexOf(',');
        int dash = name.indexOf('-');
        int cut = comma >= 0 ? comma : dash;
        if (cut > 0) {
            family = name.substring(0, cut);
            style = name.substring(cut + 1);
        }
        family = family.replaceAll("(PSMT|PS|MT)$", "");
        Matcher matcher = STYLE_WORDS.matcher(family);
        while (family.contains(" ") && matcher.find() && matcher.start() > 0) {
            style = matcher.group(1) + style;
            family = family.substring(0, matcher.start()).strip();
            matcher = STYLE_WORDS.matcher(family);
        }
        return new String[] {family, style.replaceAll("(PSMT|PS|MT)$", "")};
    }

    /** A family written without spaces, spaced at its words: "LiberationSerif" → "Liberation Serif". */
    static String spaced(String family) {
        if (family.contains(" ")) {
            return family;
        }
        return family.replaceAll("(?<=[a-z])(?=[A-Z])", " ");
    }

    /** A text string's characters: UTF-16 with its mark, UTF-8 with its own, or else PDFDocEncoding (Latin-1). */
    static String text(byte[] bytes) {
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    double scale() {
        return scale;
    }
}
