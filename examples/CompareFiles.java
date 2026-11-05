/*
 * Compares two files with doccomparator-core alone — no window — and prints what differs, in words.
 *
 *   java -cp doccomparator-core.jar examples/CompareFiles.java old.docx new.docx
 *   java -cp doccomparator-core.jar examples/CompareFiles.java budget-A.xlsx budget-B.xlsx --all
 *
 * Any format a reader knows: Word, Excel, PDF, text (and those another jar on the class path adds).
 */

import java.nio.file.Path;
import java.util.List;
import org.jtools.doccomparator.compare.CompareOptions;
import org.jtools.doccomparator.compare.Comparison;
import org.jtools.doccomparator.compare.DocumentComparer;
import org.jtools.doccomparator.compare.Pairing;
import org.jtools.doccomparator.compare.PropertyDifference;
import org.jtools.doccomparator.compare.StoryComparison;
import org.jtools.doccomparator.document.Document;
import org.jtools.doccomparator.document.Picture;
import org.jtools.doccomparator.i18n.I18n;
import org.jtools.doccomparator.i18n.I18nLanguage;
import org.jtools.doccomparator.reader.DocumentReadException;
import org.jtools.doccomparator.reader.DocumentReaders;
import org.jtools.doccomparator.report.ComparisonWords;

public class CompareFiles {

    public static void main(String[] args) throws Exception {
        I18n.use(I18nLanguage.ENGLISH);   // the words of the comparison, and the readers' messages

        // 1. Read each file with the reader of its extension (a Word document for an unknown one).
        Document left;
        Document right;
        try {
            left = read(Path.of(args[0]));
            right = read(Path.of(args[1]));
        } catch (DocumentReadException e) {
            System.err.println(e.getMessage());   // "notes.docx: neither a Word document nor ..."
            System.exit(1);
            return;
        }

        // 2. Compare: the text only by default, or every aspect (formatting, layout, tables...).
        CompareOptions options = args.length > 2 && args[2].equals("--all") ? CompareOptions.ALL
                : CompareOptions.TEXT_ONLY;
        Comparison comparison = DocumentComparer.compare(left, right, options);
        if (comparison.same()) {
            System.out.println("The same.");
            return;
        }

        // 3. Go through the differences, part by part (body, headers and footers, notes).
        for (StoryComparison story : comparison.stories()) {
            if (story.differences() == 0) {
                continue;
            }
            System.out.println("== " + ComparisonWords.part(story.part()) + ": "
                    + ComparisonWords.count(story.differences()));
            for (Pairing pairing : story.pairings()) {
                if (pairing.differs()) {
                    print(pairing);
                }
            }
        }
        for (PropertyDifference difference : comparison.documentDifferences()) {
            System.out.println("document: " + ComparisonWords.change(difference));   // styles, template...
        }
    }

    private static Document read(Path file) throws DocumentReadException {
        return DocumentReaders.forFile(file).read(file);
    }

    /** One paragraph or table that differs: what it was, what it is, and its properties that changed. */
    private static void print(Pairing pairing) {
        if (pairing.table() != null && pairing.left() != null && pairing.right() != null) {
            System.out.println("* a table:");   // a table in both: its cells, row by row and column by column
            for (List<Pairing> row : pairing.table().cells()) {
                for (Pairing cell : row) {
                    if (cell != null && cell.differs()) {
                        System.out.print("  ");
                        print(cell);
                    }
                }
            }
            return;
        }
        String before = pairing.left() == null ? "" : words(pairing.left().text());
        String after = pairing.right() == null ? "" : words(pairing.right().text());
        switch (pairing.status()) {
            case LEFT_ONLY -> System.out.println("- " + before);
            case RIGHT_ONLY -> System.out.println("+ " + after);
            case MOVED -> System.out.println("~ moved: " + before);
            default -> System.out.println("* " + before + "\n  → " + after);
        }
        if (pairing.diff() != null) {
            for (PropertyDifference difference : pairing.diff().properties()) {
                System.out.println("    " + ComparisonWords.change(difference));   // "bold: off → on"
            }
        }
    }

    /** A paragraph's text, a picture in it said so (Picture.IN_TEXT stands for it). */
    private static String words(String text) {
        return text.replace(String.valueOf(Picture.IN_TEXT), "[picture]");
    }
}
