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

import static org.jtools.doccomparator.pdf.TestPdf.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.jtools.doccomparator.document.Block;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.Run;
import org.jtools.doccomparator.document.Story;
import org.jtools.doccomparator.document.WordDocument;
import org.junit.jupiter.api.Test;

class PdfReaderTest {

    /** The body's paragraphs, as their text. */
    private static List<String> paragraphs(WordDocument document) {
        return texts(document.body().blocks());
    }

    private static List<String> texts(List<Block> blocks) {
        return blocks.stream().map(block -> ((Paragraph) block).text()).toList();
    }

    private static Paragraph first(WordDocument document) {
        return (Paragraph) document.body().blocks().getFirst();
    }

    private static WordDocument fixture(String name) throws IOException {
        try {
            return PdfReader.read(Path.of(PdfReaderTest.class.getResource(name).toURI()));
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void linesOfOneParagraphAreJoinedAndAWiderGapStartsAnother() throws IOException {
        WordDocument document = new TestPdf().page(text("F1", 12, 72, 700, "The first line of a")
                + text("F1", 12, 72, 686, "paragraph goes on here.")
                + text("F1", 12, 72, 672, "And it ends on a third line.")
                + text("F1", 12, 72, 640, "Another paragraph, after a gap.")).read();

        assertEquals(List.of("The first line of a paragraph goes on here. And it ends on a third line.",
                "Another paragraph, after a gap."), paragraphs(document));
    }

    @Test
    void aGapBetweenWordsIsASpaceAndKerningIsNot() throws IOException {
        WordDocument document = new TestPdf()
                .page("BT /F1 12 Tf 72 700 Td (Hello) Tj 36 0 Td (world) Tj ET\n"
                        + "BT /F1 12 Tf 72 650 Td [(Ke) 20 (rn) -20 (ing) -400 (apart)] TJ ET\n")
                .read();

        assertEquals(List.of("Hello world", "Kerning apart"), paragraphs(document));
    }

    @Test
    void fontsSizesBoldItalicAndColourAreTheRunsFormatting() throws IOException {
        WordDocument document = new TestPdf().page("BT /F1 12 Tf 72 700 Td (Plain ) Tj /F2 12 Tf (bold ) Tj "
                + "/F3 12 Tf (italic ) Tj /F1 12 Tf 1 0 0 rg (red) Tj ET\n").read();

        List<Run> runs = first(document).runs();
        assertEquals(List.of("Plain ", "bold ", "italic ", "red"), runs.stream().map(Run::text).toList());
        assertEquals("Helvetica", runs.get(0).formatting().get("rFonts.ascii"));
        assertEquals("24", runs.get(0).formatting().get("sz"));
        assertNull(runs.get(0).formatting().get("b"));
        assertEquals("true", runs.get(1).formatting().get("b"));
        assertEquals("Helvetica", runs.get(1).formatting().get("rFonts.ascii"), "the family, its style aside");
        assertEquals("true", runs.get(2).formatting().get("i"));
        assertEquals("FF0000", runs.get(3).formatting().get("color"));
        assertNull(runs.get(0).formatting().get("color"), "black is no colour, as in Word");
    }

    @Test
    void theSizeIsTheOneOnThePageWhateverTheMatricesMakeIt() throws IOException {
        // a font of 1 point scaled 18 times by the text matrix, the page halved by the CTM: 9 points shown
        WordDocument document = new TestPdf()
                .page("q 0.5 0 0 0.5 0 0 cm BT /F1 1 Tf 18 0 0 18 144 1400 Tm (Scaled) Tj ET Q\n").read();

        assertEquals("18", first(document).runs().getFirst().formatting().get("sz"));
    }

    @Test
    void aBiggerLineIsAParagraphOfItsOwnAndASuperscriptIsMarked() throws IOException {
        WordDocument document = new TestPdf().page(text("F2", 20, 72, 720, "A title")
                + text("F1", 12, 72, 696, "Body text, the first line")
                + "BT /F1 7 Tf 222 701 Td (2) Tj ET\n"
                + text("F1", 12, 72, 682, "and its second line.")).read();

        assertEquals(List.of("A title", "Body text, the first line2 and its second line."), paragraphs(document));
        Paragraph body = (Paragraph) document.body().blocks().get(1);
        assertEquals("superscript", body.runs().get(1).formatting().get("vertAlign"));
    }

    @Test
    void aCompositeFontIsReadThroughItsToUnicode() throws IOException {
        TestPdf pdf = new TestPdf();
        int cmap = pdf.stream("<< >>", """
                /CIDInit /ProcSet findresource begin 12 dict begin begincmap
                1 begincodespacerange <0000> <FFFF> endcodespacerange
                2 beginbfchar <0001> <00C7> <0004> <FB01> endbfchar
                1 beginbfrange <0002> <0003> <0061> endbfrange
                1 beginbfrange <0010> <0011> [<0076> <00E9>] endbfrange
                endcmap end end
                """);
        int descendant = pdf.object("<< /Type /Font /Subtype /CIDFontType2 /BaseFont /ABCDEF+Calibri-Bold "
                + "/CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >> /DW 500 >>");
        pdf.font("F9", "<< /Type /Font /Subtype /Type0 /BaseFont /ABCDEF+Calibri-Bold /Encoding /Identity-H "
                + "/DescendantFonts [" + descendant + " 0 R] /ToUnicode " + cmap + " 0 R >>");
        // each code 500 thousandths wide (DW): the second word starts 6 points after the first ends
        WordDocument document = pdf.page("BT /F9 12 Tf 72 700 Td <000100020003> Tj 24 0 Td <00100011> Tj "
                + "24 0 Td <0004> Tj ET\n").read();

        assertEquals(List.of("Çab vé ﬁ"), paragraphs(document));
        assertEquals("Calibri", first(document).runs().getFirst().formatting().get("rFonts.ascii"));
        assertEquals("true", first(document).runs().getFirst().formatting().get("b"));
    }

    @Test
    void aSimpleFontsDifferencesAreReadByTheirGlyphNames() throws IOException {
        TestPdf pdf = new TestPdf();
        pdf.font("F9", "<< /Type /Font /Subtype /Type1 /BaseFont /Times-Roman /Encoding << /BaseEncoding "
                + "/WinAnsiEncoding /Differences [65 /eacute /uni20AC /f_i /Ccedilla] >> >>");
        WordDocument document = pdf.page("BT /F9 12 Tf 72 700 Td (ABCD and caf\\351) Tj ET\n").read();

        assertEquals(List.of("é€fiÇ and café"), paragraphs(document));
        assertEquals("Times", first(document).runs().getFirst().formatting().get("rFonts.ascii"));
    }

    @Test
    void aStandardFontWithoutWidthsIsMeasuredByItsOwn() throws IOException {
        // Helvetica's "i" is 222 thousandths wide: drawn one after the other, its letters are no words apart
        TestPdf pdf = new TestPdf();
        pdf.font("F9", "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        WordDocument document = pdf.page("BT /F9 12 Tf 72 700 Td (Mini) Tj 23.34 0 Td (mal) Tj ET\n").read();

        assertEquals(List.of("Minimal"), paragraphs(document));
    }

    @Test
    void objectStreamsAndCompressionReadTheSame() throws IOException {
        String content = text("F1", 12, 72, 700, "Read through a cross-reference stream.")
                + text("F2", 12, 72, 660, "And its objects packed.");
        List<String> plain = paragraphs(new TestPdf().page(content).read());

        assertEquals(plain, paragraphs(new TestPdf().page(content).compressed().read()));
        assertEquals(plain, paragraphs(new TestPdf().page(content).inObjectStreams().read()));
        assertEquals(List.of("Read through a cross-reference stream.", "And its objects packed."), plain);
    }

    @Test
    void aFileWhoseCrossReferencesAreWrongIsReadByItsObjects() throws IOException {
        WordDocument document = new TestPdf().page(text("F1", 12, 72, 700, "Found all the same."))
                .withWrongOffsets().read();

        assertEquals(List.of("Found all the same."), paragraphs(document));
    }

    @Test
    void aParagraphGoesOnAcrossAPageBreakAndThePageStartIsKept() throws IOException {
        WordDocument document = new TestPdf()
                .page(text("F1", 12, 72, 700, "A sentence that does not end")
                        + text("F1", 12, 72, 686, "on its page but goes on"))
                .page(text("F1", 12, 72, 700, "on the next one.")
                        + text("F1", 12, 72, 660, "A paragraph after it."))
                .page(text("F1", 12, 72, 700, "A third page."))
                .read();

        assertEquals(List.of("A sentence that does not end on its page but goes on on the next one.",
                "A paragraph after it.", "A third page."), paragraphs(document));
        Paragraph spanning = first(document);
        assertEquals(List.of(spanning.text().indexOf("on the next")), spanning.pageStarts());
        assertEquals(List.of(0), ((Paragraph) document.body().blocks().get(2)).pageStarts());
    }

    @Test
    void theRunningHeaderAndFooterAreApartFromTheBody() throws IOException {
        TestPdf pdf = new TestPdf();
        List<String> bodies = List.of("What the first page says.", "Then the second.", "And the third.");
        for (int page = 1; page <= 3; page++) {
            pdf.page(text("F1", 9, 72, 760, "Keldar Pumps \u0097 Procedure")   // WinAnsi's em dash
                    + text("F1", 12, 72, 600, bodies.get(page - 1))
                    + text("F1", 9, 72, 40, "Page " + page + " of 3"));
        }
        WordDocument document = pdf.read();

        assertEquals(bodies, paragraphs(document));
        List<Story> running = document.headersAndFooters();
        assertEquals(Story.Kind.HEADER, running.get(0).kind());
        assertEquals(List.of("Keldar Pumps — Procedure"), texts(running.get(0).blocks()));
        assertEquals(Story.Kind.FOOTER, running.get(1).kind());
        assertEquals(List.of("Page 1 of 3"), texts(running.get(1).blocks()));
    }

    @Test
    void aTaggedPdfsPaginationArtifactsAreItsHeaderAndFooter() throws IOException {
        WordDocument document = new TestPdf().page("/Artifact << /Type /Pagination /Subtype /Header >> BDC "
                + text("F1", 9, 72, 760, "Marked as a header") + "EMC\n"
                + text("F1", 12, 72, 700, "The body.")).read();

        assertEquals(List.of("The body."), paragraphs(document));
        assertEquals(List.of("Marked as a header"), texts(document.headersAndFooters().getFirst().blocks()));
    }

    @Test
    void bulletsAndNumbersAreListItemsTheirMarksApart() throws IOException {
        WordDocument document = new TestPdf().page(text("F1", 12, 72, 700, "The steps:")
                + text("F1", 12, 72, 686, "1. Stop the pump.")
                + text("F1", 12, 72, 672, "2. Drain it.")
                + text("F1", 12, 72, 658, "\u0095 A bullet,")
                + text("F1", 12, 72, 644, "a) and a letter.")).read();

        assertEquals(List.of("The steps:", "Stop the pump.", "Drain it.", "A bullet,", "and a letter."),
                paragraphs(document));
        Paragraph second = (Paragraph) document.body().blocks().get(2);
        assertEquals("decimal", second.listItem().numberFormat());
        assertEquals("%1.", second.listItem().levelText());
        Paragraph bullet = (Paragraph) document.body().blocks().get(3);
        assertEquals("bullet", bullet.listItem().numberFormat());
        assertEquals("%1)", ((Paragraph) document.body().blocks().get(4)).listItem().levelText());
    }

    @Test
    void cellsSideBySideAreNotJoinedIntoOneParagraph() throws IOException {
        WordDocument document = new TestPdf().page(text("F1", 12, 72, 700, "Part")
                + text("F1", 12, 300, 700, "Quantity")
                + text("F1", 12, 72, 686, "Filter")
                + text("F1", 12, 300, 686, "1")).read();

        assertEquals(List.of("Part Quantity", "Filter 1"), paragraphs(document));
    }

    @Test
    void theTextOfAFormXObjectIsRead() throws IOException {
        TestPdf pdf = new TestPdf();
        int form = pdf.stream("<< /Type /XObject /Subtype /Form /BBox [0 0 600 100] /Matrix [1 0 0 1 0 600] >>",
                text("F1", 12, 72, 50, "Drawn by a form."));
        WordDocument document = pdf.resources("/XObject << /X1 " + form + " 0 R >>")
                .page("q /X1 Do Q\nBI /W 2 /H 1 /BPC 8 /CS /G ID \u0000ÿ EI\n"
                        + text("F1", 12, 72, 500, "After an inline image.")).read();

        assertEquals(List.of("Drawn by a form.", "After an inline image."), paragraphs(document));
    }

    @Test
    void encryptedFilesOpenedWithoutAPasswordAreRead() throws IOException {
        for (String name : List.of("aes128.pdf", "aes256.pdf", "rc4-40.pdf", "rc4-128.pdf")) {
            WordDocument document = fixture(name);
            assertEquals(List.of("Encrypted text reads the same."), paragraphs(document), name);
        }
        assertEquals("Encrypted Family", first(fixture("aes256.pdf")).runs().getFirst().formatting()
                .get("rFonts.ascii"), "its strings decrypted too");
        assertEquals("Encrypted Family", first(fixture("aes128.pdf")).runs().getFirst().formatting()
                .get("rFonts.ascii"));
    }

    @Test
    void aFileThatAsksAPasswordIsRefused() {
        PdfException refused = assertThrows(PdfException.class, () -> fixture("password.pdf"));
        assertEquals("password.pdf: it is protected by a password", refused.getMessage());
    }

    @Test
    void whatIsNoPdfOrHoldsNoTextIsRefusedSayingWhy() {
        PdfException notPdf = assertThrows(PdfException.class,
                () -> PdfFile.of("notes.pdf", "plain text".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("notes.pdf is not a PDF: it does not start as one", notPdf.getMessage());

        PdfException scan = assertThrows(PdfException.class,
                () -> new TestPdf().page("q 100 0 0 100 0 0 cm 0 0 1 1 re f Q\n").read());
        assertTrue(scan.getMessage().contains("it holds no text"), scan.getMessage());
    }

    @Test
    void aCompositeFontThatDoesNotSayItsCharactersIsRefusedByName() {
        TestPdf pdf = new TestPdf();
        int descendant = pdf.object("<< /Type /Font /Subtype /CIDFontType2 /BaseFont /XYZABC+Mystery "
                + "/CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >> >>");
        pdf.font("F9", "<< /Type /Font /Subtype /Type0 /BaseFont /XYZABC+Mystery /Encoding /Identity-H "
                + "/DescendantFonts [" + descendant + " 0 R] >>");
        PdfException refused = assertThrows(PdfException.class,
                () -> pdf.page("BT /F9 12 Tf 72 700 Td <00010002> Tj ET\n").read());

        assertEquals("test.pdf: its text cannot be read: the font Mystery does not say which characters it draws",
                refused.getMessage());
    }

    @Test
    void aStreamThatWouldInflatePastTheLimitIsRefused() {
        byte[] zeros = new byte[1 << 20];
        byte[] packed = TestPdf.deflate(zeros);
        TestPdf pdf = new TestPdf();
        StringBuilder contents = new StringBuilder();
        for (int i = 0; i < Filters.LARGEST_DECODED / zeros.length + 1; i++) {
            contents.append(pdf.stream("<< /Filter /FlateDecode >>", packed)).append(" 0 R ");
        }
        PdfException refused = assertThrows(PdfException.class, () -> {
            PdfFile file = PdfFile.of("bomb.pdf", pdf.bytes());
            for (String ref : contents.toString().split(" 0 R ")) {
                if (!ref.isBlank()) {
                    PdfObjects.Ref stream = new PdfObjects.Ref(Integer.parseInt(ref.trim()), 0);
                    file.decoded((PdfObjects.Stream) file.resolve(stream));
                }
            }
        });
        assertTrue(refused.getMessage().contains("512 MiB"), refused.getMessage());
    }
}
