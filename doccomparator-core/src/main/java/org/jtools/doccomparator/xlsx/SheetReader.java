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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.jtools.doccomparator.document.Formatting;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.Run;
import org.jtools.doccomparator.document.Table;
import org.jtools.doccomparator.i18n.I18n;
import org.jtools.doccomparator.ooxml.OoxmlException;
import org.jtools.doccomparator.ooxml.XmlCursor;

/**
 * A worksheet (xl/worksheets/sheetN.xml) as a table, so that its rows and columns compare as a Word table's do: the
 * rectangle of its cells that hold a value or a formula, empty cells included, each cell's text its value as Excel
 * shows it. Row numbers and column letters are not cells: one row inserted would make every row after it differ.
 */
final class SheetReader {

    /**
     * The most cells a workbook's tables may hold together: a comparison takes about 3.5 KB of memory a cell on each
     * side (measured: 300,000 cells, 2 GB), and Java takes a quarter of the machine's memory, 2 GB of 8.
     */
    static final long LARGEST_WORKBOOK = 250_000;

    /** A rich text's run: its text and its own font, null when it has none (the cell's font then). */
    record RichRun(String text, Map<String, String> font) {
    }

    /** A cell read: what it shows, its style, its formula, and where Excel aligns it when its style does not say. */
    private record Cell(List<RichRun> value, int style, String formula, String alignment) {
        boolean holdsSomething() {
            return formula != null || value.stream().anyMatch(run -> !run.text().isEmpty());
        }
    }

    private record SharedFormula(String formula, int row, int column) {
    }

    private final List<List<RichRun>> strings;
    private final Styles styles;
    private final Colors colors;
    private final boolean date1904;
    private final long budget;
    private String sheetName;
    private String part;
    private long holding;

    private final SortedMap<Integer, SortedMap<Integer, Cell>> rows = new TreeMap<>();
    private final Map<Integer, Map<String, String>> rowProperties = new HashMap<>();
    private final Map<Integer, Double> widths = new HashMap<>();
    private final Map<String, SharedFormula> shared = new HashMap<>();
    private final List<int[]> merges = new ArrayList<>();   // first row, first column, last row, last column

    /** A reader of a sheet whose table may hold up to budget cells: what the workbook's other sheets leave. */
    SheetReader(List<List<RichRun>> strings, Styles styles, Colors colors, boolean date1904, long budget) {
        this.strings = strings;
        this.styles = styles;
        this.colors = colors;
        this.date1904 = date1904;
        this.budget = budget;
    }

    /** The sheet's table; null when no cell holds anything. Refused past the budget, as soon as it is known. */
    Table read(XmlCursor xml, String sheetName) throws OoxmlException {
        this.sheetName = sheetName;
        this.part = xml.part();
        xml.root("worksheet");
        while (xml.nextChild()) {
            if (xml.isSpreadsheet("cols")) {
                xml.forChildren(this::readColumns);
            } else if (xml.isSpreadsheet("sheetData")) {
                int[] lastRow = {0};
                xml.forChildren(row -> lastRow[0] = readRow(row, lastRow[0]));
            } else if (xml.isSpreadsheet("mergeCells")) {
                xml.forChildren(merge -> {
                    String[] corners = String.valueOf(merge.plainAttribute("ref")).split(":");
                    int[] from = Formulas.cell(corners[0]);
                    int[] to = corners.length > 1 ? Formulas.cell(corners[1]) : from;
                    if (from != null && to != null) {
                        merges.add(new int[] {from[0], from[1], to[0], to[1]});
                    }
                    merge.skip();
                });
            } else {
                xml.skip();
            }
        }
        return table();
    }

    private OoxmlException tooLarge() {
        return new OoxmlException(part, I18n.text("error.sheetTooLarge", sheetName, LARGEST_WORKBOOK));
    }

    /** cols' col: the width of the columns from min to max, in characters of the workbook's font. */
    private void readColumns(XmlCursor col) throws OoxmlException {
        Integer min = Styles.number(col.plainAttribute("min"));
        Integer max = Styles.number(col.plainAttribute("max"));
        String width = col.plainAttribute("width");
        if (min != null && max != null && width != null && max - min < 16_384) {
            try {
                double characters = Double.parseDouble(width);
                for (int c = min; c <= max; c++) {
                    widths.put(c, characters);
                }
            } catch (NumberFormatException _) {
                // a width Excel would not write: the column's default
            }
        }
        col.skip();
    }

    /** A row and its cells; the number of the row, its own or the one after the previous. */
    private int readRow(XmlCursor row, int previous) throws OoxmlException {
        Integer number = Styles.number(row.plainAttribute("r"));
        int r = number == null ? previous + 1 : number;
        Map<String, String> properties = new TreeMap<>();
        String height = row.plainAttribute("ht");
        if (height != null && "1".equals(row.plainAttribute("customHeight"))) {
            try {
                properties.put("trHeight.val", String.valueOf(Math.round(Double.parseDouble(height) * 20)));   // twips
                properties.put("trHeight.hRule", "exact");
            } catch (NumberFormatException _) {
                // a height Excel would not write: the row's default
            }
        }
        if ("1".equals(row.plainAttribute("hidden")) || "true".equals(row.plainAttribute("hidden"))) {
            properties.put("hidden", "true");
        }
        if (!properties.isEmpty()) {
            rowProperties.put(r, properties);
        }
        int[] lastColumn = {0};
        row.forChildren(c -> lastColumn[0] = readCell(c, r, lastColumn[0]));
        return r;
    }

    /** A cell: its value as text, its style, its formula; the number of its column. */
    private int readCell(XmlCursor c, int row, int previous) throws OoxmlException {
        if (!c.isSpreadsheet("c")) {
            c.skip();
            return previous;
        }
        int[] at = c.plainAttribute("r") == null ? null : Formulas.cell(c.plainAttribute("r"));
        int column = at == null ? previous + 1 : at[1];
        String type = c.plainAttribute("t");
        Integer styleIndex = Styles.number(c.plainAttribute("s"));
        int style = styleIndex == null ? 0 : styleIndex;
        String value = null;
        String formula = null;
        List<RichRun> inline = null;
        while (c.nextChild()) {
            if (c.isSpreadsheet("v")) {
                value = c.text();
            } else if (c.isSpreadsheet("f")) {
                formula = formula(c, row, column);
            } else if (c.isSpreadsheet("is")) {
                inline = richText(c, colors);
            } else {
                c.skip();
            }
        }
        List<RichRun> shown = value(type, value, inline, style);
        if ((formula != null || shown.stream().anyMatch(run -> !run.text().isEmpty())) && ++holding > budget) {
            throw tooLarge();   // the table holds at least these cells: no need to read on
        }
        String kind = type == null ? "n" : type;
        String alignment = switch (kind) {
            case "n", "d" -> value == null || value.isEmpty() ? null : "right";   // numbers and dates to the right
            case "b", "e" -> "center";   // TRUE, FALSE, #DIV/0! centred
            default -> null;
        };
        rows.computeIfAbsent(row, _ -> new TreeMap<>()).put(column, new Cell(shown, style, formula, alignment));
        return column;
    }

    /** A cell's f: its formula; a shared one's, shifted from the cell that writes it; consumed. */
    private String formula(XmlCursor f, int row, int column) throws OoxmlException {
        String kind = f.plainAttribute("t");
        String index = f.plainAttribute("si");
        String text = f.text();
        if ("shared".equals(kind) && index != null) {
            if (!text.isEmpty()) {
                shared.put(index, new SharedFormula(text, row, column));
                return text;
            }
            SharedFormula first = shared.get(index);
            return first == null ? null : Formulas.shift(first.formula(), row - first.row(), column - first.column());
        }
        return text.isEmpty() ? null : text;
    }

    /** What the cell shows: a shared or inline string, a boolean, an error, a number through its format. */
    private List<RichRun> value(String type, String value, List<RichRun> inline, int style) {
        String kind = type == null ? "n" : type;
        return switch (kind) {
            case "s" -> {
                Integer index = Styles.number(value);
                yield index != null && index >= 0 && index < strings.size() ? strings.get(index) : plain("");
            }
            case "inlineStr" -> inline == null ? plain("") : inline;
            case "b" -> plain(value == null ? "" : value.equals("1") ? "TRUE" : "FALSE");
            case "str", "e" -> plain(value == null ? "" : value);
            case "d" -> plain(value == null ? "" : value.replace("T00:00:00", "").replace('T', ' '));
            default -> {
                if (value == null || value.isEmpty()) {
                    yield plain("");
                }
                try {
                    yield plain(NumberFormats.format(Double.parseDouble(value), styles.of(style).numberFormat(),
                            date1904));
                } catch (NumberFormatException _) {
                    yield plain(value);
                }
            }
        };
    }

    private static List<RichRun> plain(String text) {
        return List.of(new RichRun(text, null));
    }

    /** A rich text (a shared string's si, an inline string's is): its runs, phonetic guides left out; consumed. */
    static List<RichRun> richText(XmlCursor xml, Colors colors) throws OoxmlException {
        List<RichRun> runs = new ArrayList<>();
        while (xml.nextChild()) {
            if (xml.isSpreadsheet("t")) {
                runs.add(new RichRun(xml.text(), null));
            } else if (xml.isSpreadsheet("r")) {
                Map<String, String> font = null;
                StringBuilder text = new StringBuilder();
                while (xml.nextChild()) {
                    if (xml.isSpreadsheet("rPr")) {
                        font = Styles.font(xml, colors);
                    } else if (xml.isSpreadsheet("t")) {
                        text.append(xml.text());
                    } else {
                        xml.skip();
                    }
                }
                runs.add(new RichRun(text.toString(), font));
            } else {
                xml.skip();   // rPh, phoneticPr: a reading aid, not the text
            }
        }
        return runs.isEmpty() ? plain("") : List.copyOf(runs);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The table

    private Table table() throws OoxmlException {
        int firstRow = Integer.MAX_VALUE;
        int lastRow = 0;
        int firstColumn = Integer.MAX_VALUE;
        int lastColumn = 0;
        for (Map.Entry<Integer, SortedMap<Integer, Cell>> row : rows.entrySet()) {
            for (Map.Entry<Integer, Cell> cell : row.getValue().entrySet()) {
                if (cell.getValue().holdsSomething()) {
                    firstRow = Math.min(firstRow, row.getKey());
                    lastRow = Math.max(lastRow, row.getKey());
                    firstColumn = Math.min(firstColumn, cell.getKey());
                    lastColumn = Math.max(lastColumn, cell.getKey());
                }
            }
        }
        if (lastRow == 0) {
            return null;
        }
        if ((long) (lastRow - firstRow + 1) * (lastColumn - firstColumn + 1) > budget) {
            throw tooLarge();
        }
        // merged areas, clipped to the table: their first cell spans, the others are covered
        Map<Long, int[]> mergeStarts = new HashMap<>();
        // 0: spanned from the left; else the first column of a merge continued down, as wide as the merge
        Map<Long, Integer> covered = new HashMap<>();
        for (int[] merge : merges) {
            int top = Math.max(merge[0], firstRow);
            int left = Math.max(merge[1], firstColumn);
            int bottom = Math.min(merge[2], lastRow);
            int right = Math.min(merge[3], lastColumn);
            if (top > bottom || left > right || top != merge[0] || left != merge[1]) {
                continue;   // outside the table, or its first cell is: as separate cells
            }
            mergeStarts.put(key(top, left), new int[] {bottom - top + 1, right - left + 1});
            for (int r = top; r <= bottom; r++) {
                for (int c = left; c <= right; c++) {
                    if (r != top || c != left) {
                        covered.put(key(r, c), c == left ? right - left + 1 : 0);
                    }
                }
            }
        }
        List<Table.Row> tableRows = new ArrayList<>();
        for (int r = firstRow; r <= lastRow; r++) {
            SortedMap<Integer, Cell> cells = rows.getOrDefault(r, new TreeMap<>());
            List<Table.Cell> rowCells = new ArrayList<>();
            for (int c = firstColumn; c <= lastColumn; c++) {
                Integer continuing = covered.get(key(r, c));
                if (continuing != null && continuing == 0) {
                    continue;   // covered by a cell to its left, which spans it
                }
                int[] span = mergeStarts.get(key(r, c));
                int across = continuing != null ? continuing : span != null ? span[1] : 1;
                rowCells.add(cell(cells.get(c), c, across, span != null && span[0] > 1, continuing != null));
            }
            Map<String, String> properties = rowProperties.getOrDefault(r, Map.of());
            tableRows.add(new Table.Row(new Formatting(properties), rowCells));
        }
        return new Table(null, Formatting.NONE, tableRows);
    }

    private static long key(int row, int column) {
        return (long) row << 20 | column;
    }

    /** A table cell: one paragraph of the value's runs, the cell's look and formula, its width, its span. */
    private Table.Cell cell(Cell cell, int column, int across, boolean mergesDown, boolean continuesMerge) {
        Styles.CellStyle style = styles.of(cell == null ? 0 : cell.style());
        SortedMap<String, String> properties = new TreeMap<>(style.cell());
        if (cell != null && cell.formula() != null) {
            properties.put("formula", cell.formula());
        }
        double width = 0;
        for (int c = column; c < column + across; c++) {
            width += widths.getOrDefault(c, 0.0);
        }
        if (width > 0) {
            properties.put("tcW.w", String.valueOf(Math.round(width * 7) * 15));   // characters → pixels → twips
            properties.put("tcW.type", "dxa");
        }
        if (across > 1) {
            properties.put("gridSpan", String.valueOf(across));
        }
        if (mergesDown) {
            properties.put("vMerge", "restart");
        } else if (continuesMerge) {
            properties.put("vMerge", "continue");
        }
        List<Run> runs = new ArrayList<>();
        if (cell != null && !continuesMerge) {
            for (RichRun rich : cell.value()) {
                if (rich.text().isEmpty()) {
                    continue;
                }
                Map<String, String> font = new TreeMap<>(style.run());
                if (rich.font() != null) {
                    font.putAll(rich.font());
                }
                runs.add(new Run(rich.text(), null, new Formatting(font)));
            }
        }
        Map<String, String> paragraphProperties = new TreeMap<>(style.paragraph());
        if (cell != null && cell.alignment() != null && !continuesMerge) {
            paragraphProperties.putIfAbsent("jc", cell.alignment());
        }
        Paragraph paragraph = new Paragraph(null, new Formatting(paragraphProperties), 0, null, false, runs);
        return new Table.Cell(new Formatting(properties), List.of(paragraph));
    }
}
