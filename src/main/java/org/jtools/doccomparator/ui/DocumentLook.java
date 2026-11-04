package org.jtools.doccomparator.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jtools.doccomparator.document.Formatting;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.Run;
import org.jtools.doccomparator.document.StyleSheet;

/**
 * How a paragraph looks in Word, for the document view: each run's font, size, weight, slant, lines, colours; the
 * paragraph's alignment, indents, spacing and shading — as they apply through the styles and its list; a list item's
 * number. Lengths in points.
 */
record DocumentLook(List<StyledRun> runs, Alignment alignment, float left, float right, float firstLine, float before,
                    float after, float lineFactor, float exactLine, boolean atLeast, Color shading, String number,
                    String breakBefore, String breakAfter) {

    static final String PAGE_BREAK = "Page break";
    static final String SECTION_BREAK = "Section break";

    enum Alignment {
        LEFT, CENTER, RIGHT, JUSTIFIED
    }

    /** A run as it looks: [start, end) of the paragraph's text. */
    record StyledRun(int start, int end, String family, float size, boolean bold, boolean italic, boolean underline,
                     boolean strike, Color color, Color highlight, int script) {
    }

    /** Word's size when nothing says one: 10 points. */
    private static final float DEFAULT_SIZE = 10;

    /** Word's highlight colours, by name. */
    private static final Map<String, Color> HIGHLIGHTS = Map.ofEntries(
            Map.entry("yellow", new Color(0xFFFF00)), Map.entry("green", new Color(0x00FF00)),
            Map.entry("cyan", new Color(0x00FFFF)), Map.entry("magenta", new Color(0xFF00FF)),
            Map.entry("blue", new Color(0x0000FF)), Map.entry("red", new Color(0xFF0000)),
            Map.entry("darkBlue", new Color(0x000080)), Map.entry("darkCyan", new Color(0x008080)),
            Map.entry("darkGreen", new Color(0x008000)), Map.entry("darkMagenta", new Color(0x800080)),
            Map.entry("darkRed", new Color(0x800000)), Map.entry("darkYellow", new Color(0x808000)),
            Map.entry("darkGray", new Color(0x808080)), Map.entry("lightGray", new Color(0xC0C0C0)),
            Map.entry("black", Color.BLACK), Map.entry("white", Color.WHITE));

    static DocumentLook of(StyleSheet styles, Paragraph paragraph) {
        Formatting p = styles.effectiveParagraph(paragraph);
        Alignment alignment = switch (p.get("jc") == null ? "" : p.get("jc")) {
            case "center" -> Alignment.CENTER;
            case "right", "end" -> Alignment.RIGHT;
            case "both", "distribute", "justify", "lowKashida", "mediumKashida", "highKashida" -> Alignment.JUSTIFIED;
            default -> Alignment.LEFT;
        };
        float left = points(first(p, "ind.left", "ind.start"));
        float right = points(first(p, "ind.right", "ind.end"));
        float firstLine = points(p.get("ind.firstLine")) - points(p.get("ind.hanging"));
        float lineFactor = 1;
        float exactLine = 0;
        boolean atLeast = false;
        String line = p.get("spacing.line");
        String rule = p.get("spacing.lineRule");
        if (line != null) {
            if (rule == null || rule.equals("auto")) {
                lineFactor = Math.max(0.5f, number(line) / 240);
            } else {
                exactLine = points(line);
                atLeast = rule.equals("atLeast");
            }
        }
        List<StyledRun> runs = new ArrayList<>();
        int offset = 0;
        for (Run run : paragraph.runs()) {
            Formatting r = styles.effectiveRun(paragraph.styleId(), run.styleId(), run.formatting());
            String size = r.get("sz");
            String underline = r.get("u");
            String vertical = r.get("vertAlign");
            Color highlight = r.get("highlight") == null ? null : HIGHLIGHTS.get(r.get("highlight"));
            runs.add(new StyledRun(offset, offset + run.text().length(),
                    first(r, "rFonts.ascii", "rFonts.hAnsi"),
                    size == null ? DEFAULT_SIZE : number(size) / 2,
                    on(r, "b"), on(r, "i"), underline != null && !underline.equals("none"),
                    on(r, "strike") || on(r, "dstrike"), color(r.get("color")),
                    highlight != null ? highlight : color(r.get("shd.fill")),
                    "superscript".equals(vertical) ? 1 : "subscript".equals(vertical) ? -1 : 0));
            offset += run.text().length();
        }
        return new DocumentLook(runs, alignment, left, right, firstLine, points(p.get("spacing.before")),
                points(p.get("spacing.after")), lineFactor, exactLine, atLeast, color(p.get("shd.fill")), null,
                on(p, "pageBreakBefore") ? PAGE_BREAK : null, paragraph.endsSection() ? SECTION_BREAK : null);
    }

    /** The same, its list item's number ("3.", "•") at the start of its first line. */
    DocumentLook withNumber(String text) {
        return new DocumentLook(runs, alignment, left, right, firstLine, before, after, lineFactor, exactLine, atLeast,
                shading, text, breakBefore, breakAfter);
    }

    /** The same, a break before it: one of an empty paragraph not shown (blanks left out), carried over. */
    DocumentLook withBreakBefore(String label) {
        return new DocumentLook(runs, alignment, left, right, firstLine, before, after, lineFactor, exactLine, atLeast,
                shading, number, breakBefore != null ? breakBefore : label, breakAfter);
    }

    /** A cell's shading, if it has one. */
    static Color shading(Formatting cell) {
        return color(cell.get("shd.fill"));
    }

    private static boolean on(Formatting formatting, String name) {
        String value = formatting.get(name);
        return value != null && !value.equals("false");
    }

    private static String first(Formatting formatting, String... names) {
        for (String name : names) {
            if (formatting.get(name) != null) {
                return formatting.get(name);
            }
        }
        return null;
    }

    /** Twentieths of a point, as Word writes lengths, into points. */
    private static float points(String twips) {
        return twips == null ? 0 : number(twips) / 20;
    }

    private static float number(String text) {
        try {
            return Float.parseFloat(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** "1F3864" is a colour; "auto", or anything else, none. */
    private static Color color(String hex) {
        if (hex == null || !hex.matches("[0-9A-Fa-f]{6}")) {
            return null;
        }
        return new Color(Integer.parseInt(hex, 16));
    }
}
