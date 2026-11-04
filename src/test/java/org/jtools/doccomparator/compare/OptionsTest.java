package org.jtools.doccomparator.compare;

import static org.jtools.doccomparator.compare.ParagraphComparerTest.marked;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jtools.doccomparator.compare.CompareOptions.Aspect;
import org.jtools.doccomparator.document.WordDocument;
import org.jtools.doccomparator.docx.DocxReader;
import org.junit.jupiter.api.Test;

/** Each kind of difference on the samples: shown with its switch on, gone with it off. */
class OptionsTest {

    private static final WordDocument REVISION_A = read("procedure-revA.docx");
    private static final WordDocument REVISION_B = read("procedure-revB.docx");

    @Test
    void byDefaultTheTextAloneCounts() {
        List<PropertyDifference> all = everyDifference(CompareOptions.DEFAULT);
        assertEquals(List.of(), all, "no layout by default");
        Comparison comparison = DocumentComparer.compare(REVISION_A, REVISION_B, CompareOptions.DEFAULT);
        assertEquals("Headers and footers", comparison.stories().get(1).title());
        assertEquals(Row.Status.SAME, row(comparison, "Records").status(), "a heading's level is layout");
    }

    @Test
    void eachKindIsFoundWithItsSwitchOnlyAndNeverAsAnother() {
        assertFound(Aspect.HEADINGS, "heading");
        assertFound(Aspect.STYLES, "paragraph style");
        assertFound(Aspect.CHARACTERS, "italic");
        assertFound(Aspect.INDENTATION, "indent left");
        assertFound(Aspect.TABLE_LAYOUT, "rows");
        assertFound(Aspect.TABLE_LAYOUT, "row height");
        assertFound(Aspect.CELLS, "cell shading");
        assertFound(Aspect.CELLS, "cell bottom border");
        assertFound(Aspect.TEMPLATE, "template");
    }

    @Test
    void aPropertyAndItsAttributesAreOneDifference() {
        List<PropertyDifference> cells = everyDifference(CompareOptions.TEXT_ONLY.with(Aspect.TEXT, false)
                .with(Aspect.CELLS, true));
        assertTrue(cells.contains(new PropertyDifference(Aspect.CELLS, "cell shading", "(none)",
                "clear, color auto, fill D9E2F3")), cells.toString());
        assertTrue(cells.contains(new PropertyDifference(Aspect.CELLS, "cell bottom border", "(none)",
                "single, color C00000, space 0, sz 12")), cells.toString());
    }

    @Test
    void wordsBandingFlagsAreNoDifference() {
        assertTrue(everyDifference(CompareOptions.ALL).stream().noneMatch(d -> d.property().contains("cnfStyle")));
    }

    @Test
    void theIndentationIsComparedAsItApplies() {
        Row indented = row(DocumentComparer.compare(REVISION_A, REVISION_B,
                CompareOptions.TEXT_ONLY.with(Aspect.INDENTATION, true)), "The parts needed");
        assertEquals(Row.Status.OTHERWISE, indented.status());
        assertEquals(List.of(new PropertyDifference(Aspect.INDENTATION, "indent left", "(none)", "720")),
                indented.diff().properties());
    }

    @Test
    void withoutTheTextOnlyTheLayoutCounts() {
        CompareOptions layout = CompareOptions.LAYOUT_ONLY;
        Comparison comparison = DocumentComparer.compare(REVISION_A, REVISION_B, layout);
        Row changed = row(comparison, "This procedure describes");
        assertEquals(Row.Status.SAME, changed.status(), "its words differ, but they do not count");
        assertEquals("This procedure describes the periodic maintenance of the KP-40 hydraulic pump installed on the "
                + "test benches of building|(s)| C|( and D)|.", marked(changed.right().text(), changed.diff().right()),
                "they are shown, as blanks left out are");
        assertEquals(Row.Status.SAME, row(comparison, "It replaces revision A").status(), "added: text");
        Row moved = row(comparison, "Safety");
        assertTrue(moved.moved(), "still linked to its match");
        assertEquals(Row.Status.SAME, moved.status(), "but a move is a matter of text");
        assertEquals(Row.Status.OTHERWISE, row(comparison, "Records").status(), "its level still counts");
        assertFalse(comparison.same());
    }

    @Test
    void withoutTheTableLayoutRowsMovedDoNotCount() {
        String[][] parts = {{"Part", "Reference"}, {"Filter", "KP-F-110"}, {"Seal", "KP-J-031"}};
        String[][] moved = {{"Part", "Reference"}, {"Seal", "KP-J-031"}, {"Filter", "KP-F-110"}};
        WordDocument a = document(TableComparerTest.table(parts));
        WordDocument b = document(TableComparerTest.table(moved));
        assertEquals(Row.Status.OTHERWISE, DocumentComparer.compare(a, b, CompareOptions.ALL).stories().getFirst()
                .rows().getFirst().status());
        assertEquals(Row.Status.SAME, DocumentComparer.compare(a, b, CompareOptions.TEXT_ONLY).stories().getFirst()
                .rows().getFirst().status());
    }

    /** Found with its switch alone, under its kind; not found with every other switch on. */
    private static void assertFound(Aspect aspect, String property) {
        List<PropertyDifference> alone = everyDifference(CompareOptions.TEXT_ONLY.with(Aspect.TEXT, false)
                .with(aspect, true));
        assertTrue(alone.stream().anyMatch(d -> d.property().equals(property) && d.aspect() == aspect),
                property + " with " + aspect + ": " + alone);
        assertTrue(alone.stream().allMatch(d -> d.aspect() == aspect), "nothing but " + aspect + ": " + alone);
        List<PropertyDifference> others = everyDifference(CompareOptions.ALL.with(aspect, false));
        assertTrue(others.stream().noneMatch(d -> d.aspect() == aspect), aspect + " left out: " + others);
    }

    /** Every property difference of revision A against B: the documents', the paragraphs', the tables' and cells'. */
    private static List<PropertyDifference> everyDifference(CompareOptions options) {
        Comparison comparison = DocumentComparer.compare(REVISION_A, REVISION_B, options);
        List<PropertyDifference> all = new ArrayList<>(comparison.documentDifferences());
        for (StoryComparison story : comparison.stories()) {
            for (Row row : story.rows()) {
                all.addAll(row.diff().properties());
                all.addAll(row.diff().leftFormatting().stream().flatMap(f -> f.differences().stream()).toList());
                if (row.table() != null) {
                    row.table().rowProperties().forEach(all::addAll);
                    for (List<Row> cells : row.table().cells()) {
                        for (Row cell : cells) {
                            if (cell != null) {
                                all.addAll(cell.diff().properties());
                            }
                        }
                    }
                }
            }
        }
        return all;
    }

    private static Row row(Comparison comparison, String start) {
        return comparison.stories().getFirst().rows().stream()
                .filter(row -> row.right() != null && row.right().text().startsWith(start)).findFirst()
                .orElseThrow(() -> new AssertionError("no row on the right starts with " + start));
    }

    private static WordDocument document(org.jtools.doccomparator.document.Table table) {
        return new WordDocument("test.docx", new org.jtools.doccomparator.document.Story(
                org.jtools.doccomparator.document.Story.Kind.BODY, "Body", List.of(table)), List.of(), List.of(),
                org.jtools.doccomparator.document.StyleSheet.NONE, org.jtools.doccomparator.document.Template.NONE,
                false);
    }

    private static WordDocument read(String name) {
        try {
            return DocxReader.read(Path.of("samples", name));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
