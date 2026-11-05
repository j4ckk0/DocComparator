/*
 * Adds a format to doccomparator-core: CSV files, read as a table, so that they compare row by row and column by
 * column, a row inserted or moved found as such.
 *
 *   java -cp doccomparator-core.jar examples/CsvReader.java prices-2025.csv prices-2026.csv
 *
 * Here the reader is used directly. Put in a jar of its own, beside the libraries, it is found by DocumentReaders —
 * the comparison view's file chooser then offering "CSV files" — once declared as a service:
 *   - on the class path: a file META-INF/services/org.jtools.doccomparator.reader.DocumentReader holding its class name;
 *   - as a module: provides org.jtools.doccomparator.reader.DocumentReader with com.example.CsvReader;
 */

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jtools.doccomparator.compare.CompareOptions;
import org.jtools.doccomparator.compare.Comparison;
import org.jtools.doccomparator.compare.DocumentComparer;
import org.jtools.doccomparator.compare.Pairing;
import org.jtools.doccomparator.compare.TableComparison;
import org.jtools.doccomparator.document.Block;
import org.jtools.doccomparator.document.Document;
import org.jtools.doccomparator.document.Formatting;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.Run;
import org.jtools.doccomparator.document.Story;
import org.jtools.doccomparator.document.StyleSheet;
import org.jtools.doccomparator.document.Table;
import org.jtools.doccomparator.document.Template;
import org.jtools.doccomparator.reader.AbstractDocumentReader;

public class CsvReader extends AbstractDocumentReader {

    /** A public constructor without arguments: what ServiceLoader needs to make it. */
    public CsvReader() {
        super("csv");   // its extensions; after the built-in readers in the file chooser
    }

    @Override
    public String formatName() {
        return "CSV files";   // a real one would say it in the language in use, from its own texts (I18nTexts)
    }

    @Override
    public String readingNote() {
        return "CSV: a table, a row a line";
    }

    /** The file as one table; an IOException's message says why, and read() adds the file's name. */
    @Override
    protected Document readFile(Path file) throws IOException {
        List<Table.Row> rows = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            List<Table.Cell> cells = new ArrayList<>();
            for (String value : line.split(",", -1)) {
                cells.add(new Table.Cell(Formatting.NONE, List.of(paragraph(value.strip()))));
            }
            rows.add(new Table.Row(cells));
        }
        if (rows.isEmpty()) {
            throw new IOException("it holds no row");
        }
        List<Block> body = List.of(new Table(null, Formatting.NONE, rows));
        return new Document(String.valueOf(file.getFileName()), new Story(Story.Name.BODY, body), List.of(),
                List.of(), StyleSheet.NONE, Template.NONE, false);
    }

    private static Paragraph paragraph(String text) {
        List<Run> runs = text.isEmpty() ? List.of() : List.of(new Run(text, null, Formatting.NONE));
        return new Paragraph(null, Formatting.NONE, 0, null, false, runs);
    }

    /** Compares two CSV files and says which cells changed. */
    public static void main(String[] args) throws Exception {
        CsvReader reader = new CsvReader();
        Comparison comparison = DocumentComparer.compare(reader.read(Path.of(args[0])), reader.read(Path.of(args[1])),
                CompareOptions.TEXT_ONLY);
        for (Pairing pairing : comparison.stories().getFirst().pairings()) {
            TableComparison table = pairing.table();
            if (table == null) {
                continue;
            }
            for (List<Pairing> row : table.cells()) {
                for (Pairing cell : row) {
                    if (cell != null && cell.differs()) {
                        System.out.println(cell.status() + ": " + (cell.left() == null ? "" : cell.left().text())
                                + " → " + (cell.right() == null ? "" : cell.right().text()));
                    }
                }
            }
        }
        System.out.println(comparison.same() ? "The same." : "Done.");
    }
}
