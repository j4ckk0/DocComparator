package org.jtools.doccomparator.compare;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jtools.doccomparator.compare.CompareOptions.Aspect;
import org.jtools.doccomparator.document.Block;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.Story;
import org.jtools.doccomparator.document.Style;
import org.jtools.doccomparator.document.StyleSheet;
import org.jtools.doccomparator.document.WordDocument;

/**
 * Compares two Word documents: their paragraphs and tables paired (Pairer), the pairs compared (ParagraphComparer,
 * TableComparer), then laid out in rows for a side-by-side view, kind of story by kind of story; and the documents
 * themselves.
 */
public final class DocumentComparer {

    private final WordDocument left;
    private final WordDocument right;
    private final CompareOptions options;
    private final Vocabulary vocabulary = new Vocabulary();
    private final ParagraphComparer paragraphs;
    private final TableComparer tables;

    private DocumentComparer(WordDocument left, WordDocument right, CompareOptions options) {
        this.left = left;
        this.right = right;
        this.options = options;
        this.paragraphs = new ParagraphComparer(options, vocabulary, left.styles(), right.styles());
        this.tables = new TableComparer(options, paragraphs, left.styles(), right.styles());
    }

    public static Comparison compare(WordDocument left, WordDocument right, CompareOptions options) {
        return new DocumentComparer(left, right, options).compare();
    }

    private Comparison compare() {
        List<StoryComparison> stories = new ArrayList<>();
        stories.add(compare("Body", List.of(left.body()), List.of(right.body())));
        if (options.has(Aspect.HEADERS_AND_FOOTERS)) {
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
        List<Slot> slots = Slot.align(l.size(), r.size(), Pairer.pair(l, r));
        List<Row> rows = new ArrayList<>();
        for (Slot slot : slots) {
            if (slot.left() >= 0 && slot.right() >= 0) {
                rows.add(pair(l.get(slot.left()), r.get(slot.right()), -1));
            } else if (slot.left() >= 0) {
                Unit unit = l.get(slot.left());
                if (slot.moved()) {
                    Row pair = pair(unit, r.get(slots.get(slot.counterpart()).right()), slot.counterpart());
                    rows.add(new Row(pair.left(), null, pair.status(), pair.counterpart(), pair.diff(), pair.table()));
                } else {
                    rows.add(new Row(unit.item(), null, alone(Row.Status.LEFT_ONLY), -1, ParagraphDiff.alone(
                            unit.isTable() ? "" : unit.text(), true, options.text()),
                            unit.isTable() ? tables.alone(unit, true) : null));
                }
            } else {
                Unit unit = r.get(slot.right());
                if (slot.moved()) {
                    Row pair = pair(l.get(slots.get(slot.counterpart()).left()), unit, slot.counterpart());
                    rows.add(new Row(null, pair.right(), pair.status(), pair.counterpart(), pair.diff(), pair.table()));
                } else {
                    rows.add(new Row(null, unit.item(), alone(Row.Status.RIGHT_ONLY), -1, ParagraphDiff.alone(
                            unit.isTable() ? "" : unit.text(), false, options.text()),
                            unit.isTable() ? tables.alone(unit, false) : null));
                }
            }
        }
        return new StoryComparison(title, rows);
    }

    /** A paragraph or table alone counts as text: added, or removed. */
    private Row.Status alone(Row.Status status) {
        return options.text() ? status : Row.Status.SAME;
    }

    /** Two paragraphs, or two tables, compared; a moved pair otherwise the same is shown moved, when the text counts. */
    private Row pair(Unit a, Unit b, int counterpart) {
        Row.Status status;
        ParagraphDiff diff;
        TableComparison table = null;
        if (a.isTable()) {
            table = tables.compare(a, b);
            diff = tables.properties(a, b);
            status = tables.status(table, diff);
        } else {
            diff = paragraphs.compare(a, b);
            status = Row.statusOf(diff);
        }
        if (counterpart >= 0 && status == Row.Status.SAME && options.text()) {
            status = Row.Status.MOVED;
        }
        return new Row(a.item(), b.item(), status, counterpart, diff, table);
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
        Differences differences = new Differences(options);
        differences.add(Aspect.TEMPLATE, "attached template", left.template().attached(), right.template().attached());
        differences.add(Aspect.TEMPLATE, "template", left.template().named(), right.template().named());
        differences.add(Aspect.SECTIONS, "sections", String.valueOf(sections(left)), String.valueOf(sections(right)));
        if (options.has(Aspect.STYLES)) {
            styleDifferences(differences, left.styles(), right.styles());
        }
        return differences.list();
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

    /** The defaults, then each style by name, as both documents define them: all of it a matter of styles. */
    private static void styleDifferences(Differences differences, StyleSheet a, StyleSheet b) {
        differences.formattingAs(Aspect.STYLES, "default paragraph ", a.defaultParagraph(), b.defaultParagraph());
        differences.formattingAs(Aspect.STYLES, "default text ", a.defaultRun(), b.defaultRun());
        Map<String, Style> stylesA = byName(a);
        Map<String, Style> stylesB = byName(b);
        TreeSet<String> names = new TreeSet<>(stylesA.keySet());
        names.addAll(stylesB.keySet());
        for (String name : names) {
            Style styleA = stylesA.get(name);
            Style styleB = stylesB.get(name);
            String style = "style \"" + name + "\"";
            if (styleA == null || styleB == null) {
                differences.add(Aspect.STYLES, style, styleA == null ? null : "defined", styleB == null ? null : "defined");
                continue;
            }
            differences.add(Aspect.STYLES, style + " type", styleA.type(), styleB.type());
            differences.add(Aspect.STYLES, style + " based on", a.nameOf(styleA.basedOn()),
                    b.nameOf(styleB.basedOn()));
            differences.formattingAs(Aspect.STYLES, style + " paragraph ", styleA.paragraph(), styleB.paragraph());
            differences.formattingAs(Aspect.STYLES, style + " text ", styleA.run(), styleB.run());
        }
    }

    private static Map<String, Style> byName(StyleSheet styles) {
        Map<String, Style> byName = new TreeMap<>();
        for (Style style : styles.styles().values()) {
            byName.putIfAbsent(style.name() == null ? style.id() : style.name(), style);
        }
        return byName;
    }
}
