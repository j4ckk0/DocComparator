package org.jtools.doccomparator.compare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jtools.doccomparator.document.Block;
import org.jtools.doccomparator.document.Formatting;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.Run;
import org.jtools.doccomparator.document.Story;
import org.jtools.doccomparator.document.StyleSheet;
import org.jtools.doccomparator.document.Table;
import org.jtools.doccomparator.document.Template;
import org.jtools.doccomparator.document.WordDocument;
import org.junit.jupiter.api.Test;

class TableComparerTest {

    private static final String[][] PARTS = {
            {"Part", "Reference", "Quantity"},
            {"Suction filter element", "KP-F-110", "1"},
            {"Cover seal", "KP-J-031", "1"},
            {"Hydraulic oil ISO VG 46", "KP-H-046", "2 litres"}};

    @Test
    void theSameTableIsTheSame() {
        Row row = compare(PARTS, PARTS);
        assertEquals(Row.Status.SAME, row.status());
        assertEquals(3, row.table().columns().size());
        assertEquals(4, row.table().rows().size());
    }

    @Test
    void rowsAddedAndRemovedHaveTheirOwnSlot() {
        String[][] edited = {PARTS[0], PARTS[1], {"Pressure gauge", "KP-M-200", "2"}, PARTS[3]};
        TableComparison table = compare(PARTS, edited).table();
        assertEquals(List.of(new Slot(0, 0, -1), new Slot(1, 1, -1), new Slot(2, -1, -1), new Slot(-1, 2, -1),
                new Slot(3, 3, -1)), table.rows());
        assertEquals(Row.Status.LEFT_ONLY, table.cell(2, 0).status());
        assertEquals(Row.Status.RIGHT_ONLY, table.cell(3, 0).status());
    }

    @Test
    void aMovedRowIsComparedWithItsMatch() {
        String[][] moved = {PARTS[0], PARTS[2], PARTS[3], {"Suction filter element", "KP-F-111", "1"}};
        Row row = compare(PARTS, moved);
        TableComparison table = row.table();
        Slot left = table.rows().get(1);
        assertEquals(1, left.left());
        assertTrue(left.moved());
        Slot right = table.rows().get(left.counterpart());
        assertEquals(3, right.right());
        assertEquals(Row.Status.CHANGED, table.cell(1, 1).status(), "KP-F-110 → KP-F-111");
        assertEquals("KP-F-110", table.cell(1, 1).left().text());
        assertNull(table.cell(1, 1).right(), "its match is on its own line");
        assertEquals(Row.Status.CHANGED, row.status());
    }

    @Test
    void columnsAddedRemovedAndMovedAreFound() {
        String[][] reordered = {
                {"Reference", "Part", "Supplier"},
                {"KP-F-110", "Suction filter element", "Keldar"},
                {"KP-J-031", "Cover seal", "Keldar"},
                {"KP-H-046", "Hydraulic oil ISO VG 46", "Oleo"}};
        Row row = compare(PARTS, reordered);
        TableComparison table = row.table();
        List<Slot> columns = table.columns();
        // Part and Reference swapped: one of them in place, the other moved; Quantity removed, Supplier added: not
        // the same column, though at the same place
        assertEquals(1, columns.stream().filter(slot -> slot.left() >= 0 && slot.right() >= 0).count());
        assertEquals(2, columns.stream().filter(Slot::moved).count());
        assertTrue(columns.contains(new Slot(2, -1, -1)), "Quantity");
        assertTrue(columns.contains(new Slot(-1, 2, -1)), "Supplier");
        assertTrue(row.diff().properties().isEmpty(), "as many rows and columns: " + row.diff().properties());
        assertTrue(table.layoutDiffers());
    }

    @Test
    void aColumnRewrittenUnderTheSameHeaderIsStillInPlace() {
        String[][] rewritten = {
                {"Part", "Reference", "Quantity"},
                {"Suction filter element", "KP-F-110", "12"},
                {"Cover seal", "KP-J-031", "40"},
                {"Hydraulic oil ISO VG 46", "KP-H-046", "8 drums"}};
        TableComparison table = compare(PARTS, rewritten).table();
        assertEquals(List.of(new Slot(0, 0, -1), new Slot(1, 1, -1), new Slot(2, 2, -1)), table.columns());
        assertEquals(Row.Status.SAME, table.cell(0, 2).status());
        assertEquals(Row.Status.CHANGED, table.cell(1, 2).status(), "1 → 12");
    }

    @Test
    void aMergedCellIsWhereItStartsOnTheGrid() {
        Table merged = new Table(null, Formatting.NONE, List.of(
                row(cell("Part"), cell("Reference"), cell("Quantity")),
                new Table.Row(List.of(new Table.Cell(new Formatting(Map.of("gridSpan", "2")), List.of(paragraph("Spare parts kit"))),
                        cell("1")))));
        Table plain = table(new String[][] {{"Part", "Reference", "Quantity"}, {"Spare parts kit", "", "1"}});
        TableComparison table = compare(merged, plain).table();
        assertEquals(3, table.columns().size());
        assertEquals("Spare parts kit", table.cell(1, 0).left().text());
        assertEquals(Row.Status.RIGHT_ONLY, table.cell(1, 1).status(), "covered on the left, empty on the right");
        assertEquals(Row.Status.SAME, table.cell(1, 2).status());
    }

    @Test
    void stylesAsideOnlyLayoutAndContentCount() {
        Table styled = new Table("TableGrid", new Formatting(Map.of("jc", "center")), table(PARTS).rows());
        Table plain = table(PARTS);
        assertEquals(Row.Status.SAME, compare(styled, plain, CompareOptions.DEFAULT.withStyles(false)).status());
    }

    @Test
    void aTableIsPairedWithATableOnly() {
        List<Row> rows = DocumentComparer.compare(document(paragraph("Part Reference Quantity"), table(PARTS)),
                document(table(PARTS)), CompareOptions.DEFAULT).stories().getFirst().rows();
        assertEquals(2, rows.size());
        assertEquals(Row.Status.LEFT_ONLY, rows.get(0).status());
        assertEquals(Row.Status.SAME, rows.get(1).status());
    }

    private static Row compare(String[][] left, String[][] right) {
        return compare(table(left), table(right));
    }

    private static Row compare(Table left, Table right) {
        return compare(left, right, CompareOptions.DEFAULT);
    }

    private static Row compare(Table left, Table right, CompareOptions options) {
        List<Row> rows = DocumentComparer.compare(document(left), document(right), options).stories().getFirst().rows();
        assertEquals(1, rows.size(), "the tables are paired");
        return rows.getFirst();
    }

    private static WordDocument document(Block... blocks) {
        return new WordDocument("test.docx", new Story(Story.Kind.BODY, "Body", List.of(blocks)), List.of(), List.of(),
                StyleSheet.NONE, Template.NONE, false);
    }

    static Table table(String[][] cells) {
        List<Table.Row> rows = new ArrayList<>();
        for (String[] row : cells) {
            List<Table.Cell> line = new ArrayList<>();
            for (String text : row) {
                line.add(cell(text));
            }
            rows.add(new Table.Row(line));
        }
        return new Table(null, Formatting.NONE, rows);
    }

    private static Table.Row row(Table.Cell... cells) {
        return new Table.Row(List.of(cells));
    }

    private static Table.Cell cell(String text) {
        return new Table.Cell(Formatting.NONE, List.of(paragraph(text)));
    }

    private static Paragraph paragraph(String text) {
        return new Paragraph(null, Formatting.NONE, 0, null, false, List.of(new Run(text, null, Formatting.NONE)));
    }
}
