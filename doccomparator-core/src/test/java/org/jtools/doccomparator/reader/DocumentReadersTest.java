package org.jtools.doccomparator.reader;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jtools.doccomparator.docx.DocxReader;
import org.jtools.doccomparator.document.Document;
import org.jtools.doccomparator.pdf.PdfReader;
import org.jtools.doccomparator.text.TextReader;
import org.jtools.doccomparator.xlsx.XlsxReader;
import org.junit.jupiter.api.Test;

/** The readers found as services, in their order, and what a reader of another format is given. */
class DocumentReadersTest {

    @Test
    void theProgramsReadersAreFoundInTheirOrder() {
        assertEquals(List.of(DocxReader.class, XlsxReader.class, PdfReader.class, TextReader.class),
                DocumentReaders.all().stream().map(Object::getClass).toList());
        assertEquals(List.of("docx", "docm", "dotx", "dotm", "xlsx", "xlsm", "xltx", "xltm", "pdf", "txt", "text", "md",
                "markdown"), DocumentReaders.extensions());
    }

    @Test
    void aFileIsReadByTheReaderOfItsExtensionWhateverItsCase() {
        assertEquals(PdfReader.class, DocumentReaders.of(Path.of("Report.PDF")).orElseThrow().getClass());
        assertEquals(XlsxReader.class, DocumentReaders.of(Path.of("budget.xlsm")).orElseThrow().getClass());
        assertTrue(DocumentReaders.of(Path.of("drawing.svg")).isEmpty());
        assertTrue(DocumentReaders.of(Path.of("README")).isEmpty());
    }

    @Test
    void eachReaderNamesItsFormatAndSaysHowItReadsWhenItMatters() {
        assertEquals("Word documents", new DocxReader().formatName());
        assertEquals(null, new DocxReader().readingNote(), "a Word document is read as it is");
        assertEquals("PDF: its paragraphs rebuilt from the lines of its pages", new PdfReader().readingNote());
    }

    /**
     * The module's declaration and the class path's services file list the same readers: the tests run on the class
     * path, the distributions as a module, and either alone would leave the other without them.
     */
    @Test
    void theModuleAndTheClassPathProvideTheSameReaders() throws IOException {
        String module = Files.readString(Path.of("src", "main", "java", "module-info.java"));
        Matcher provides = Pattern.compile("provides org\\.jtools\\.doccomparator\\.reader\\.DocumentReader with ([^;]+);")
                .matcher(module);
        assertTrue(provides.find(), module);
        assertTrue(module.contains("uses org.jtools.doccomparator.reader.DocumentReader;"), "ServiceLoader in a module");
        List<String> declared = Arrays.stream(provides.group(1).split(",")).map(String::strip).toList();
        try (InputStream in = DocumentReaders.class.getResourceAsStream(
                "/META-INF/services/org.jtools.doccomparator.reader.DocumentReader")) {
            List<String> listed = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::strip).filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
            assertEquals(declared, listed);
        }
    }

    /** A reader of another format: its extensions as given, whatever their case or dot. */
    private static final class Svg extends AbstractDocumentReader {

        Svg() {
            super(".SVG", "svgz");
        }

        @Override
        public String formatName() {
            return "Drawings";
        }

        @Override
        protected Document readFile(Path file) throws IOException {
            throw new IOException("its drawings hold no text");
        }
    }

    @Test
    void whatAReaderCannotReadIsOneExceptionNamingTheFileOnce() {
        DocumentReadException refused = assertThrows(DocumentReadException.class, () -> new Svg().read(Path.of("dir",
                "plan.svg")));
        assertEquals("plan.svg: its drawings hold no text", refused.getMessage());
        assertEquals("plan.svg", refused.fileName());
        assertEquals("its drawings hold no text", refused.reason());
    }

    @Test
    void aFileOfNoReadersExtensionIsReadAsAWordDocument() {
        assertEquals(DocxReader.class, DocumentReaders.forFile(Path.of("draft.tmp")).getClass());
        assertEquals(PdfReader.class, DocumentReaders.forFile(Path.of("report.pdf")).getClass());
    }

    @Test
    void anotherReadersExtensionsAreNormalisedAndItComesAfterTheProgramsOwn() {
        Svg svg = new Svg();
        assertEquals(List.of("svg", "svgz"), svg.extensions());
        assertTrue(svg.reads(Path.of("plan.Svg")));
        assertFalse(svg.reads(Path.of("plan.svg.bak")));
        assertEquals(DocumentReader.LATER, svg.order());
        assertTrue(DocumentReaders.all().stream().allMatch(reader -> reader.order() < svg.order()));
        assertThrows(IllegalArgumentException.class, () -> new AbstractDocumentReader() {
            @Override
            public String formatName() {
                return "none";
            }

            @Override
            protected Document readFile(Path file) {
                return null;
            }
        });
    }
}
