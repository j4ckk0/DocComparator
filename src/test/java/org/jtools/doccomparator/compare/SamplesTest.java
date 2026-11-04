package org.jtools.doccomparator.compare;

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
        List<Pairing> body = comparison.stories().getFirst().pairings();

        Pairing added = rowOnRight(body, "It replaces revision A");
        assertEquals(Pairing.Status.RIGHT_ONLY, added.status());
        assertNull(added.left());

        Pairing edited = rowOnRight(body, "This procedure describes");
        assertEquals("This procedure describes the periodic maintenance of the KP-40 hydraulic pump installed on the "
                + "test benches of building|[s]| C|[ and D]|.", marked(edited.right().text(), edited.diff().right()));

        Pairing rewritten = rowOnRight(body, "Unscrew the filter cover");
        assertEquals(Pairing.Status.CHANGED, rewritten.status());
        assertEquals("Unscrew the filter cover by hand, without any tool.", rewritten.left().text());

        Pairing tracked = rowOnRight(body, "Fit a new element");
        assertEquals("Fit a new element, reference KP-F-110, and screw the cover back|[ and tighten it to 25 N·m]|.",
                marked(tracked.right().text(), tracked.diff().right()));

        Pairing blanks = rowOnRight(body, "Repairs of the motor");
        assertEquals(Pairing.Status.SAME, blanks.status(), "blanks alone, left out");

        Pairing heading = rowOnRight(body, "Records");
        assertEquals(Pairing.Status.OTHERWISE, heading.status());
        assertTrue(Paragraphs.said(heading.diff().properties()).contains("HEADINGS heading: heading 1 → heading 2"));

        Pairing underlined = rowOnRight(body, "Record the date");
        assertEquals(Pairing.Status.CHANGED, underlined.status());
        assertEquals(List.of("CHARACTERS bold: true → (none)", "CHARACTERS underline: (none) → single"),
                Paragraphs.said(underlined.diff().rightFormatting().getFirst().differences()));

        Pairing table = body.stream().filter(r -> r.table() != null).findFirst().orElseThrow();
        assertEquals(Pairing.Status.CHANGED, table.status());
        assertTrue(Paragraphs.said(table.diff().properties()).contains("TABLE_LAYOUT rows: 4 → 5"));
        TableComparison cells = table.table();
        assertEquals(List.of(new Slot(0, 0, -1), new Slot(1, 1, -1), new Slot(2, 2, -1)), cells.columns());
        assertEquals(new Slot(-1, 3, -1), cells.rows().get(3), "a row added");
        assertEquals("Drain valve seal", cells.cell(3, 0).right().text());
        assertEquals(Pairing.Status.RIGHT_ONLY, cells.cell(3, 0).status());
        Pairing quantity = cells.cell(4, 2);
        assertEquals("{2} litres", marked(quantity.left().text(), quantity.diff().left()).replace("|", ""));
        assertEquals(Pairing.Status.SAME, cells.cell(4, 0).status(), "the rows after the one added are in place");

        assertTrue(Paragraphs.said(comparison.documentDifferences()).contains("TEMPLATE template: Keldar-Procedure.dotx → Keldar-Procedure-2026.dotx"));
        StoryComparison headersAndFooters = comparison.stories().get(1);
        assertEquals(StoryComparison.Part.HEADERS_AND_FOOTERS, headersAndFooters.part());
        assertEquals(1, headersAndFooters.differences(), "the footer's revision");
        assertEquals(1, comparison.stories().get(2).differences(), "the footnote");
    }

    /** A figure redrawn: paired with the one it replaces, the picture changed; one copied as it is: the same. */
    @Test
    void picturesAreComparedByTheirImages() {
        List<Pairing> body = DocumentComparer.compare(REVISION_A, REVISION_B, Paragraphs.FULL).stories().getFirst().pairings();
        Pairing figure = body.stream().filter(pairing -> pairing.left() != null && pairing.right() != null
                && pairing.left().text().equals("\uFFFC")).findFirst().orElseThrow();
        assertEquals(Pairing.Status.CHANGED, figure.status());
        assertEquals("{\uFFFC}", marked(figure.right().text(), figure.diff().right()));
        assertEquals(Pairing.Status.SAME, rowOnRight(body, "Figure 1").status(), "its caption");

        List<Pairing> handbook = DocumentComparer.compare(REVISION_A, HANDBOOK, CompareOptions.DEFAULT).stories().getFirst()
                .pairings();
        assertTrue(handbook.stream().anyMatch(pairing -> pairing.left() != null && pairing.right() != null
                && pairing.left().text().equals("\uFFFC") && pairing.status() == Pairing.Status.SAME), "copied as it is");
    }

    @Test
    void theSwappedSectionIsMovedOnBothSides() {
        List<Pairing> body = DocumentComparer.compare(REVISION_A, REVISION_B, Paragraphs.FULL)
                .stories().getFirst().pairings();
        for (String moved : List.of("Safety", "Before any operation", "The residual pressure", "Wear safety glasses")) {
            Pairing right = rowOnRight(body, moved);
            assertTrue(right.moved(), moved);
            Pairing left = body.get(right.counterpart());
            assertEquals(body.indexOf(right), left.counterpart(), "each names the other");
            assertTrue(left.left().text().startsWith(moved));
        }
        Pairing italic = rowOnRight(body, "Wear safety glasses");
        assertEquals(Pairing.Status.OTHERWISE, italic.status());
        assertEquals(List.of("CHARACTERS italic: (none) → true"),
                Paragraphs.said(italic.diff().rightFormatting().getFirst().differences()));
        Pairing pressure = rowOnRight(body, "The residual pressure");
        assertEquals("The residual pressure in the circuit can reach |{15}| bar for several minutes after the stop; "
                + "wait at least |{fifteen}| minutes before opening any connection.",
                marked(pressure.right().text(), pressure.diff().right()));
    }

    @Test
    void whatIsLeftOutDoesNotCount() {
        CompareOptions textOnly = CompareOptions.TEXT_ONLY;
        Comparison comparison = DocumentComparer.compare(REVISION_A, REVISION_B, textOnly);
        List<Pairing> body = comparison.stories().getFirst().pairings();
        assertEquals(Pairing.Status.SAME, rowOnRight(body, "Records").status());
        assertEquals(Pairing.Status.MOVED, rowOnRight(body, "Wear safety glasses").status());
        assertTrue(rowOnRight(body, "Wear safety glasses").moved(), "but still moved");
        assertEquals(List.of(), comparison.documentDifferences());
        assertEquals(List.of(StoryComparison.Part.BODY, StoryComparison.Part.NOTES),
                comparison.stories().stream().map(StoryComparison::part).toList());

        List<Pairing> withBlanks = DocumentComparer.compare(REVISION_A, REVISION_B, textOnly.with(Aspect.BLANKS, true))
                .stories().getFirst().pairings();
        assertEquals(Pairing.Status.CHANGED, rowOnRight(withBlanks, "Repairs of the motor").status());
        assertTrue(withBlanks.stream().anyMatch(pairing -> pairing.right() != null && pairing.right().text().isEmpty()
                && pairing.status() == Pairing.Status.RIGHT_ONLY), "the empty paragraph added");
    }

    @Test
    void theCopiedParagraphsOfAnotherDocumentAreFound() {
        Comparison comparison = DocumentComparer.compare(REVISION_A, HANDBOOK, Paragraphs.FULL);
        List<Pairing> body = comparison.stories().getFirst().pairings();
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

        Pairing padlock = rowOnRight(body, "Before any operation");
        assertEquals("Before any operation, the pump must be stopped, isolated and locked out with |{your own}| padlock.",
                marked(padlock.right().text(), padlock.diff().right()));
        assertEquals(Pairing.Status.MOVED, rowOnRight(body, "Wear safety glasses").status());
        for (String own : List.of("Why it matters", "A clogged filter", "Follow the steps below",
                "Why must you wait", "Where do you record", "Which reference")) {
            assertEquals(Pairing.Status.RIGHT_ONLY, rowOnRight(body, own).status(), own);
        }
        assertTrue(Paragraphs.said(comparison.documentDifferences()).contains(
                "TEMPLATE template: Keldar-Procedure.dotx → Keldar-Training.dotx"));
        assertFalse(comparison.documentDifferences().stream()
                .anyMatch(difference -> difference.property().say().startsWith("style \"heading 1\" type")),
                "the styles are paired by name, not by id (Heading1, Titre1)");
    }

    /** The pairing of the right paragraph starting so is paired with the left one starting so, in place or moved. */
    private static void assertCopiedFrom(List<Pairing> pairings, String right, String left, boolean moved) {
        Pairing pairing = rowOnRight(pairings, right);
        assertEquals(moved, pairing.moved(), right);
        Pairing partner = moved ? pairings.get(pairing.counterpart()) : pairing;
        assertTrue(partner.left() != null && partner.left().text().startsWith(left),
                right + " comes from " + left + ", not " + partner.left());
    }

    private static Pairing rowOnRight(List<Pairing> pairings, String start) {
        return pairings.stream().filter(pairing -> pairing.right() != null && pairing.right().text().startsWith(start)).findFirst()
                .orElseThrow(() -> new AssertionError("no pairing on the right starts with " + start));
    }

    private static WordDocument read(String name) {
        try {
            return DocxReader.read(Path.of("samples", name));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
