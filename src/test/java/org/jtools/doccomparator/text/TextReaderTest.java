package org.jtools.doccomparator.text;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.jtools.doccomparator.compare.CompareOptions;
import org.jtools.doccomparator.compare.Comparison;
import org.jtools.doccomparator.compare.DocumentComparer;
import org.jtools.doccomparator.document.Block;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.WordDocument;
import org.junit.jupiter.api.Test;

/** Text pasted, read as a document: a paragraph a line. */
class TextReaderTest {

    @Test
    void aParagraphALineWhateverItsBreaks() {
        assertEquals(List.of("one", "two", "three", "four"), texts("one\r\ntwo\nthree\rfour"));
    }

    @Test
    void theLastBreakEndsTheLastParagraph() {
        assertEquals(List.of("one", "two"), texts("one\ntwo\n"));
        assertEquals(List.of("one", "two", ""), texts("one\ntwo\n\n"), "a blank line at the end: one more");
    }

    @Test
    void anEmptyLineIsAnEmptyParagraphTabsKept() {
        WordDocument document = TextReader.read("pasted", "a\tb\n\nc");
        assertEquals(List.of("a\tb", "", "c"), texts(document));
        assertTrue(((Paragraph) document.body().blocks().get(1)).runs().isEmpty(), "no run, as Word's empty one");
    }

    @Test
    void noStyleNoFormattingNothingButTheBody() {
        WordDocument document = TextReader.read("pasted", "one");
        Paragraph paragraph = (Paragraph) document.body().blocks().getFirst();
        assertEquals(null, paragraph.styleId());
        assertTrue(paragraph.formatting().isEmpty());
        assertEquals(0, paragraph.headingLevel());
        assertTrue(document.headersAndFooters().isEmpty());
        assertTrue(document.notes().isEmpty());
        assertFalse(document.hasTrackedChanges());
        assertEquals("pasted", document.fileName());
        assertEquals("Body", document.body().label());
    }

    @Test
    void twoTextsCompareLineByLine() {
        Comparison same = DocumentComparer.compare(TextReader.read("a", "one\ntwo"), TextReader.read("b", "one\r\ntwo\r\n"),
                CompareOptions.ALL);
        assertTrue(same.same(), "the same lines, whatever their breaks");
        Comparison changed = DocumentComparer.compare(TextReader.read("a", "one\ntwo"), TextReader.read("b", "one\nthree"),
                CompareOptions.DEFAULT);
        assertFalse(changed.same());
    }

    private static List<String> texts(String text) {
        return texts(TextReader.read("pasted", text));
    }

    private static List<String> texts(WordDocument document) {
        return document.body().blocks().stream().map(Block.class::cast).map(b -> ((Paragraph) b).text()).toList();
    }
}
