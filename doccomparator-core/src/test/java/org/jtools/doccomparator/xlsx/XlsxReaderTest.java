package org.jtools.doccomparator.xlsx;


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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jtools.doccomparator.compare.CompareOptions;
import org.jtools.doccomparator.compare.CompareOptions.Aspect;
import org.jtools.doccomparator.compare.Comparison;
import org.jtools.doccomparator.compare.DocumentComparer;
import org.jtools.doccomparator.compare.Pairing;
import org.jtools.doccomparator.compare.PropertyDifference;
import org.jtools.doccomparator.document.Block;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.Table;
import org.jtools.doccomparator.document.WordDocument;
import org.jtools.doccomparator.ooxml.OoxmlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A workbook read as a document: each sheet a heading and its table. */
class XlsxReaderTest {

    @TempDir
    Path folder;

    private static String row(int r, String... cells) {
        return "<row r=\"" + r + "\">" + String.join("", cells) + "</row>";
    }

    private static String cell(String reference, String attributes, String content) {
        return "<c r=\"" + reference + "\"" + attributes + ">" + content + "</c>";
    }

    private static String number(String reference, String value) {
        return cell(reference, "", "<v>" + value + "</v>");
    }

    private static String shared(String reference, int index) {
        return cell(reference, " t=\"s\"", "<v>" + index + "</v>");
    }

    private WordDocument read(TestXlsx workbook) throws IOException {
        return new XlsxReader().read(workbook.write(folder, "book" + System.nanoTime() + ".xlsx"));
    }

    private static Table table(WordDocument document, int sheet) {
        int tables = 0;
        for (Block block : document.body().blocks()) {
            if (block instanceof Table table && tables++ == sheet) {
                return table;
            }
        }
        throw new AssertionError("no table " + sheet);
    }

    private static List<List<String>> texts(Table table) {
        List<List<String>> rows = new ArrayList<>();
        for (Table.Row row : table.rows()) {
            rows.add(row.cells().stream().map(c -> ((Paragraph) c.blocks().getFirst()).text()).toList());
        }
        return rows;
    }

    private static Table.Cell at(Table table, int row, int column) {
        return table.rows().get(row).cells().get(column);
    }

    @Test
    void workbooksByTheirExtension() {
        assertTrue(new XlsxReader().reads(Path.of("Budget.XLSX")));
        assertTrue(new XlsxReader().reads(Path.of("macros.xlsm")));
        assertFalse(new XlsxReader().reads(Path.of("old.xls")), "Excel 97's format is not a zip of XML");
        assertFalse(new XlsxReader().reads(Path.of("a.docx")));
    }

    @Test
    void eachSheetAHeadingThenItsTableEveryKindOfValue() throws IOException {
        WordDocument document = read(new TestXlsx()
                .strings("Name", "Total")
                .sheet("Data", "<sheetData>"
                        + row(1, shared("A1", 0), number("B1", "3.5"))
                        + row(2, cell("A2", " t=\"inlineStr\"", "<is><t>inline</t></is>"),
                                cell("B2", " t=\"b\"", "<v>1</v>"), cell("C2", " t=\"e\"", "<v>#DIV/0!</v>"))
                        + row(4, shared("A4", 1), cell("B4", " t=\"str\"", "<f>A1&amp;\"!\"</f><v>Name!</v>"))
                        + "</sheetData>")
                .sheet("Empty", "<sheetData/>"));
        List<Block> blocks = document.body().blocks();
        assertEquals("Data", ((Paragraph) blocks.get(0)).text());
        assertEquals(1, ((Paragraph) blocks.get(0)).headingLevel());
        assertInstanceOf(Table.class, blocks.get(1));
        assertEquals("Empty", ((Paragraph) blocks.get(2)).text(), "an empty sheet: its name alone");
        assertEquals(3, blocks.size());
        assertEquals(List.of(List.of("Name", "3.5", ""), List.of("inline", "TRUE", "#DIV/0!"), List.of("", "", ""),
                List.of("Total", "Name!", "")), texts(table(document, 0)), "the empty row 3 kept, every row as wide");
        assertEquals("A1&\"!\"", at(table(document, 0), 3, 1).formatting().get("formula"));
        assertEquals("right", ((Paragraph) at(table(document, 0), 0, 1).blocks().getFirst()).formatting().get("jc"),
                "a number to the right, as Excel puts it");
    }

    @Test
    void theTableIsTheRectangleOfWhatCellsHold() throws IOException {
        WordDocument document = read(new TestXlsx().sheet("S", "<sheetData>"
                + row(3, number("C3", "1"), number("D3", "2"))
                + row(5, number("D5", "3"))
                + row(40, cell("H40", " s=\"0\"", ""))   // formatted, empty: no part of it
                + "</sheetData>"));
        assertEquals(List.of(List.of("1", "2"), List.of("", ""), List.of("", "3")), texts(table(document, 0)),
                "from C3 to D5: no row number, no column letter");
    }

    @Test
    void sharedFormulasAreShiftedFromTheirFirstCell() throws IOException {
        WordDocument document = read(new TestXlsx().sheet("S", "<sheetData>"
                + row(1, number("A1", "10"), cell("B1", "", "<f t=\"shared\" si=\"0\" ref=\"B1:B3\">A1*2+$A$1</f>"
                        + "<v>30</v>"))
                + row(2, number("A2", "20"), cell("B2", "", "<f t=\"shared\" si=\"0\"/><v>50</v>"))
                + row(3, number("A3", "30"), cell("B3", "", "<f t=\"shared\" si=\"0\"/><v>70</v>"))
                + "</sheetData>"));
        Table table = table(document, 0);
        assertEquals("A1*2+$A$1", at(table, 0, 1).formatting().get("formula"));
        assertEquals("A2*2+$A$1", at(table, 1, 1).formatting().get("formula"));
        assertEquals("A3*2+$A$1", at(table, 2, 1).formatting().get("formula"));
        assertNull(at(table, 2, 0).formatting().get("formula"));
        assertEquals("70", ((Paragraph) at(table, 2, 1).blocks().getFirst()).text(), "its value as computed");
    }

    @Test
    void mergedCellsSpanAsWordsDo() throws IOException {
        WordDocument document = read(new TestXlsx().sheet("S", "<sheetData>"
                + row(1, number("A1", "1"), number("D1", "4"))
                + row(2, number("A2", "5"), number("B2", "6"), number("D2", "8"))
                + row(3, number("B3", "9"), number("D3", "11"))
                + "</sheetData><mergeCells><mergeCell ref=\"A1:C1\"/><mergeCell ref=\"A2:A3\"/></mergeCells>"));
        Table table = table(document, 0);
        assertEquals(2, table.rows().get(0).cells().size(), "A1 across three columns, then D1");
        assertEquals("3", at(table, 0, 0).formatting().get("gridSpan"));
        assertEquals("restart", at(table, 1, 0).formatting().get("vMerge"));
        assertEquals("continue", at(table, 2, 0).formatting().get("vMerge"));
        assertEquals("", ((Paragraph) at(table, 2, 0).blocks().getFirst()).text());
        Comparison same = DocumentComparer.compare(document, document, CompareOptions.ALL);
        assertTrue(same.stories().getFirst().pairings().stream().noneMatch(Pairing::differs), "laid out as a table");
    }

    private static final String STYLES = "<numFmts><numFmt numFmtId=\"164\" formatCode=\"0.0%\"/></numFmts>"
            + "<fonts><font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
            + "<font><b/><i val=\"0\"/><u/><sz val=\"14\"/><color rgb=\"FFFF0000\"/><name val=\"Arial\"/></font>"
            + "<font><sz val=\"11\"/><color theme=\"4\" tint=\"-0.25\"/><name val=\"Calibri\"/></font></fonts>"
            + "<fills><fill><patternFill patternType=\"none\"/></fill>"
            + "<fill><patternFill patternType=\"gray125\"/></fill>"
            + "<fill><patternFill patternType=\"solid\"><fgColor indexed=\"13\"/><bgColor indexed=\"64\"/>"
            + "</patternFill></fill></fills>"
            + "<borders><border/><border><left style=\"thin\"><color auto=\"1\"/></left><bottom style=\"double\">"
            + "<color rgb=\"FF0000FF\"/></bottom></border></borders>"
            + "<cellXfs><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/>"
            + "<xf numFmtId=\"164\" fontId=\"1\" fillId=\"2\" borderId=\"1\"><alignment horizontal=\"center\" "
            + "vertical=\"top\" wrapText=\"1\"/></xf>"
            + "<xf numFmtId=\"14\" fontId=\"2\" fillId=\"0\" borderId=\"0\"><alignment vertical=\"bottom\"/></xf>"
            + "</cellXfs>";

    private static final String THEME = "<a:dk1><a:sysClr val=\"windowText\" lastClr=\"000000\"/></a:dk1>"
            + "<a:lt1><a:sysClr val=\"window\" lastClr=\"FFFFFF\"/></a:lt1><a:dk2><a:srgbClr val=\"44546A\"/></a:dk2>"
            + "<a:lt2><a:srgbClr val=\"E7E6E6\"/></a:lt2><a:accent1><a:srgbClr val=\"4472C4\"/></a:accent1>";

    @Test
    void aCellsLookAsWordsProperties() throws IOException {
        WordDocument document = read(new TestXlsx().styles(STYLES).theme(THEME).sheet("S",
                "<cols><col min=\"1\" max=\"2\" width=\"10\" customWidth=\"1\"/></cols><sheetData>"
                        + "<row r=\"1\" ht=\"30\" customHeight=\"1\">" + cell("A1", " s=\"1\"", "<v>0.125</v>")
                        + cell("B1", " s=\"2\"", "<v>46331</v>") + "</row>"
                        + "<row r=\"2\" hidden=\"1\">" + number("A2", "1") + "</row></sheetData>"));
        Table table = table(document, 0);
        Table.Cell styled = at(table, 0, 0);
        Paragraph paragraph = (Paragraph) styled.blocks().getFirst();
        assertEquals("12.5%", paragraph.text());
        assertEquals("0.0%", styled.formatting().get("numFmt"));
        assertEquals("clear", styled.formatting().get("shd.val"));
        assertEquals("FFFF00", styled.formatting().get("shd.fill"), "indexed colour 13 of the legacy palette");
        assertEquals("single", styled.formatting().get("tcBorders.left.val"));
        assertEquals("4", styled.formatting().get("tcBorders.left.sz"));
        assertEquals("auto", styled.formatting().get("tcBorders.left.color"));
        assertEquals("double", styled.formatting().get("tcBorders.bottom.val"));
        assertEquals("0000FF", styled.formatting().get("tcBorders.bottom.color"), "the alpha byte dropped");
        assertEquals("top", styled.formatting().get("vAlign"));
        assertEquals("true", styled.formatting().get("wrapText"));
        assertEquals("1050", styled.formatting().get("tcW.w"), "10 characters, 70 pixels, in twips");
        assertEquals("center", paragraph.formatting().get("jc"), "its own alignment, not a number's");
        var font = paragraph.runs().getFirst().formatting();
        assertEquals("true", font.get("b"));
        assertNull(font.get("i"), "i val=0: off");
        assertEquals("single", font.get("u"));
        assertEquals("28", font.get("sz"), "in half-points");
        assertEquals("FF0000", font.get("color"));
        assertEquals("Arial", font.get("rFonts.ascii"));
        Table.Cell dated = at(table, 0, 1);
        assertEquals("2026-11-05", ((Paragraph) dated.blocks().getFirst()).text());
        assertNull(dated.formatting().get("vAlign"), "bottom: Excel's default, as none");
        assertEquals("2F5496", ((Paragraph) dated.blocks().getFirst()).runs().getFirst().formatting().get("color"),
                "theme 4 (accent 1), 25% darker");
        assertEquals("600", table.rows().get(0).formatting().get("trHeight.val"), "30 points in twips");
        assertEquals("true", table.rows().get(1).formatting().get("hidden"));
    }

    @Test
    void aRichStringKeepsItsRunsFonts() throws IOException {
        WordDocument document = read(new TestXlsx()
                .richString("<r><t>plain </t></r><r><rPr><b/><rFont val=\"Arial\"/></rPr><t>bold</t></r>"
                        + "<rPh sb=\"0\" eb=\"1\"><t>yomi</t></rPh>")
                .sheet("S", "<sheetData>" + row(1, shared("A1", 0)) + "</sheetData>"));
        Paragraph paragraph = (Paragraph) at(table(document, 0), 0, 0).blocks().getFirst();
        assertEquals("plain bold", paragraph.text(), "the phonetic guide left out");
        assertEquals("true", paragraph.runs().get(1).formatting().get("b"));
        assertNull(paragraph.runs().get(0).formatting().get("b"));
    }

    @Test
    void datesFrom1904WhenTheWorkbookSaysSo() throws IOException {
        WordDocument document = read(new TestXlsx().date1904().styles(STYLES)
                .sheet("S", "<sheetData>" + row(1, cell("A1", " s=\"2\"", "<v>0</v>")) + "</sheetData>"));
        assertEquals("1904-01-01", ((Paragraph) at(table(document, 0), 0, 0).blocks().getFirst()).text());
    }

    @Test
    void aFormulaChangedIsACellsDifferenceItsValueATextOne() throws IOException {
        WordDocument before = read(new TestXlsx().sheet("S", "<sheetData>"
                + row(1, number("A1", "2"), cell("B1", "", "<f>A1*2</f><v>4</v>"), number("C1", "7"))
                + row(2, number("A2", "3"), number("B2", "5"), number("C2", "9"))
                + "</sheetData>"));
        WordDocument after = read(new TestXlsx().sheet("S", "<sheetData>"
                + row(1, number("A1", "2"), cell("B1", "", "<f>A1+A1</f><v>4</v>"), number("C1", "8"))
                + row(2, number("A2", "3"), number("B2", "5"), number("C2", "9"))
                + "</sheetData>"));
        Pairing table = DocumentComparer.compare(before, after, CompareOptions.ALL).stories().getFirst().pairings()
                .stream().filter(p -> p.table() != null).findFirst().orElseThrow();
        Pairing formula = table.table().cell(0, 1);
        assertEquals(Pairing.Status.OTHERWISE, formula.status(), "the same value, another formula");
        PropertyDifference difference = formula.diff().properties().getFirst();
        assertEquals(Aspect.CELLS, difference.aspect());
        assertEquals("compared.ofCell(formula): A1*2 → A1+A1", difference.toString());
        assertEquals(Pairing.Status.CHANGED, table.table().cell(0, 2).status(), "7 → 8, its column paired by 9");
        Pairing textOnly = DocumentComparer.compare(before, after, CompareOptions.TEXT_ONLY).stories().getFirst()
                .pairings().stream().filter(p -> p.table() != null).findFirst().orElseThrow();
        assertEquals(Pairing.Status.SAME, textOnly.table().cell(0, 1).status(), "formulas compared with Cells only");
    }

    @Test
    void aSheetTooLargeIsRefusedNamed() throws IOException {
        Path file = new TestXlsx().sheet("Huge", "<sheetData>" + row(1, number("A1", "1"))
                + row(1_000_000, number("C1000000", "2")) + "</sheetData>").write(folder, "huge.xlsx");
        OoxmlException refused = assertThrows(OoxmlException.class, () -> new XlsxReader().read(file));
        assertTrue(refused.getMessage().contains("too many cells to compare: sheet Huge takes the workbook beyond "
                + "250000"), refused.getMessage());
    }

    @Test
    void theLimitIsTheWholeWorkbooks() throws IOException {
        String corners = "<sheetData>" + row(1, number("A1", "1")) + row(1000, number("CV1000", "2"))
                + "</sheetData>";   // 1,000 rows of 100 columns: 100,000 cells
        Path two = new TestXlsx().sheet("One", corners).sheet("Two", corners).write(folder, "two.xlsx");
        assertEquals(4, new XlsxReader().read(two).body().blocks().size(), "200,000 cells: read");
        Path three = new TestXlsx().sheet("One", corners).sheet("Two", corners).sheet("Three", corners)
                .write(folder, "three.xlsx");
        OoxmlException refused = assertThrows(OoxmlException.class, () -> new XlsxReader().read(three));
        assertTrue(refused.getMessage().contains("sheet Three takes the workbook beyond 250000"), refused.getMessage());
    }

    @Test
    void notAZipIsRefusedSaidAsSuch() throws IOException {
        Path file = Files.writeString(folder.resolve("notes.xlsx"), "not a workbook");
        OoxmlException refused = assertThrows(OoxmlException.class, () -> new XlsxReader().read(file));
        assertEquals("notes.xlsx is neither a Word document nor an Excel workbook: it is not a zip archive",
                refused.getMessage());
    }
}
