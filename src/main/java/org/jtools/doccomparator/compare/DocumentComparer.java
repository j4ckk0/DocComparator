package org.jtools.doccomparator.compare;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jtools.doccomparator.document.Block;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.Story;
import org.jtools.doccomparator.document.Style;
import org.jtools.doccomparator.document.StyleSheet;
import org.jtools.doccomparator.document.WordDocument;

/**
 * Compares two Word documents: their paragraphs paired (Pairer), the pairs compared (ParagraphComparer), then laid out
 * in rows for a side-by-side view, kind of story by kind of story; and the documents themselves.
 */
public final class DocumentComparer {

    private final WordDocument left;
    private final WordDocument right;
    private final CompareOptions options;
    private final Vocabulary vocabulary = new Vocabulary();
    private final ParagraphComparer paragraphs;

    private DocumentComparer(WordDocument left, WordDocument right, CompareOptions options) {
        this.left = left;
        this.right = right;
        this.options = options;
        this.paragraphs = new ParagraphComparer(options, vocabulary, left.styles(), right.styles());
    }

    public static Comparison compare(WordDocument left, WordDocument right, CompareOptions options) {
        return new DocumentComparer(left, right, options).compare();
    }

    private Comparison compare() {
        List<StoryComparison> stories = new ArrayList<>();
        stories.add(compare("Body", List.of(left.body()), List.of(right.body())));
        if (options.headersAndFooters()) {
            stories.add(compare("Headers and footers", left.headersAndFooters(), right.headersAndFooters()));
        }
        stories.add(compare("Footnotes and endnotes", left.notes(), right.notes()));
        return new Comparison(left, right, options, stories, documentDifferences());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // The stories

    private StoryComparison compare(String title, List<Story> leftStories, List<Story> rightStories) {
        List<Unit> l = units(leftStories);
        List<Unit> r = units(rightStories);
        List<Pairer.Match> matches = Pairer.pair(l, r);

        Map<Integer, Pairer.Match> movedFromLeft = new TreeMap<>();
        Map<Integer, Pairer.Match> movedFromRight = new TreeMap<>();
        Map<Pairer.Match, ParagraphDiff> movedDiffs = new HashMap<>();
        List<Pairer.Match> inOrder = new ArrayList<>();
        for (Pairer.Match match : matches) {
            if (match.moved()) {
                movedFromLeft.put(match.left(), match);
                movedFromRight.put(match.right(), match);
                movedDiffs.put(match, paragraphs.compare(l.get(match.left()), r.get(match.right())));
            } else {
                inOrder.add(match);
            }
        }
        inOrder.add(new Pairer.Match(l.size(), r.size(), 0, false));   // past the end: what is left of both sides

        List<Row> rows = new ArrayList<>();
        int[] leftRows = new int[l.size()];
        int[] rightRows = new int[r.size()];
        int i = 0;
        int j = 0;
        for (Pairer.Match match : inOrder) {
            for (; i < match.left(); i++) {
                leftRows[i] = rows.size();
                Pairer.Match moved = movedFromLeft.get(i);
                rows.add(moved == null
                        ? new Row(l.get(i).item(), null, Row.Status.LEFT_ONLY, -1,
                                ParagraphDiff.alone(l.get(i).text(), true))
                        : new Row(l.get(i).item(), null, Row.statusOf(movedDiffs.get(moved)), -1, movedDiffs.get(moved)));
            }
            for (; j < match.right(); j++) {
                rightRows[j] = rows.size();
                Pairer.Match moved = movedFromRight.get(j);
                rows.add(moved == null
                        ? new Row(null, r.get(j).item(), Row.Status.RIGHT_ONLY, -1,
                                ParagraphDiff.alone(r.get(j).text(), false))
                        : new Row(null, r.get(j).item(), Row.statusOf(movedDiffs.get(moved)), -1, movedDiffs.get(moved)));
            }
            if (i < l.size() && j < r.size()) {
                ParagraphDiff diff = paragraphs.compare(l.get(i), r.get(j));
                rows.add(new Row(l.get(i).item(), r.get(j).item(), Row.statusOf(diff), -1, diff));
                i++;
                j++;
            }
        }
        for (Pairer.Match moved : movedFromLeft.values()) {   // each row of a moved pair names the other's
            int leftRow = leftRows[moved.left()];
            int rightRow = rightRows[moved.right()];
            Row l0 = rows.get(leftRow);
            Row r0 = rows.get(rightRow);
            rows.set(leftRow, new Row(l0.left(), null, l0.status(), rightRow, l0.diff()));
            rows.set(rightRow, new Row(null, r0.right(), r0.status(), leftRow, r0.diff()));
        }
        return new StoryComparison(title, rows);
    }

    /** The paragraphs to compare; with the blanks left out, empty ones are not. */
    private List<Unit> units(List<Story> stories) {
        List<Unit> units = new ArrayList<>();
        for (Item item : Items.of(stories)) {
            Unit unit = Unit.of(item, options.blanks(), vocabulary);
            if (options.blanks() || !unit.isBlank()) {
                units.add(unit);
            }
        }
        return units;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // The documents themselves

    private List<PropertyDifference> documentDifferences() {
        List<PropertyDifference> differences = new ArrayList<>();
        if (options.template()) {
            add(differences, PropertyDifference.of("attached template", left.template().attached(),
                    right.template().attached()));
            add(differences, PropertyDifference.of("template", left.template().named(), right.template().named()));
        }
        if (options.structure()) {
            add(differences, PropertyDifference.of("sections", String.valueOf(sections(left)),
                    String.valueOf(sections(right))));
        }
        if (options.styles()) {
            styleDifferences(differences, left.styles(), right.styles());
        }
        return differences;
    }

    /** The sections of a body: one, and one more after each paragraph that ends one. */
    private static int sections(WordDocument document) {
        int sections = 1;
        for (Block block : document.body().blocks()) {
            if (block instanceof Paragraph paragraph && paragraph.endsSection()) {
                sections++;
            }
        }
        return sections;
    }

    /** The defaults, then each style by name, as both documents define them. */
    private static void styleDifferences(List<PropertyDifference> differences, StyleSheet a, StyleSheet b) {
        ParagraphComparer.formattingDifferences(differences, "default paragraph ", a.defaultParagraph(),
                b.defaultParagraph());
        ParagraphComparer.formattingDifferences(differences, "default text ", a.defaultRun(), b.defaultRun());
        Map<String, Style> stylesA = byName(a);
        Map<String, Style> stylesB = byName(b);
        TreeSet<String> names = new TreeSet<>(stylesA.keySet());
        names.addAll(stylesB.keySet());
        for (String name : names) {
            Style styleA = stylesA.get(name);
            Style styleB = stylesB.get(name);
            String style = "style \"" + name + "\"";
            if (styleA == null || styleB == null) {
                add(differences, PropertyDifference.of(style, styleA == null ? null : "defined",
                        styleB == null ? null : "defined"));
                continue;
            }
            add(differences, PropertyDifference.of(style + " type", styleA.type(), styleB.type()));
            add(differences, PropertyDifference.of(style + " based on",
                    ParagraphComparer.styleName(a, styleA.basedOn()), ParagraphComparer.styleName(b, styleB.basedOn())));
            ParagraphComparer.formattingDifferences(differences, style + " paragraph ", styleA.paragraph(),
                    styleB.paragraph());
            ParagraphComparer.formattingDifferences(differences, style + " text ", styleA.run(), styleB.run());
        }
    }

    private static Map<String, Style> byName(StyleSheet styles) {
        Map<String, Style> byName = new TreeMap<>();
        for (Style style : styles.styles().values()) {
            byName.putIfAbsent(style.name() == null ? style.id() : style.name(), style);
        }
        return byName;
    }

    private static void add(List<PropertyDifference> differences, PropertyDifference difference) {
        if (difference != null) {
            differences.add(difference);
        }
    }
}
