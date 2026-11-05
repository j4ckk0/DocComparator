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

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jtools.doccomparator.compare.CompareOptions;
import org.jtools.doccomparator.compare.Comparison;
import org.jtools.doccomparator.compare.DocumentComparer;
import org.jtools.doccomparator.document.Block;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.WordDocument;
import org.jtools.doccomparator.document.Story;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Text pasted, or a text file, read as a document: a paragraph a line. */
class TextReaderTest {

    @Test
    void textFilesByTheirExtensionWhateverItsCase() {
        for (String name : List.of("a.txt", "notes.TEXT", "README.md", "guide.Markdown")) {
            assertTrue(new TextReader().reads(Path.of(name)), name);
        }
        for (String name : List.of("a.docx", "md", "a.txt.docx", "a.mdx")) {
            assertFalse(new TextReader().reads(Path.of(name)), name);
        }
    }

    @Test
    void theEncodingTheByteOrderMarkNames() {
        String text = "Été → déjà";
        assertEquals(text, TextReader.decode(bytes(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},
                text, StandardCharsets.UTF_8)), "UTF-8, its mark dropped");
        assertEquals(text, TextReader.decode(bytes(new byte[] {(byte) 0xFF, (byte) 0xFE},
                text, StandardCharsets.UTF_16LE)), "UTF-16, little-endian");
        assertEquals(text, TextReader.decode(bytes(new byte[] {(byte) 0xFE, (byte) 0xFF},
                text, StandardCharsets.UTF_16BE)), "UTF-16, big-endian");
    }

    @Test
    void withoutAMarkUtf8OrElseWindowsLatin() {
        assertEquals("Été → déjà", TextReader.decode("Été → déjà".getBytes(StandardCharsets.UTF_8)));
        assertEquals("Été, déjà, 5 €", TextReader.decode("Été, déjà, 5 €".getBytes(Charset.forName("windows-1252"))),
                "not UTF-8: as an older Windows program wrote it");
        assertEquals("", TextReader.decode(new byte[0]));
    }

    @Test
    void aTextFileIsNamedByItsFile(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("notes.md");
        Files.writeString(file, "# Title\r\n\r\nSome *text*.\r\n");
        WordDocument document = new TextReader().read(file);
        assertEquals("notes.md", document.fileName());
        assertEquals(List.of("# Title", "", "Some *text*."), texts(document), "Markdown as the text it is");
    }

    private static byte[] bytes(byte[] mark, String text, Charset charset) {
        byte[] encoded = text.getBytes(charset);
        byte[] all = new byte[mark.length + encoded.length];
        System.arraycopy(mark, 0, all, 0, mark.length);
        System.arraycopy(encoded, 0, all, mark.length, encoded.length);
        return all;
    }

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
        assertEquals(Story.Name.BODY, document.body().name());
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
