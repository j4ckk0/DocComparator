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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jtools.doccomparator.document.Block;
import org.jtools.doccomparator.document.Formatting;
import org.jtools.doccomparator.document.ListItem;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.Run;
import org.jtools.doccomparator.pdf.ContentReader.Glyph;
import org.jtools.doccomparator.pdf.PdfLines.Line;

/**
 * A document's lines as paragraphs: lines joined until a gap wider than its line spacing, a change of size or
 * boldness, an indent, a list item's mark or a sentence's end; across a page break while its sentence goes on. Each
 * paragraph's runs, by its glyphs' formatting; its list mark apart, as Word holds one.
 */
final class PdfParagraphs {

    /** A list item's mark, as the start of its text: a bullet, a number or a letter, then a space. */
    private static final Pattern LIST_MARK = Pattern.compile(
            "^(?:([\\u2022\\u25E6\\u25AA\\u25AB\\u2023\\u2043\\u25CF\\u25CB\\u25A0\\u25A1\\u2013\\u00B7*\\-–\\uF0B7\\uF0A7])"
                    + "|(\\(?)([0-9]{1,2}|[a-z]|[A-Z](?=\\)))([.)]))\\s+");
    private static final Pattern SENTENCE_END = Pattern.compile(".*[.!?:;…][\"'”’»)\\]]*\\s*$");

    private PdfParagraphs() {
    }

    /**
     * The document's line spacing, by size (in half points): the smallest spacing between the lines of a size that
     * comes back often, as within a paragraph, between once and nearly twice the size.
     */
    static Map<Long, Double> pitches(List<List<Line>> pages) {
        Map<Long, TreeMap<Long, Integer>> found = new HashMap<>();
        for (List<Line> page : pages) {
            for (int i = 1; i < page.size(); i++) {
                Line previous = page.get(i - 1);
                Line line = page.get(i);
                if (!line.horizontal() || !previous.horizontal() || Math.abs(line.size - previous.size) > 0.01) {
                    continue;
                }
                double pitch = previous.across - line.across;
                if (pitch > 0.9 * line.size && pitch < 3 * line.size) {
                    found.computeIfAbsent(Math.round(line.size * 2), size -> new TreeMap<>())
                            .merge(Math.round(pitch * 4), 1, Integer::sum);
                }
            }
        }
        Map<Long, Double> pitches = new HashMap<>();
        found.forEach((size, counts) -> {
            int total = counts.values().stream().mapToInt(Integer::intValue).sum();
            int often = Math.max(2, total / 20);
            for (Map.Entry<Long, Integer> entry : counts.entrySet()) {
                // spacings a quarter of a point apart are the same, rounded either way
                int near = entry.getValue() + counts.getOrDefault(entry.getKey() + 1, 0);
                if (near >= often) {
                    double pitch = entry.getKey() / 4.0;
                    pitches.put(size, Math.min(pitch, 1.9 * size / 2.0));
                    break;
                }
            }
        });
        return pitches;
    }

    private static double pitch(Map<Long, Double> pitches, double size) {
        Double pitch = pitches.get(Math.round(size * 2));
        return pitch == null ? 1.25 * size : Math.max(pitch, size);
    }

    /** A paragraph being made: its lines, and where pages began in its text. */
    private static final class Building {
        final List<Line> lines = new ArrayList<>();
        /** The lines that begin a page, once for each page begun there (a blank page before it included). */
        final List<Integer> pageLines = new ArrayList<>();
        double right;

        Line last() {
            return lines.getLast();
        }
    }

    static List<Block> of(List<List<Line>> pages, Map<Long, Double> pitches, Set<String> words, boolean paged) {
        List<Building> built = new ArrayList<>();
        int[] lists = {0};
        int blankPages = 0;
        for (int p = 0; p < pages.size(); p++) {
            List<Line> page = pages.get(p);
            if (page.isEmpty()) {
                blankPages += paged && p > 0 ? 1 : 0;
                continue;
            }
            for (int i = 0; i < page.size(); i++) {
                Line line = page.get(i);
                Building current = built.isEmpty() ? null : built.getLast();
                boolean pageBegins = paged && i == 0 && p > 0;
                boolean joined = current != null && (pageBegins ? continuesOnNextPage(current, line)
                        : sameParagraph(current, line, pitches));
                if (joined) {
                    if (pageBegins) {
                        for (int k = 0; k <= blankPages; k++) {
                            current.pageLines.add(current.lines.size());
                        }
                        blankPages = 0;
                    }
                    current.lines.add(line);
                    current.right = Math.max(current.right, line.end);
                } else {
                    Building paragraph = new Building();
                    paragraph.lines.add(line);
                    paragraph.right = line.end;
                    if (pageBegins) {
                        for (int k = 0; k <= blankPages; k++) {
                            paragraph.pageLines.add(0);
                        }
                        blankPages = 0;
                    }
                    built.add(paragraph);
                }
            }
        }
        List<Block> paragraphs = new ArrayList<>(built.size());
        for (Building paragraph : built) {
            paragraphs.add(listed(paragraph(paragraph, words), lists));
        }
        return paragraphs;
    }

    private static boolean sameParagraph(Building current, Line line, Map<Long, Double> pitches) {
        Line previous = current.last();
        if (previous.page != line.page || !compatible(previous, line) || startsItem(current, line)) {
            return false;
        }
        double pitch = previous.across - line.across;
        double expected = pitch(pitches, previous.size);
        if (pitch <= 0.5 * line.size || pitch > expected + Math.max(0.25 * line.size, 0.15 * expected)) {
            return false;   // above (another column, a table's next cell) or after a wider gap than the lines'
        }
        if (line.start > previous.start + line.size && current.lines.size() > 1) {
            return false;   // its first line indented
        }
        return !(current.lines.size() > 1 && SENTENCE_END.matcher(previous.text()).matches()
                && previous.end < current.right - 3 * line.size);
    }

    private static boolean continuesOnNextPage(Building current, Line line) {
        Line previous = current.last();
        return compatible(previous, line) && !SENTENCE_END.matcher(previous.text()).matches()
                && !startsItem(current, line);
    }

    /**
     * Whether a line starts a list item: its mark first — a dash only after a sentence's end or another item, as a
     * line may begin with a dash within a sentence.
     */
    private static boolean startsItem(Building current, Line line) {
        Matcher mark = LIST_MARK.matcher(line.text());
        if (!mark.find()) {
            return false;
        }
        boolean dash = mark.group(1) != null && "-–*".contains(mark.group(1));
        return !dash || SENTENCE_END.matcher(current.last().text()).matches()
                || LIST_MARK.matcher(current.lines.getFirst().text()).find();
    }

    private static boolean compatible(Line previous, Line line) {
        return Math.abs(previous.dx - line.dx) < 0.05 && Math.abs(previous.dy - line.dy) < 0.05
                && Math.abs(previous.size - line.size) <= 0.15 * Math.max(previous.size, line.size)
                && previous.allBold() == line.allBold() && !previous.tabular && !line.tabular;
    }

    // ---- paragraphs into runs -----------------------------------------------------------------------------------

    private static Paragraph paragraph(Building paragraph, Set<String> words) {
        List<Run> runs = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        Formatting formatting = null;
        int length = 0;
        List<Integer> pageStarts = new ArrayList<>();
        boolean[] spaced = new boolean[paragraph.lines.size()];
        for (int l = 1; l < spaced.length; l++) {
            // before any line is written: a hyphen dropped is the end of the line before
            Line previous = paragraph.lines.get(l - 1);
            Join join = join(previous, paragraph.lines.get(l), words);
            if (join == Join.HYPHEN_DROPPED) {
                previous.glyphs.removeLast();
            }
            spaced[l] = join == Join.SPACE;
        }
        for (int l = 0; l < paragraph.lines.size(); l++) {
            Line line = paragraph.lines.get(l);
            double baseline = baseline(line);
            if (spaced[l]) {
                text.append(' ');
                length++;
            }
            for (int begun : paragraph.pageLines) {
                if (begun == l) {
                    pageStarts.add(length);
                }
            }
            for (Glyph glyph : line.glyphs) {
                Formatting own = formatting(glyph, line, baseline);
                if (formatting != null && !own.equals(formatting) && !glyph.text().isBlank()) {
                    runs.add(new Run(text.toString(), null, formatting));
                    text.setLength(0);
                }
                if (formatting == null || !glyph.text().isBlank()) {
                    formatting = own;
                }
                text.append(glyph.text());
                length += glyph.text().length();
            }
        }
        if (!text.isEmpty()) {
            runs.add(new Run(text.toString(), null, formatting == null ? Formatting.NONE : formatting));
        }
        return new Paragraph(null, Formatting.NONE, 0, null, false, runs, pageStarts);
    }

    /**
     * A paragraph that starts with a list item's mark, as Word holds one: the mark apart from its text, as a list
     * item's, numbered the same way ("%1.", "(%1)"); a list begins again at its first number, 1, a or A.
     */
    private static Paragraph listed(Paragraph paragraph, int[] lists) {
        Matcher mark = LIST_MARK.matcher(paragraph.text());
        if (!mark.find() || mark.end() >= paragraph.text().length()) {
            return paragraph;
        }
        ListItem item;
        if (mark.group(1) != null) {
            item = new ListItem("pdf-bullets", 0, "bullet", mark.group(1));
        } else {
            String number = mark.group(3);
            String format = Character.isDigit(number.charAt(0)) ? "decimal"
                    : Character.isLowerCase(number.charAt(0)) ? "lowerLetter" : "upperLetter";
            if (number.equals("1") || number.equals("a") || number.equals("A") || lists[0] == 0) {
                lists[0]++;
            }
            String open = mark.group(2);
            item = new ListItem("pdf-" + lists[0], 0, format, open + "%1" + mark.group(4));
        }
        int cut = mark.end();
        List<Run> runs = new ArrayList<>();
        int at = 0;
        for (Run run : paragraph.runs()) {
            int end = at + run.text().length();
            if (end > cut) {
                runs.add(at >= cut ? run : new Run(run.text().substring(cut - at), run.styleId(), run.formatting()));
            }
            at = end;
        }
        List<Integer> pageStarts = paragraph.pageStarts().stream().map(offset -> Math.max(0, offset - cut)).toList();
        return new Paragraph(null, Formatting.NONE, 0, item, false, runs, pageStarts);
    }

    /**
     * The words of the document, in lower case, as written within its lines: those a line's end cuts are known by
     * them.
     */
    static Set<String> words(List<List<Line>> pages) {
        Set<String> words = new HashSet<>();
        for (List<Line> page : pages) {
            for (Line line : page) {
                String[] tokens = line.text().split("\\s+");
                for (int i = 0; i < tokens.length; i++) {
                    if (i < tokens.length - 1 || !tokens[i].endsWith("-")) {
                        words.add(bare(tokens[i]));
                    }
                }
            }
        }
        return words;
    }

    /** A word without the punctuation around it, in lower case. */
    private static String bare(String token) {
        return token.replaceAll("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$", "").toLowerCase(Locale.ROOT);
    }

    /** How a line is joined to the one before it. */
    enum Join {
        /** By a space. */
        SPACE,
        /** By nothing: after a hyphen kept ("well-known"). */
        NOTHING,
        /** By nothing, the line before losing its last glyph: a soft hyphen, or a hyphen cutting a word. */
        HYPHEN_DROPPED
    }

    /**
     * How a line is joined to the one before it: by a space; by nothing after a soft hyphen (dropped) or a hyphen
     * that cuts a word — dropped too when the document writes the word whole elsewhere, kept otherwise ("well-known").
     */
    static Join join(Line previous, Line line, Set<String> words) {
        String before = previous.text();
        if (before.endsWith("\u00AD")) {
            return Join.HYPHEN_DROPPED;
        }
        if (!(before.length() > 1 && before.endsWith("-") && Character.isLetter(before.charAt(before.length() - 2)))) {
            return Join.SPACE;
        }
        String[] head = before.split("\\s+");
        String[] tail = line.text().split("\\s+", 2);
        String start = head[head.length - 1];
        String whole = bare(start.substring(0, start.length() - 1) + tail[0]);
        boolean cut = !tail[0].isEmpty() && Character.isLowerCase(tail[0].charAt(0)) && words.contains(whole)
                && !words.contains(bare(start + tail[0]));
        return cut ? Join.HYPHEN_DROPPED : Join.NOTHING;
    }

    private static double baseline(Line line) {
        for (Glyph glyph : line.glyphs) {
            if (Math.abs(glyph.size() - line.size) < 0.26 && !glyph.text().isBlank()) {
                return -glyph.x() * glyph.dy() + glyph.y() * glyph.dx();
            }
        }
        return line.across;
    }

    private static Formatting formatting(Glyph glyph, Line line, double baseline) {
        Map<String, String> values = new HashMap<>();
        if (!glyph.font().family.isEmpty()) {
            values.put("rFonts.ascii", glyph.font().family);
        }
        values.put("sz", String.valueOf(Math.round(glyph.size() * 2)));
        if (PdfLines.bold(glyph)) {
            values.put("b", "true");
        }
        if (glyph.font().italic || glyph.slanted()) {
            values.put("i", "true");
        }
        if (glyph.color() != null) {
            values.put("color", glyph.color());
        }
        double offset = -glyph.x() * glyph.dy() + glyph.y() * glyph.dx() - baseline;
        if (glyph.size() < 0.85 * line.size) {
            if (offset > 0.15 * line.size) {
                values.put("vertAlign", "superscript");
            } else if (offset < -0.08 * line.size) {
                values.put("vertAlign", "subscript");
            }
        }
        return new Formatting(values);
    }
}
