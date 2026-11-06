package org.jtools.doccomparator.xlsx;


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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.jtools.doccomparator.ooxml.OoxmlException;
import org.jtools.doccomparator.ooxml.OoxmlPackage;
import org.jtools.doccomparator.ooxml.XmlCursor;
import org.jtools.doccomparator.document.WordProperties;

/**
 * A workbook's cell styles (xl/styles.xml) as Word's properties, so that a cell's look compares and shows as a table
 * cell's does: its font as a run's, its fill as shading, its borders and alignment; what Word has no name for, its
 * number format and text wrapped, under names of their own (numFmt, wrapText).
 */
final class Styles {

    /** A cell style: the code of its number format; its properties for the cell's runs, the cell, its paragraph. */
    record CellStyle(String numberFormat, Map<String, String> run, Map<String, String> cell,
                     Map<String, String> paragraph) {
    }

    private static final CellStyle PLAIN = new CellStyle(NumberFormats.GENERAL, Map.of(), Map.of(), Map.of());

    static final Styles NONE = new Styles(List.of());

    /** Excel's border styles as Word's: its value, and its width in eighths of a point. */
    private static final Map<String, String[]> BORDERS = Map.ofEntries(
            Map.entry("hair", new String[] {"single", "2"}), Map.entry("thin", new String[] {"single", "4"}),
            Map.entry("medium", new String[] {"single", "12"}), Map.entry("thick", new String[] {"single", "18"}),
            Map.entry("double", new String[] {"double", "4"}), Map.entry("dotted", new String[] {"dotted", "4"}),
            Map.entry("dashed", new String[] {"dashed", "4"}), Map.entry("mediumDashed", new String[] {"dashed", "12"}),
            Map.entry("dashDot", new String[] {"dotDash", "4"}),
            Map.entry("mediumDashDot", new String[] {"dotDash", "12"}),
            Map.entry("dashDotDot", new String[] {"dotDotDash", "4"}),
            Map.entry("mediumDashDotDot", new String[] {"dotDotDash", "12"}),
            Map.entry("slantDashDot", new String[] {"dotDash", "12"}));

    private static final Map<String, String> HORIZONTAL = Map.of("left", "left", "center", "center", "right",
            "right", "justify", "both", "distributed", "distribute", "centerContinuous", "center", "fill", "left");

    /** Excel's vertical alignments as Word's; bottom, Excel's default that some writers spell out, as none. */
    private static final Map<String, String> VERTICAL = Map.of("top", "top", "center", "center", "justify", "both",
            "distributed", "both");

    private final List<CellStyle> styles;

    private Styles(List<CellStyle> styles) {
        this.styles = styles;
    }

    /** The style of this index (a cell's s); the first, Excel's normal one, for an index the workbook lacks. */
    CellStyle of(int index) {
        if (index >= 0 && index < styles.size()) {
            return styles.get(index);
        }
        return styles.isEmpty() ? PLAIN : styles.getFirst();
    }

    /** The styles part, read twice: its palette (written last) first, the styles then. */
    static Styles read(OoxmlPackage xlsx, String part, Colors themed) throws OoxmlException {
        List<String> palette = new ArrayList<>();
        try (XmlCursor xml = xlsx.open(part)) {
            xml.root("styleSheet");
            while (xml.nextChild()) {
                if (xml.isSpreadsheet("colors")) {
                    while (xml.nextChild()) {
                        if (xml.isSpreadsheet("indexedColors")) {
                            while (xml.nextChild()) {
                                String rgb = xml.plainAttribute("rgb");
                                if (rgb != null) {
                                    palette.add(rgb.length() == 8 ? rgb.substring(2).toUpperCase(Locale.ROOT)
                                            : rgb.toUpperCase(Locale.ROOT));
                                }
                                xml.skip();
                            }
                        } else {
                            xml.skip();
                        }
                    }
                } else {
                    xml.skip();
                }
            }
        }
        Colors colors = themed.withPalette(palette);
        Map<Integer, String> formats = new HashMap<>();
        List<Map<String, String>> fonts = new ArrayList<>();
        List<Map<String, String>> fills = new ArrayList<>();
        List<Map<String, String>> borders = new ArrayList<>();
        List<CellStyle> styles = new ArrayList<>();
        try (XmlCursor xml = xlsx.open(part)) {
            xml.root("styleSheet");
            while (xml.nextChild()) {
                if (xml.isSpreadsheet("numFmts")) {
                    xml.forChildren(format -> {
                        Integer id = number(format.plainAttribute("numFmtId"));
                        String code = format.plainAttribute("formatCode");
                        if (id != null && code != null) {
                            formats.put(id, code);
                        }
                        format.skip();
                    });
                } else if (xml.isSpreadsheet("fonts")) {
                    xml.forChildren(font -> fonts.add(font(font, colors)));
                } else if (xml.isSpreadsheet("fills")) {
                    xml.forChildren(fill -> fills.add(fill(fill, colors)));
                } else if (xml.isSpreadsheet("borders")) {
                    xml.forChildren(border -> borders.add(border(border, colors)));
                } else if (xml.isSpreadsheet("cellXfs")) {
                    xml.forChildren(xf -> styles.add(style(xf, formats, fonts, fills, borders)));
                } else {
                    xml.skip();
                }
            }
        }
        return new Styles(styles);
    }

    /** A font (a fonts' font, a rich text run's rPr) as a run's properties, consumed. */
    static SortedMap<String, String> font(XmlCursor xml, Colors colors) throws OoxmlException {
        SortedMap<String, String> run = new TreeMap<>();
        while (xml.nextChild()) {
            String value = xml.plainAttribute("val");
            switch (xml.localName()) {
                case "b", "i", "strike" -> {
                    if (on(value)) {
                        run.put(xml.localName(), "true");
                    }
                }
                case "u" -> {
                    String underline = value == null ? "single" : value;
                    if (!underline.equals("none")) {
                        run.put(WordProperties.UNDERLINE, underline.startsWith("double") ? "double" : "single");
                    }
                }
                case "sz" -> {
                    if (value != null) {
                        try {
                            // points → half-points
                            run.put(WordProperties.SIZE, String.valueOf(Math.round(Double.parseDouble(value) * 2)));
                        } catch (NumberFormatException _) {
                            run.put(WordProperties.SIZE, value);
                        }
                    }
                }
                case "name", "rFont" -> {
                    if (value != null) {
                        run.put(WordProperties.FONT, value);
                    }
                }
                case "color" -> {
                    String colour = colors.of(xml);
                    if (colour != null) {
                        run.put(WordProperties.COLOR, colour);
                    }
                }
                case "vertAlign" -> {
                    if (value != null && !value.equals("baseline")) {
                        run.put(WordProperties.VERTICAL_ALIGNMENT, value);
                    }
                }
                default -> {
                    // family, charset, scheme, outline, shadow, condense, extend: how a font is found, not shown
                }
            }
            xml.skip();
        }
        return run;
    }

    private static boolean on(String value) {
        return value == null || value.equals("1") || value.equals("true");
    }

    /** A fill as shading: solid, its colour; another pattern, its name and colours; none, nothing. */
    private static Map<String, String> fill(XmlCursor xml, Colors colors) throws OoxmlException {
        Map<String, String> cell = new TreeMap<>();
        while (xml.nextChild()) {
            if (xml.isSpreadsheet("patternFill")) {
                String pattern = xml.plainAttribute("patternType");
                String foreground = null;
                String background = null;
                while (xml.nextChild()) {
                    if (xml.isSpreadsheet("fgColor")) {
                        foreground = colors.of(xml);
                    } else if (xml.isSpreadsheet("bgColor")) {
                        background = colors.of(xml);
                    }
                    xml.skip();
                }
                if ("solid".equals(pattern)) {
                    cell.put(WordProperties.SHADING_PATTERN, "clear");
                    cell.put(WordProperties.SHADING_COLOR, "auto");
                    cell.put(WordProperties.SHADING_FILL, foreground == null ? "auto" : foreground);
                } else if (pattern != null && !pattern.equals("none") && !pattern.equals("gray125")) {
                    cell.put(WordProperties.SHADING_PATTERN, pattern);
                    cell.put(WordProperties.SHADING_COLOR, foreground == null ? "auto" : foreground);
                    cell.put(WordProperties.SHADING_FILL, background == null ? "auto" : background);
                }
            } else if (xml.isSpreadsheet("gradientFill")) {
                cell.put(WordProperties.SHADING_PATTERN, "gradient");
                xml.skip();
            } else {
                xml.skip();
            }
        }
        return cell;
    }

    /** A border's sides as tcBorders: Word's style, width and colour for each one drawn. */
    private static Map<String, String> border(XmlCursor xml, Colors colors) throws OoxmlException {
        Map<String, String> cell = new TreeMap<>();
        while (xml.nextChild()) {
            String side = switch (xml.localName()) {
                case "left", "start" -> "left";
                case "right", "end" -> "right";
                case "top" -> "top";
                case "bottom" -> "bottom";
                default -> null;   // diagonal, vertical, horizontal: not a cell's own side
            };
            String[] style = BORDERS.get(String.valueOf(xml.plainAttribute("style")));
            String colour = null;
            while (xml.nextChild()) {
                if (xml.isSpreadsheet("color")) {
                    colour = colors.of(xml);
                }
                xml.skip();
            }
            if (side != null && style != null) {
                cell.put(WordProperties.cellBorder(side, "val"), style[0]);
                cell.put(WordProperties.cellBorder(side, "sz"), style[1]);
                cell.put(WordProperties.cellBorder(side, "color"), colour == null ? "auto" : colour);
            }
        }
        return cell;
    }

    /** A cell style (cellXfs' xf): its format, font, fill and border by index, its alignment; consumed. */
    private static CellStyle style(XmlCursor xml, Map<Integer, String> formats, List<Map<String, String>> fonts,
            List<Map<String, String>> fills, List<Map<String, String>> borders) throws OoxmlException {
        Integer formatId = number(xml.plainAttribute("numFmtId"));
        String code = formatId == null ? NumberFormats.GENERAL
                : formats.getOrDefault(formatId, NumberFormats.builtIn(formatId));
        Map<String, String> run = new TreeMap<>(at(fonts, xml.plainAttribute("fontId")));
        Map<String, String> cell = new TreeMap<>(at(fills, xml.plainAttribute("fillId")));
        cell.putAll(at(borders, xml.plainAttribute("borderId")));
        if (!code.equals(NumberFormats.GENERAL)) {
            cell.put(WordProperties.NUMBER_FORMAT, code);
        }
        Map<String, String> paragraph = new TreeMap<>();
        while (xml.nextChild()) {
            if (xml.isSpreadsheet("alignment")) {
                String horizontal = HORIZONTAL.get(String.valueOf(xml.plainAttribute("horizontal")));
                if (horizontal != null) {
                    paragraph.put(WordProperties.ALIGNMENT, horizontal);
                }
                String vertical = VERTICAL.get(String.valueOf(xml.plainAttribute("vertical")));
                if (vertical != null) {
                    cell.put(WordProperties.CELL_VERTICAL_ALIGNMENT, vertical);
                }
                if (on(xml.plainAttribute("wrapText")) && xml.plainAttribute("wrapText") != null) {
                    cell.put(WordProperties.WRAP_TEXT, "true");
                }
            }
            xml.skip();
        }
        return new CellStyle(code, Map.copyOf(run), Map.copyOf(cell), Map.copyOf(paragraph));
    }

    private static Map<String, String> at(List<Map<String, String>> list, String index) {
        Integer i = number(index);
        return i != null && i >= 0 && i < list.size() ? list.get(i) : Map.of();
    }

    static Integer number(String text) {
        try {
            return text == null ? null : Integer.valueOf(text.trim());
        } catch (NumberFormatException _) {
            return null;
        }
    }
}
