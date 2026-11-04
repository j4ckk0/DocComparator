package org.jtools.doccomparator.docx;

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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jtools.doccomparator.document.Block;
import org.jtools.doccomparator.document.Formatting;
import org.jtools.doccomparator.document.ListItem;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.Picture;
import org.jtools.doccomparator.document.Run;
import org.jtools.doccomparator.document.Story;
import org.jtools.doccomparator.document.Table;
import org.jtools.doccomparator.document.Template;
import org.jtools.doccomparator.document.TextBox;
import org.jtools.doccomparator.document.WordDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocxReaderTest {

    @TempDir
    Path directory;

    // ---- the text ----------------------------------------------------------------------------------------------

    @Test
    void theTextIsWhatWordShows() throws IOException {
        WordDocument document = read(TestDocx.body("""
                <w:p w14:paraId="1A2B3C4D">
                  <w:r><w:t xml:space="preserve">The text </w:t></w:r>
                  <w:proofErr w:type="spellStart"/>
                  <w:r w:rsidR="00AB12CD"><w:t>comes</w:t></w:r>
                  <w:proofErr w:type="spellEnd"/>
                  <w:bookmarkStart w:id="0" w:name="here"/>
                  <w:r><w:tab/><w:t>first</w:t><w:br/><w:t>.</w:t></w:r>
                  <w:bookmarkEnd w:id="0"/>
                </w:p>
                <w:p/>
                """));

        List<Paragraph> paragraphs = paragraphs(document.body());
        assertEquals(List.of("The text comes\tfirst\n.", ""), texts(paragraphs));
        assertEquals(1, paragraphs.getFirst().runs().size(), "runs that look the same are one");
        assertFalse(document.hasTrackedChanges());
    }

    @Test
    void breaksHyphensAndSymbols() throws IOException {
        WordDocument document = read(TestDocx.body("""
                <w:p><w:r>
                  <w:t>non</w:t><w:noBreakHyphen/><w:t>breaking</w:t><w:softHyphen/><w:t>soft</w:t>
                  <w:br w:type="page"/><w:cr/><w:sym w:font="Symbol" w:char="F0E0"/>
                </w:r></w:p>
                """));

        assertEquals("non‑breaking­soft\f\n", paragraphs(document.body()).getFirst().text());
    }

    @Test
    void trackedChangesAreReadAsAccepted() throws IOException {
        WordDocument document = read(TestDocx.body("""
                <w:p>
                  <w:r><w:t xml:space="preserve">kept </w:t></w:r>
                  <w:ins w:id="1" w:author="A" w:date="2026-11-04T10:00:00Z"><w:r><w:t>inserted</w:t></w:r></w:ins>
                  <w:del w:id="2" w:author="A" w:date="2026-11-04T10:00:00Z"><w:r><w:delText> deleted</w:delText></w:r></w:del>
                </w:p>
                """));

        assertEquals("kept inserted", paragraphs(document.body()).getFirst().text());
        assertTrue(document.hasTrackedChanges());
    }

    @Test
    void aFieldShowsItsResultNotItsCode() throws IOException {
        WordDocument document = read(TestDocx.body("""
                <w:p>
                  <w:r><w:t xml:space="preserve">Page </w:t></w:r>
                  <w:r><w:fldChar w:fldCharType="begin"/></w:r>
                  <w:r><w:instrText xml:space="preserve"> PAGE </w:instrText></w:r>
                  <w:r><w:fldChar w:fldCharType="separate"/></w:r>
                  <w:r><w:t>7</w:t></w:r>
                  <w:r><w:fldChar w:fldCharType="end"/></w:r>
                  <w:r><w:t xml:space="preserve">, </w:t></w:r>
                  <w:fldSimple w:instr=" DATE \\@ &quot;yyyy-MM-dd&quot; "><w:r><w:t>2026-11-04</w:t></w:r></w:fldSimple>
                </w:p>
                """));

        assertEquals("Page 7, 2026-11-04", paragraphs(document.body()).getFirst().text());
    }

    @Test
    void linksControlsAndTagsKeepTheirText() throws IOException {
        WordDocument document = read(TestDocx.body("""
                <w:p>
                  <w:hyperlink r:id="rId9" w:history="1"><w:r><w:t>a link</w:t></w:r></w:hyperlink>
                  <w:sdt><w:sdtPr><w:alias w:val="Client"/></w:sdtPr>
                    <w:sdtContent><w:r><w:t xml:space="preserve">, a control</w:t></w:r></w:sdtContent></w:sdt>
                  <w:smartTag w:uri="urn:x" w:element="place"><w:r><w:t>, a tag</w:t></w:r></w:smartTag>
                </w:p>
                <w:sdt><w:sdtPr/><w:sdtContent><w:p><w:r><w:t>a block control</w:t></w:r></w:p></w:sdtContent></w:sdt>
                """));

        assertEquals(List.of("a link, a control, a tag", "a block control"), texts(paragraphs(document.body())));
    }

    @Test
    void anEquationKeepsItsText() throws IOException {
        WordDocument document = read(TestDocx.body("""
                <w:p>
                  <w:r><w:t xml:space="preserve">where </w:t></w:r>
                  <m:oMath><m:r><m:t>a</m:t></m:r><m:r><m:t>+b</m:t></m:r></m:oMath>
                </w:p>
                """));

        Paragraph paragraph = paragraphs(document.body()).getFirst();
        assertEquals("where a+b", paragraph.text());
        assertEquals("true", paragraph.runs().getLast().formatting().get("oMath"));
    }

    // ---- the structure -----------------------------------------------------------------------------------------

    @Test
    void tablesHoldTheirCellsBlocksTablesWithin() throws IOException {
        WordDocument document = read(TestDocx.body("""
                <w:tbl>
                  <w:tblPr><w:tblStyle w:val="TableGrid"/><w:tblW w:w="0" w:type="auto"/></w:tblPr>
                  <w:tblGrid><w:gridCol w:w="4536"/><w:gridCol w:w="4536"/></w:tblGrid>
                  <w:tr>
                    <w:tc><w:tcPr><w:gridSpan w:val="2"/></w:tcPr><w:p><w:r><w:t>a1</w:t></w:r></w:p></w:tc>
                    <w:tc>
                      <w:p><w:r><w:t>b1</w:t></w:r></w:p>
                      <w:tbl><w:tr><w:tc><w:p><w:r><w:t>within</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
                      <w:p/>
                    </w:tc>
                  </w:tr>
                </w:tbl>
                """));

        Table table = assertInstanceOf(Table.class, document.body().blocks().getFirst());
        assertEquals("TableGrid", table.styleId());
        assertEquals(Map.of("tblW.w", "0", "tblW.type", "auto"), table.formatting().values());
        List<Table.Cell> cells = table.rows().getFirst().cells();
        assertEquals("2", cells.getFirst().formatting().get("gridSpan"));
        assertEquals(List.of("a1"), texts(paragraphs(cells.getFirst().blocks())));

        List<Block> second = cells.get(1).blocks();
        assertEquals(3, second.size());
        Table within = assertInstanceOf(Table.class, second.get(1));
        assertEquals(List.of("within"), texts(paragraphs(within.rows().getFirst().cells().getFirst().blocks())));
    }

    /** A picture as Word writes one, its size in EMUs (12700 to the point), its description, its image's id. */
    private static String drawing(String id, String description) {
        return "<w:r><w:drawing><wp:inline><wp:extent cx=\"2286000\" cy=\"1270000\"/>"
                + "<wp:docPr id=\"1\" name=\"Picture 1\" descr=\"" + description + "\"/>"
                + "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">"
                + "<pic:pic xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:blipFill>"
                + "<a:blip r:embed=\"" + id + "\"/></pic:blipFill></pic:pic></a:graphicData></a:graphic>"
                + "</wp:inline></w:drawing></w:r>";
    }

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 1, 2, 3};

    /** A picture: a run of its own, its character in the text, its size, description, image and digest kept. */
    @Test
    void aPictureIsReadWithItsSizeAndImage() throws IOException {
        WordDocument document = read(TestDocx.body("<w:p><w:r><w:t xml:space=\"preserve\">See </w:t></w:r>"
                + drawing("rIdPicture", "The pump") + "<w:r><w:t>here.</w:t></w:r></w:p>")
                .binary("word/media/image1.png", PNG)
                .link("word/document.xml", "rIdPicture", "image", "media/image1.png", false));
        Paragraph paragraph = paragraphs(document.body()).getFirst();
        assertEquals("See \uFFFChere.", paragraph.text());
        Picture picture = paragraph.runs().get(1).picture();
        assertEquals(Picture.Kind.IMAGE, picture.kind());
        assertEquals(180, picture.width(), 0.01);
        assertEquals(100, picture.height(), 0.01);
        assertEquals("The pump", picture.description());
        assertEquals("png", picture.format());
        assertArrayEquals(PNG, picture.bytes());
        assertEquals(64, picture.digest().length(), "SHA-256 in hexadecimal");
    }

    /** An image used twice — an icon in two steps — is read once, its bytes shared. */
    @Test
    void anImageUsedAgainIsReadOnce() throws IOException {
        WordDocument document = read(TestDocx.body("<w:p>" + drawing("rIdIcon", "icon") + "</w:p><w:p>"
                + drawing("rIdIcon", "icon") + "</w:p>")
                .binary("word/media/icon.png", PNG)
                .link("word/document.xml", "rIdIcon", "image", "media/icon.png", false));
        List<Paragraph> paragraphs = paragraphs(document.body());
        assertTrue(paragraphs.get(0).runs().getFirst().picture().bytes()
                == paragraphs.get(1).runs().getFirst().picture().bytes(), "the same bytes, kept once");
    }

    /** Word writes a picture twice for older versions of itself, as it does a text box: read once. */
    @Test
    void aPictureForOlderWordsIsReadOnce() throws IOException {
        WordDocument document = read(TestDocx.body("<w:p><w:r><mc:AlternateContent><mc:Choice Requires=\"wps\">"
                + drawing("rIdPicture", "new").replace("<w:r>", "").replace("</w:r>", "")
                + "</mc:Choice><mc:Fallback><w:pict><v:shape style=\"width:180pt;height:100pt\">"
                + "<v:imagedata r:id=\"rIdPicture\"/></v:shape></w:pict></mc:Fallback></mc:AlternateContent>"
                + "</w:r></w:p>")
                .binary("word/media/image1.png", PNG)
                .link("word/document.xml", "rIdPicture", "image", "media/image1.png", false));
        Paragraph paragraph = paragraphs(document.body()).getFirst();
        assertEquals("\uFFFC", paragraph.text());
        assertEquals("new", paragraph.runs().getFirst().picture().description());
    }

    /** A picture outside the document — embedded by its word, or linked — is never fetched. */
    @Test
    void aPictureOutsideTheDocumentIsNeverFetched() throws IOException {
        WordDocument document = read(TestDocx.body("<w:p>" + drawing("rIdOutside", "on a share") + "</w:p>")
                .link("word/document.xml", "rIdOutside", "image", "file:///\\\\fileserver\\pictures\\pump.png", true));
        Picture picture = paragraphs(document.body()).getFirst().runs().getFirst().picture();
        assertEquals(Picture.Kind.LINKED, picture.kind());
        assertNull(picture.bytes());
        assertEquals("link:file:///\\\\fileserver\\pictures\\pump.png", picture.digest());
    }

    /** A header's picture is found through the header's own relationships, not the body's. */
    @Test
    void aHeadersPictureIsTheHeadersOwn() throws IOException {
        WordDocument document = read(TestDocx.body("<w:p/><w:sectPr><w:headerReference w:type=\"default\" "
                + "r:id=\"rIdHeader\"/></w:sectPr>")
                .wordPart("rIdHeader", "header", "header1.xml", "w:hdr",
                        "<w:p>" + drawing("rIdLogo", "logo") + "</w:p>")
                .binary("word/media/logo.png", PNG)
                .link("word/header1.xml", "rIdLogo", "image", "media/logo.png", false));
        Picture logo = paragraphs(document.headersAndFooters().getFirst()).getFirst().runs().getFirst().picture();
        assertEquals(Picture.Kind.IMAGE, logo.kind());
        assertArrayEquals(PNG, logo.bytes());
    }

    /** A chart cannot be shown: a picture saying so stands in its place. */
    @Test
    void aChartIsAPictureSayingSo() throws IOException {
        WordDocument document = read(TestDocx.body("<w:p><w:r><w:drawing><wp:inline><wp:extent cx=\"1270000\" "
                + "cy=\"1270000\"/><wp:docPr id=\"2\" name=\"Chart 2\"/><a:graphic><a:graphicData "
                + "uri=\"http://schemas.openxmlformats.org/drawingml/2006/chart\"><c:chart "
                + "xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\" r:id=\"rIdChart\"/>"
                + "</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"));
        Picture chart = paragraphs(document.body()).getFirst().runs().getFirst().picture();
        assertEquals(Picture.Kind.UNSUPPORTED, chart.kind());
        assertEquals("chart", chart.format());
        assertEquals(100, chart.width(), 0.01);
    }

    /** Where Word's pages began at its last layout: kept, as offsets in the text; after a page break, once. */
    @Test
    void wordsOwnPagesAreKeptWhereTheyBegan() throws IOException {
        WordDocument document = read(TestDocx.body("""
                <w:p><w:r><w:t xml:space="preserve">The end of a page </w:t></w:r>
                  <w:r><w:lastRenderedPageBreak/><w:t>and the next.</w:t></w:r></w:p>
                <w:p><w:r><w:t>Before a break.</w:t><w:br w:type="page"/></w:r></w:p>
                <w:p><w:r><w:lastRenderedPageBreak/><w:t>After it.</w:t></w:r></w:p>
                """));
        List<Paragraph> paragraphs = paragraphs(document.body());
        assertEquals(List.of(18), paragraphs.get(0).pageStarts(), "before \"and the next.\"");
        assertEquals(List.of(), paragraphs.get(1).pageStarts());
        assertEquals(List.of(), paragraphs.get(2).pageStarts(), "the page the break began: said once, by the break");
        assertEquals("The end of a page and the next.", paragraphs.get(0).text(), "no text of its own");
    }

    @Test
    void rowsAndCellsChangedWithTrackChangesAreReadAsAccepted() throws IOException {
        WordDocument document = read(TestDocx.body("""
                <w:tbl>
                  <w:tr><w:trPr><w:trHeight w:val="400"/><w:tblHeader/></w:trPr>
                    <w:tc><w:p><w:r><w:t>kept</w:t></w:r></w:p></w:tc></w:tr>
                  <w:tr><w:trPr><w:ins w:id="1" w:author="M. Laurent" w:date="2026-10-20T09:30:00Z"/></w:trPr>
                    <w:tc><w:p><w:r><w:t>inserted</w:t></w:r></w:p></w:tc></w:tr>
                  <w:tr><w:trPr><w:del w:id="2" w:author="M. Laurent" w:date="2026-10-20T09:31:00Z"/></w:trPr>
                    <w:tc><w:p><w:r><w:delText>deleted</w:delText></w:r></w:p></w:tc></w:tr>
                  <w:tr>
                    <w:tc><w:p><w:r><w:t>a</w:t></w:r></w:p></w:tc>
                    <w:tc><w:tcPr><w:cellDel w:id="3" w:author="M. Laurent"/></w:tcPr><w:p/></w:tc>
                    <w:tc><w:tcPr><w:cellIns w:id="4" w:author="M. Laurent"/><w:shd w:fill="FFFF00"/></w:tcPr>
                      <w:p><w:r><w:t>b</w:t></w:r></w:p></w:tc>
                  </w:tr>
                </w:tbl>
                """));

        Table table = assertInstanceOf(Table.class, document.body().blocks().getFirst());
        assertEquals(3, table.rows().size(), "the row deleted is no more");
        assertEquals(Map.of("trHeight", "400", "tblHeader", "true"), table.rows().getFirst().formatting().values());
        assertEquals(Map.of(), table.rows().get(1).formatting().values(), "inserted: an ordinary row now");
        List<Table.Cell> cells = table.rows().get(2).cells();
        assertEquals(2, cells.size(), "the cell deleted is no more");
        assertEquals(Map.of("shd.fill", "FFFF00"), cells.get(1).formatting().values());
        assertTrue(document.hasTrackedChanges());
    }

    /** Word writes a text box twice — for itself, and in VML for older versions — and it is read once. */
    @Test
    void aTextBoxFollowsItsParagraphOnce() throws IOException {
        WordDocument document = read(TestDocx.body("""
                <w:p>
                  <w:r><w:t>anchor</w:t></w:r>
                  <w:r><mc:AlternateContent>
                    <mc:Choice Requires="wps"><w:drawing><wp:anchor><a:graphic><a:graphicData>
                      <wps:wsp><wps:txbx><w:txbxContent>
                        <w:p><w:r><w:t>in the box</w:t></w:r></w:p>
                      </w:txbxContent></wps:txbx></wps:wsp>
                    </a:graphicData></a:graphic></wp:anchor></w:drawing></mc:Choice>
                    <mc:Fallback><w:pict><v:shape><v:textbox><w:txbxContent>
                      <w:p><w:r><w:t>in the box</w:t></w:r></w:p>
                    </w:txbxContent></v:textbox></v:shape></w:pict></mc:Fallback>
                  </mc:AlternateContent></w:r>
                </w:p>
                """));

        List<Block> blocks = document.body().blocks();
        assertEquals(2, blocks.size());
        assertEquals("anchor", assertInstanceOf(Paragraph.class, blocks.getFirst()).text());
        TextBox box = assertInstanceOf(TextBox.class, blocks.get(1));
        assertEquals(List.of("in the box"), texts(paragraphs(box.blocks())));
    }

    @Test
    void headingsAndListsComeFromTheParagraphOrItsStyles() throws IOException {
        WordDocument document = read(TestDocx.body("""
                <w:p><w:pPr><w:pStyle w:val="MyTitle"/></w:pPr><w:r><w:t>heading 1, from the style it is based on</w:t></w:r></w:p>
                <w:p><w:pPr><w:pStyle w:val="Titre2"/></w:pPr><w:r><w:t>heading 2, by its style's name</w:t></w:r></w:p>
                <w:p><w:pPr><w:pStyle w:val="Heading1"/><w:outlineLvl w:val="9"/></w:pPr><w:r><w:t>body text, whatever its style</w:t></w:r></w:p>
                <w:p><w:pPr><w:pStyle w:val="ListBullet"/></w:pPr><w:r><w:t>a bullet, from its style</w:t></w:r></w:p>
                <w:p><w:pPr><w:numPr><w:ilvl w:val="1"/><w:numId w:val="2"/></w:numPr></w:pPr><w:r><w:t>a level the list overrides</w:t></w:r></w:p>
                <w:p><w:pPr><w:pStyle w:val="ListBullet"/><w:numPr><w:numId w:val="0"/></w:numPr></w:pPr><w:r><w:t>out of its style's list</w:t></w:r></w:p>
                """)
                .wordPart("rIdStyles", "styles", "styles.xml", "w:styles", STYLES)
                .wordPart("rIdNumbering", "numbering", "numbering.xml", "w:numbering", """
                        <w:abstractNum w:abstractNumId="0">
                          <w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="•"/></w:lvl>
                          <w:lvl w:ilvl="1"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="%2."/></w:lvl>
                        </w:abstractNum>
                        <w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num>
                        <w:num w:numId="2"><w:abstractNumId w:val="0"/>
                          <w:lvlOverride w:ilvl="1"><w:lvl w:ilvl="1"><w:numFmt w:val="lowerLetter"/><w:lvlText w:val="%2)"/></w:lvl></w:lvlOverride>
                        </w:num>
                        """));

        List<Paragraph> paragraphs = paragraphs(document.body());
        assertEquals(List.of(1, 2, 0, 0, 0, 0), paragraphs.stream().map(Paragraph::headingLevel).toList());
        assertEquals("MyTitle", paragraphs.getFirst().styleId());
        assertEquals(new ListItem("1", 0, "bullet", "•"), paragraphs.get(3).listItem());
        assertEquals(new ListItem("2", 1, "lowerLetter", "%2)"), paragraphs.get(4).listItem());
        assertNull(paragraphs.get(5).listItem());
        assertNull(paragraphs.getFirst().listItem());
    }

    @Test
    void stylesAndTheirDefaults() throws IOException {
        WordDocument document = read(TestDocx.body("<w:p/>")
                .wordPart("rIdStyles", "styles", "styles.xml", "w:styles", STYLES));

        assertEquals("22", document.styles().defaultRun().get("sz"));
        assertEquals("Calibri", document.styles().defaultRun().get("rFonts.ascii"));
        assertEquals("160", document.styles().defaultParagraph().get("spacing.after"));
        var heading = document.styles().style("Heading1");
        assertEquals("heading 1", heading.name());
        assertEquals("Normal", heading.basedOn());
        assertEquals(Map.of("b", "true", "sz", "32"), heading.run().values());
        assertEquals("true", heading.paragraph().get("keepNext"));
    }

    @Test
    void formattingIsEveryPropertyUnderWordsName() throws IOException {
        String red = """
                <w:rPr><w:b/><w:i w:val="0"/><w:color w:val="FF0000"/><w:u w:val="single" w:color="00FF00"/></w:rPr>""";
        WordDocument document = read(TestDocx.body("""
                <w:p>
                  <w:pPr><w:jc w:val="center"/><w:pBdr><w:top w:val="single" w:sz="4"/></w:pBdr></w:pPr>
                  <w:r>%s<w:t>red</w:t></w:r>
                  <w:r>%s<w:t xml:space="preserve"> and </w:t></w:r>
                  <w:r><w:rPr><w:rStyle w:val="Emphasis"/><w:b w:val="true"/><w:rPrChange w:id="3" w:author="A"><w:rPr/></w:rPrChange></w:rPr><w:t>styled</w:t></w:r>
                </w:p>
                """.formatted(red, red)));

        Paragraph paragraph = paragraphs(document.body()).getFirst();
        assertEquals(Map.of("jc", "center", "pBdr.top", "single", "pBdr.top.sz", "4"), paragraph.formatting().values());
        assertEquals(List.of(
                new Run("red and ", null, new Formatting(Map.of(
                        "b", "true", "i", "false", "color", "FF0000", "u", "single", "u.color", "00FF00"))),
                new Run("styled", "Emphasis", new Formatting(Map.of("b", "true")))), paragraph.runs());
        assertTrue(document.hasTrackedChanges(), "a change of formatting is a tracked change");
    }

    // ---- headers, footers, notes, template ---------------------------------------------------------------------

    @Test
    void headersAndFootersByTheSectionsThatReferToThem() throws IOException {
        WordDocument document = read(TestDocx.body("""
                <w:p><w:pPr><w:sectPr><w:headerReference w:type="default" r:id="rIdH1"/></w:sectPr></w:pPr><w:r><w:t>one</w:t></w:r></w:p>
                <w:p><w:r><w:t>two</w:t></w:r></w:p>
                <w:sectPr>
                  <w:headerReference w:type="first" r:id="rIdH2"/>
                  <w:footerReference w:type="default" r:id="rIdF1"/>
                  <w:pgSz w:w="11906" w:h="16838"/><w:titlePg/>
                </w:sectPr>
                """)
                .wordPart("rIdH1", "header", "header1.xml", "w:hdr", "<w:p><w:r><w:t>The header</w:t></w:r></w:p>")
                .wordPart("rIdH2", "header", "header2.xml", "w:hdr", "<w:p><w:r><w:t>The first page's</w:t></w:r></w:p>")
                .wordPart("rIdF1", "footer", "footer1.xml", "w:ftr", "<w:p><w:r><w:t>The footer</w:t></w:r></w:p>"));

        assertEquals(List.of(Story.Name.headerOrFooter(Story.Kind.HEADER, "default", 1),
                Story.Name.headerOrFooter(Story.Kind.HEADER, Story.Name.FIRST_PAGE, 2),
                Story.Name.headerOrFooter(Story.Kind.FOOTER, "default", 2)),
                document.headersAndFooters().stream().map(Story::name).toList());
        assertEquals(List.of(Story.Kind.HEADER, Story.Kind.HEADER, Story.Kind.FOOTER),
                document.headersAndFooters().stream().map(Story::kind).toList());
        assertEquals(List.of("The first page's"), texts(paragraphs(document.headersAndFooters().get(1))));
        assertTrue(paragraphs(document.body()).getFirst().endsSection());
        assertFalse(paragraphs(document.body()).get(1).endsSection());
    }

    @Test
    void footnotesWithoutTheirSeparators() throws IOException {
        WordDocument document = read(TestDocx.body("""
                <w:p><w:r><w:t>noted</w:t></w:r><w:r><w:rPr><w:rStyle w:val="FootnoteReference"/></w:rPr><w:footnoteReference w:id="1"/></w:r></w:p>
                """)
                .wordPart("rIdNotes", "footnotes", "footnotes.xml", "w:footnotes", """
                        <w:footnote w:type="separator" w:id="-1"><w:p><w:r><w:separator/></w:r></w:p></w:footnote>
                        <w:footnote w:type="continuationSeparator" w:id="0"><w:p><w:r><w:continuationSeparator/></w:r></w:p></w:footnote>
                        <w:footnote w:id="1"><w:p><w:r><w:footnoteRef/></w:r><w:r><w:t xml:space="preserve"> A note.</w:t></w:r></w:p></w:footnote>
                        """));

        assertEquals(1, document.notes().size());
        Story note = document.notes().getFirst();
        assertEquals(Story.Kind.FOOTNOTE, note.kind());
        assertEquals(Story.Name.note(Story.Kind.FOOTNOTE, 1), note.name());
        assertEquals(List.of(" A note."), texts(paragraphs(note)));
        assertEquals("noted", paragraphs(document.body()).getFirst().text());
    }

    @Test
    void theTemplateAsTheDocumentNamesIt() throws IOException {
        String share = "file:///\\\\server\\templates\\Corporate.dotm";
        WordDocument document = read(TestDocx.body("<w:p/>")
                .wordPart("rIdSettings", "settings", "settings.xml", "w:settings",
                        "<w:zoom w:percent=\"100\"/><w:attachedTemplate r:id=\"rIdTemplate\"/>")
                .link("word/settings.xml", "rIdTemplate", "attachedTemplate", share, true)
                .raw("docProps/app.xml", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"><Template>Corporate.dotm</Template><Pages>1</Pages></Properties>""")
                .link("", "rIdApp", "extended-properties", "docProps/app.xml", false));

        assertEquals(new Template(share, "Corporate.dotm"), document.template());
    }

    @Test
    void aDocumentWithoutTemplateStylesOrNotes() throws IOException {
        WordDocument document = read(TestDocx.body("<w:p><w:r><w:t>bare</w:t></w:r></w:p>"));

        assertEquals(Template.NONE, document.template());
        assertTrue(document.styles().styles().isEmpty());
        assertTrue(document.headersAndFooters().isEmpty());
        assertTrue(document.notes().isEmpty());
    }

    @Test
    void anEmptyTemplateNameIsNone() throws IOException {
        WordDocument document = read(TestDocx.body("<w:p/>")
                .raw("docProps/app.xml", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"><Template></Template></Properties>""")
                .link("", "rIdApp", "extended-properties", "docProps/app.xml", false));

        assertEquals(Template.NONE, document.template());
    }

    @Test
    void isoStrictDocuments() throws IOException {
        String strict = "xmlns:w=\"http://purl.oclc.org/ooxml/wordprocessingml/main\"";
        WordDocument document = read(TestDocx.body("")
                .withoutLinks("")
                .raw("word/document.xml", "<w:document " + strict + "><w:body><w:p><w:r><w:t>strict</w:t></w:r></w:p></w:body></w:document>")
                .link("", "rId1", "http://purl.oclc.org/ooxml/officeDocument/relationships/officeDocument", "word/document.xml", false));

        assertEquals("strict", paragraphs(document.body()).getFirst().text());
    }

    // ---- what is refused ---------------------------------------------------------------------------------------

    /** An entity defined outside the document: the parser would read another file, or reach the network for it. */
    @Test
    void anExternalEntityIsNeverRead() throws IOException {
        Path secret = Files.writeString(directory.resolve("secret.txt"), "SECRET-CONTENT", StandardCharsets.UTF_8);
        String document = """
                <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                <!DOCTYPE w:document [<!ENTITY secret SYSTEM "%s">]>
                <w:document %s><w:body><w:p><w:r><w:t>&secret;</w:t></w:r></w:p></w:body></w:document>
                """.formatted(secret.toUri(), TestDocx.NAMESPACES);

        DocxException refused = assertThrows(DocxException.class,
                () -> read(TestDocx.body("").raw("word/document.xml", document)));
        assertTrue(refused.getMessage().contains("DOCTYPE"), refused.getMessage());
        assertFalse(refused.getMessage().contains("SECRET-CONTENT"));
    }

    /** A DTD outside the document: never fetched, the part is refused first. */
    @Test
    void anExternalDtdIsNeverFetched() throws IOException {
        Path dtd = Files.writeString(directory.resolve("word.dtd"), "<!ENTITY secret \"SECRET-CONTENT\">");
        String document = """
                <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                <!DOCTYPE w:document SYSTEM "%s">
                <w:document %s><w:body><w:p><w:r><w:t>&secret;</w:t></w:r></w:p></w:body></w:document>
                """.formatted(dtd.toUri(), TestDocx.NAMESPACES);

        DocxException refused = assertThrows(DocxException.class,
                () -> read(TestDocx.body("").raw("word/document.xml", document)));
        assertFalse(refused.getMessage().contains("SECRET-CONTENT"));
    }

    @Test
    void aFileThatIsNoZip() throws IOException {
        Path file = Files.writeString(directory.resolve("notes.docx"), "plain text");

        DocxException refused = assertThrows(DocxException.class, () -> DocxReader.read(file));
        assertEquals("notes.docx is not a Word document (.docx): it is not a zip archive", refused.getMessage());
    }

    @Test
    void aZipThatIsNoWordDocument() throws IOException {
        Path file = TestDocx.body("").withoutLinks("").raw("word/document.xml", "<other/>")
                .raw("content.xml", "<x/>").write(directory);

        DocxException refused = assertThrows(DocxException.class, () -> DocxReader.read(file));
        assertTrue(refused.getMessage().contains("<other> where a Word document has <document>"), refused.getMessage());
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    /** The styles of a document as Word writes them, a localized style id ("Titre2") included. */
    private static final String STYLES = """
            <w:docDefaults>
              <w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/><w:sz w:val="22"/></w:rPr></w:rPrDefault>
              <w:pPrDefault><w:pPr><w:spacing w:after="160"/></w:pPr></w:pPrDefault>
            </w:docDefaults>
            <w:latentStyles w:count="376"><w:lsdException w:name="Normal" w:qFormat="1"/></w:latentStyles>
            <w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style>
            <w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/>
              <w:pPr><w:keepNext/><w:outlineLvl w:val="0"/></w:pPr><w:rPr><w:b/><w:sz w:val="32"/></w:rPr></w:style>
            <w:style w:type="paragraph" w:styleId="MyTitle"><w:name w:val="My Title"/><w:basedOn w:val="Heading1"/></w:style>
            <w:style w:type="paragraph" w:styleId="Titre2"><w:name w:val="heading 2"/><w:basedOn w:val="Normal"/></w:style>
            <w:style w:type="paragraph" w:styleId="ListBullet"><w:name w:val="List Bullet"/>
              <w:pPr><w:numPr><w:numId w:val="1"/></w:numPr></w:pPr></w:style>
            """;

    private WordDocument read(TestDocx docx) throws IOException {
        return DocxReader.read(docx.write(directory));
    }

    private static List<Paragraph> paragraphs(Story story) {
        return paragraphs(story.blocks());
    }

    private static List<Paragraph> paragraphs(List<Block> blocks) {
        return blocks.stream().filter(Paragraph.class::isInstance).map(Paragraph.class::cast).toList();
    }

    private static List<String> texts(List<Paragraph> paragraphs) {
        return paragraphs.stream().map(Paragraph::text).toList();
    }
}
