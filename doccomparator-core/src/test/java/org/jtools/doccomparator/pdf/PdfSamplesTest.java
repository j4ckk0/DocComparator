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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.jtools.doccomparator.compare.CompareOptions;
import org.jtools.doccomparator.compare.DocumentComparer;
import org.jtools.doccomparator.compare.Pairing;
import org.jtools.doccomparator.compare.StoryComparison;
import org.jtools.doccomparator.document.Block;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.Story;
import org.jtools.doccomparator.document.Document;
import org.junit.jupiter.api.Test;

/** The sample PDFs (samples/make_pdfs.py), as LibreOffice writes them, their words and their differences. */
class PdfSamplesTest {

    private static Document sample(String name) throws IOException {
        return new PdfReader().read(Path.of("..", "samples", name));
    }

    /** Every pairing that differs, as "status: left | right", in each story. */
    private static List<String> differences(Document left, Document right, CompareOptions options) {
        List<String> said = new ArrayList<>();
        for (StoryComparison story : DocumentComparer.compare(left, right, options).stories()) {
            for (Pairing pairing : story.pairings()) {
                if (pairing.differs()) {
                    said.add(pairing.status() + ": " + (pairing.left() == null ? "" : pairing.left().text()) + " | "
                            + (pairing.right() == null ? "" : pairing.right().text()));
                }
            }
        }
        return said;
    }

    private static Set<String> words(Document document) {
        Set<String> words = new TreeSet<>();
        List<Story> stories = new ArrayList<>(document.headersAndFooters());
        stories.add(document.body());
        for (Story story : stories) {
            for (Block block : story.blocks()) {
                Paragraph paragraph = (Paragraph) block;
                String mark = paragraph.listItem() == null ? "" : paragraph.listItem().levelText() + " ";
                words.addAll(Arrays.asList((mark + paragraph.text()).trim().split("\\s+")));
            }
        }
        return words;
    }

    @Test
    void theWordsReadAreThoseAnotherReaderReads() throws IOException {
        for (String rev : List.of("A", "B")) {
            Set<String> expected;
            try (InputStream in = PdfSamplesTest.class.getResourceAsStream("procedure-rev" + rev + ".words")) {
                expected = new TreeSet<>(Arrays.asList(new String(in.readAllBytes(), StandardCharsets.UTF_8)
                        .split("\\R")));
            }
            Set<String> read = words(sample("procedure-rev" + rev + ".pdf"));
            // a numbered item's mark is read apart, as "%1."; a page's number, in the footer, once: small numbers
            // are left out of both
            read.remove("%1.");
            read.removeIf(word -> word.matches("[0-9]{1,2}\\.?"));
            expected.removeIf(word -> word.matches("[0-9]{1,2}\\.?"));
            assertEquals(expected, read, "procedure-rev" + rev + ".pdf");
        }
    }

    @Test
    void aPdfComparedWithItselfIsTheSame() throws IOException {
        Document procedure = sample("procedure-revA.pdf");
        assertEquals(List.of(), differences(procedure, procedure, CompareOptions.ALL));
    }

    @Test
    void theNextVersionsChangesAreFoundAsBetweenTheWordDocuments() throws IOException {
        List<String> said = differences(sample("procedure-revA.pdf"), sample("procedure-revB.pdf"),
                CompareOptions.ALL);
        String all = String.join("\n", said);
        assertTrue(said.contains("CHANGED: Document KP-MP-012, revision A, approved by the workshop manager. | "
                + "Document KP-MP-012, revision B, approved by the workshop manager."), all);
        assertTrue(said.contains("RIGHT_ONLY:  | It replaces revision A, which no longer applies after 1 December "
                + "2026."), all);
        assertTrue(said.contains("CHANGED: Unscrew the filter cover by hand, without any tool. | Unscrew the filter "
                + "cover with the KP-T-05 strap wrench."), "the steps paired, their numbers apart\n" + all);
        assertTrue(said.contains("RIGHT_ONLY:  | Drain valve seal KP-J-034 1"), "the table's row inserted\n" + all);
        assertTrue(said.contains("MOVED: Safety | "), "the section moved\n" + all);
        assertTrue(said.contains("CHANGED: Revision A — Page 1 | Revision B — Page 1"), "the footer\n" + all);
        assertTrue(said.stream().noneMatch(line -> line.contains("Stop the pump")),
                "a step only renumbered is the same\n" + all);
        List<String> textOnly = differences(sample("procedure-revA.pdf"), sample("procedure-revB.pdf"),
                CompareOptions.TEXT_ONLY);
        assertTrue(textOnly.stream().noneMatch(line -> line.startsWith("OTHERWISE")),
                "no formatting with Text only\n" + String.join("\n", textOnly));
    }
}
