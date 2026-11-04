package org.jtools.doccomparator.compare;

import static org.jtools.doccomparator.compare.ParagraphComparerTest.marked;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.jtools.doccomparator.compare.CompareOptions.Aspect;
import org.jtools.doccomparator.document.WordDocument;
import org.jtools.doccomparator.docx.DocxReader;
import org.junit.jupiter.api.Test;

/**
 * The comparison of the documents of samples/ (made by samples/make_samples.py), which carry each kind of difference
 * the program is for: what it finds is what the script says it changed.
 */
class SamplesTest {

    private static final WordDocument REVISION_A = read("procedure-revA.docx");
    private static final WordDocument REVISION_B = read("procedure-revB.docx");
    private static final WordDocument HANDBOOK = read("training-handbook.docx");

    @Test
    void aDocumentComparedWithItselfIsTheSame() {
        assertTrue(DocumentComparer.compare(REVISION_A, REVISION_A, Paragraphs.FULL).same());
        assertTrue(DocumentComparer.compare(REVISION_B, REVISION_B, Paragraphs.FULL.with(Aspect.BLANKS, true)).same());
    }

    @Test
    void theNextVersionsChangesAreFound() {
        Comparison comparison = DocumentComparer.compare(REVISION_A, REVISION_B, Paragraphs.FULL);
        List<Row> body = comparison.stories().getFirst().rows();

        Row added = rowOnRight(body, "It replaces revision A");
        assertEquals(Row.Status.RIGHT_ONLY, added.status());
        assertNull(added.left());

        Row edited = rowOnRight(body, "This procedure describes");
        assertEquals("This procedure describes the periodic maintenance of the KP-40 hydraulic pump installed on the "
                + "test benches of building|[s]| C|[ and D]|.", marked(edited.right().text(), edited.diff().right()));

        Row rewritten = rowOnRight(body, "Unscrew the filter cover");
        assertEquals(Row.Status.CHANGED, rewritten.status());
        assertEquals("Unscrew the filter cover by hand, without any tool.", rewritten.left().text());

        Row tracked = rowOnRight(body, "Fit a new element");
        assertEquals("Fit a new element, reference KP-F-110, and screw the cover back|[ and tighten it to 25 N·m]|.",
                marked(tracked.right().text(), tracked.diff().right()));

        Row blanks = rowOnRight(body, "Repairs of the motor");
        assertEquals(Row.Status.SAME, blanks.status(), "blanks alone, left out");

        Row heading = rowOnRight(body, "Records");
        assertEquals(Row.Status.OTHERWISE, heading.status());
        assertTrue(heading.diff().properties().contains(new PropertyDifference(Aspect.HEADINGS, "heading", "heading 1", "heading 2")));

        Row underlined = rowOnRight(body, "Record the date");
        assertEquals(Row.Status.CHANGED, underlined.status());
        assertEquals(List.of(new PropertyDifference(Aspect.CHARACTERS, "bold", "true", "(none)"), new PropertyDifference(Aspect.CHARACTERS, "underline", "(none)", "single")),
                underlined.diff().rightFormatting().getFirst().differences());

        Row table = body.stream().filter(r -> r.table() != null).findFirst().orElseThrow();
        assertEquals(Row.Status.CHANGED, table.status());
        assertTrue(table.diff().properties().contains(new PropertyDifference(Aspect.TABLE_LAYOUT, "rows", "4", "5")));
        TableComparison cells = table.table();
        assertEquals(List.of(new Slot(0, 0, -1), new Slot(1, 1, -1), new Slot(2, 2, -1)), cells.columns());
        assertEquals(new Slot(-1, 3, -1), cells.rows().get(3), "a row added");
        assertEquals("Drain valve seal", cells.cell(3, 0).right().text());
        assertEquals(Row.Status.RIGHT_ONLY, cells.cell(3, 0).status());
        Row quantity = cells.cell(4, 2);
        assertEquals("{2} litres", marked(quantity.left().text(), quantity.diff().left()).replace("|", ""));
        assertEquals(Row.Status.SAME, cells.cell(4, 0).status(), "the rows after the one added are in place");

        assertTrue(comparison.documentDifferences().contains(new PropertyDifference(Aspect.TEMPLATE, "template",
                "Keldar-Procedure.dotx", "Keldar-Procedure-2026.dotx")));
        StoryComparison headersAndFooters = comparison.stories().get(1);
        assertEquals("Headers and footers", headersAndFooters.title());
        assertEquals(1, headersAndFooters.differences(), "the footer's revision");
        assertEquals(1, comparison.stories().get(2).differences(), "the footnote");
    }

    @Test
    void theSwappedSectionIsMovedOnBothSides() {
        List<Row> body = DocumentComparer.compare(REVISION_A, REVISION_B, Paragraphs.FULL)
                .stories().getFirst().rows();
        for (String moved : List.of("Safety", "Before any operation", "The residual pressure", "Wear safety glasses")) {
            Row right = rowOnRight(body, moved);
            assertTrue(right.moved(), moved);
            Row left = body.get(right.counterpart());
            assertEquals(body.indexOf(right), left.counterpart(), "each names the other");
            assertTrue(left.left().text().startsWith(moved));
        }
        Row italic = rowOnRight(body, "Wear safety glasses");
        assertEquals(Row.Status.OTHERWISE, italic.status());
        assertEquals(List.of(new PropertyDifference(Aspect.CHARACTERS, "italic", "(none)", "true")),
                italic.diff().rightFormatting().getFirst().differences());
        Row pressure = rowOnRight(body, "The residual pressure");
        assertEquals("The residual pressure in the circuit can reach |{15}| bar for several minutes after the stop; "
                + "wait at least |{fifteen}| minutes before opening any connection.",
                marked(pressure.right().text(), pressure.diff().right()));
    }

    @Test
    void whatIsLeftOutDoesNotCount() {
        CompareOptions textOnly = CompareOptions.TEXT_ONLY;
        Comparison comparison = DocumentComparer.compare(REVISION_A, REVISION_B, textOnly);
        List<Row> body = comparison.stories().getFirst().rows();
        assertEquals(Row.Status.SAME, rowOnRight(body, "Records").status());
        assertEquals(Row.Status.MOVED, rowOnRight(body, "Wear safety glasses").status());
        assertTrue(rowOnRight(body, "Wear safety glasses").moved(), "but still moved");
        assertEquals(List.of(), comparison.documentDifferences());
        assertEquals(List.of("Body", "Footnotes and endnotes"),
                comparison.stories().stream().map(StoryComparison::title).toList());

        List<Row> withBlanks = DocumentComparer.compare(REVISION_A, REVISION_B, textOnly.with(Aspect.BLANKS, true))
                .stories().getFirst().rows();
        assertEquals(Row.Status.CHANGED, rowOnRight(withBlanks, "Repairs of the motor").status());
        assertTrue(withBlanks.stream().anyMatch(row -> row.right() != null && row.right().text().isEmpty()
                && row.status() == Row.Status.RIGHT_ONLY), "the empty paragraph added");
    }

    @Test
    void theCopiedParagraphsOfAnotherDocumentAreFound() {
        Comparison comparison = DocumentComparer.compare(REVISION_A, HANDBOOK, Paragraphs.FULL);
        List<Row> body = comparison.stories().getFirst().rows();
        assertCopiedFrom(body, "This module describes", "This procedure describes", false);
        assertCopiedFrom(body, "Before any operation", "Before any operation", false);
        assertCopiedFrom(body, "Wear safety glasses", "Wear safety glasses", true);
        assertCopiedFrom(body, "The residual pressure", "The residual pressure", false);
        assertCopiedFrom(body, "Unscrew the cover of the filter", "Unscrew the filter cover", false);
        assertCopiedFrom(body, "Fit a new element (reference", "Fit a new element, reference", false);
        assertCopiedFrom(body, "Record the date", "Record the date", false);
        assertCopiedFrom(body, "The module does not cover", "Repairs of the motor", true);

        TableComparison table = body.stream().filter(r -> r.table() != null).findFirst().orElseThrow().table();
        assertEquals(new Slot(2, -1, -1), table.columns().get(2), "the quantities left behind");
        assertEquals(new Slot(3, -1, -1), table.rows().get(3), "and the oil");

        Row padlock = rowOnRight(body, "Before any operation");
        assertEquals("Before any operation, the pump must be stopped, isolated and locked out with |{your own}| padlock.",
                marked(padlock.right().text(), padlock.diff().right()));
        assertEquals(Row.Status.MOVED, rowOnRight(body, "Wear safety glasses").status());
        for (String own : List.of("Why it matters", "A clogged filter", "Follow the steps below",
                "Why must you wait", "Where do you record", "Which reference")) {
            assertEquals(Row.Status.RIGHT_ONLY, rowOnRight(body, own).status(), own);
        }
        assertTrue(comparison.documentDifferences().contains(
                new PropertyDifference(Aspect.TEMPLATE, "template", "Keldar-Procedure.dotx", "Keldar-Training.dotx")));
        assertFalse(comparison.documentDifferences().stream()
                .anyMatch(difference -> difference.property().startsWith("style \"heading 1\" type")),
                "the styles are paired by name, not by id (Heading1, Titre1)");
    }

    /** The row of the right paragraph starting so is paired with the left one starting so, in place or moved. */
    private static void assertCopiedFrom(List<Row> rows, String right, String left, boolean moved) {
        Row row = rowOnRight(rows, right);
        assertEquals(moved, row.moved(), right);
        Row partner = moved ? rows.get(row.counterpart()) : row;
        assertTrue(partner.left() != null && partner.left().text().startsWith(left),
                right + " comes from " + left + ", not " + partner.left());
    }

    private static Row rowOnRight(List<Row> rows, String start) {
        return rows.stream().filter(row -> row.right() != null && row.right().text().startsWith(start)).findFirst()
                .orElseThrow(() -> new AssertionError("no row on the right starts with " + start));
    }

    private static WordDocument read(String name) {
        try {
            return DocxReader.read(Path.of("samples", name));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
