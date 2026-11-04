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
        assertEquals(Pairing.Status.SAME, pairing(comparison, "Records").status(), "a heading's level is layout");
    }

    @Test
    void eachKindIsFoundWithItsSwitchOnlyAndNeverAsAnother() {
        assertFound(Aspect.HEADINGS, "heading");
        assertFound(Aspect.STYLES, "paragraph style");
        assertFound(Aspect.CHARACTERS, "italic");
        assertFound(Aspect.INDENTATION, "indent left");
        assertFound(Aspect.SPACING, "spacing after");
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
        Pairing indented = pairing(DocumentComparer.compare(REVISION_A, REVISION_B,
                CompareOptions.TEXT_ONLY.with(Aspect.INDENTATION, true)), "The parts needed");
        assertEquals(Pairing.Status.OTHERWISE, indented.status());
        assertEquals(List.of(new PropertyDifference(Aspect.INDENTATION, "indent left", "(none)", "720")),
                indented.diff().properties());
    }

    /** A list item's indent comes from its list's level, which wins over its style's: Word's order. */
    @Test
    void aListGivesItsItemsTheirIndent() {
        org.jtools.doccomparator.document.Paragraph item = REVISION_A.body().blocks().stream()
                .filter(b -> b instanceof org.jtools.doccomparator.document.Paragraph p
                        && p.text().startsWith("Replacement of the suction"))
                .map(b -> (org.jtools.doccomparator.document.Paragraph) b).findFirst().orElseThrow();
        assertEquals("360", item.listItem().paragraph().get("ind.hanging"), "read from the numbering");
        org.jtools.doccomparator.document.Formatting applied = REVISION_A.styles().effectiveParagraph(item);
        assertEquals("720", applied.get("ind.left"));
        assertEquals("360", applied.get("ind.hanging"));
    }

    @Test
    void withoutTheTextOnlyTheLayoutCounts() {
        CompareOptions layout = CompareOptions.LAYOUT_ONLY;
        Comparison comparison = DocumentComparer.compare(REVISION_A, REVISION_B, layout);
        Pairing changed = pairing(comparison, "This procedure describes");
        assertEquals(Pairing.Status.SAME, changed.status(), "its words differ, but they do not count");
        assertEquals(changed.right().text(), marked(changed.right().text(), changed.diff().right()),
                "nor shown: nothing marked in it");
        assertEquals(Pairing.Status.SAME, pairing(comparison, "It replaces revision A").status(), "added: text");
        Pairing moved = pairing(comparison, "Safety");
        assertTrue(moved.moved(), "still linked to its match");
        assertEquals(Pairing.Status.SAME, moved.status(), "but a move is a matter of text");
        assertEquals(Pairing.Status.OTHERWISE, pairing(comparison, "Records").status(), "its level still counts");
        assertFalse(comparison.same());
    }

    @Test
    void withoutTheTableLayoutRowsMovedDoNotCount() {
        String[][] parts = {{"Part", "Reference"}, {"Filter", "KP-F-110"}, {"Seal", "KP-J-031"}};
        String[][] moved = {{"Part", "Reference"}, {"Seal", "KP-J-031"}, {"Filter", "KP-F-110"}};
        WordDocument a = document(TableComparerTest.table(parts));
        WordDocument b = document(TableComparerTest.table(moved));
        assertEquals(Pairing.Status.OTHERWISE, DocumentComparer.compare(a, b, CompareOptions.ALL).stories().getFirst()
                .pairings().getFirst().status());
        assertEquals(Pairing.Status.SAME, DocumentComparer.compare(a, b, CompareOptions.TEXT_ONLY).stories().getFirst()
                .pairings().getFirst().status());
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
            for (Pairing pairing : story.pairings()) {
                all.addAll(pairing.diff().properties());
                all.addAll(pairing.diff().leftFormatting().stream().flatMap(f -> f.differences().stream()).toList());
                if (pairing.table() != null) {
                    pairing.table().rowProperties().forEach(all::addAll);
                    for (List<Pairing> cells : pairing.table().cells()) {
                        for (Pairing cell : cells) {
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

    private static Pairing pairing(Comparison comparison, String start) {
        return comparison.stories().getFirst().pairings().stream()
                .filter(pairing -> pairing.right() != null && pairing.right().text().startsWith(start)).findFirst()
                .orElseThrow(() -> new AssertionError("no pairing on the right starts with " + start));
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
