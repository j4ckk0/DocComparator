package org.jtools.doccomparator.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.jtools.doccomparator.compare.CompareOptions;
import org.jtools.doccomparator.compare.Comparison;
import org.jtools.doccomparator.compare.DocumentComparer;
import org.jtools.doccomparator.docx.DocxReader;
import org.junit.jupiter.api.Test;

/** The lines of the view, on the samples: what is shown where. Drawn too, without a screen. */
class LinesTest {

    private static final Fonts FONTS = Fonts.of(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

    @Test
    void eachPartHasItsTitleCountingItsLinesThatDiffer() throws IOException {
        List<Line> lines = Lines.of(compare("procedure-revB.docx"), false, FONTS);
        List<String> titles = lines.stream().filter(Line::isTitle).map(line -> line.title).toList();
        assertEquals(4, titles.size(), titles.toString());
        assertTrue(titles.getFirst().startsWith("Document — 2 differences"));
        int body = indexOf(lines, "Body");
        int next = indexOf(lines, "Headers and footers");
        long differing = lines.subList(body, next).stream().filter(Line::differs).count();
        assertEquals("Body — " + differing + " differences", lines.get(body).title);
    }

    @Test
    void aMovedParagraphLinksToItsMatchBothWays() throws IOException {
        List<Line> lines = Lines.of(compare("procedure-revB.docx"), false, FONTS);
        int left = lineWith(lines, "Wear safety glasses", true);
        Cell cell = lines.get(left).left;
        assertTrue(cell.moved());
        Line match = lines.get(cell.counterpartLine);
        assertNull(match.left);
        assertEquals(left, match.right.counterpartLine);
        assertNotNull(cell.link(), "said");
    }

    @Test
    void paragraphsAloneShareLinesSideBySide() throws IOException {
        List<Line> lines = Lines.of(compare("training-handbook.docx"), false, FONTS);
        int line = lineWith(lines, "Document KP-MP-012", true);
        assertEquals("Trainer: J. Petit. Duration: half a day, on the bench, in groups of four.",
                lines.get(line).right.text.text());
        assertEquals(Cell.Look.ORPHAN, lines.get(line).look());
    }

    @Test
    void aTableHasALineForItsColumnsThenOneForEachRow() throws IOException {
        List<Line> lines = Lines.of(compare("training-handbook.docx"), false, FONTS);
        int header = lines.indexOf(lines.stream().filter(l -> l.left != null && l.left.columns != null).findFirst()
                .orElseThrow());
        Line columns = lines.get(header);
        assertEquals(3, columns.left.columns.size());
        assertEquals("C (removed)", columns.left.columns.get(2).text.text());
        assertNull(columns.right.columns.get(2), "the right side has not got it");
        assertEquals("Part", lines.get(header + 1).left.columns.get(0).text.text());
        assertNull(lines.get(header + 4).right, "the oil's row, removed");
        assertEquals("4", lines.get(header + 4).left.marker);
    }

    @Test
    void listItemsAreNumberedAsWordShowsThem() throws IOException {
        List<Line> lines = Lines.of(compare("training-handbook.docx"), false, FONTS);
        assertEquals("3.", lines.get(lineWith(lines, "Unscrew the cover of the filter", false)).right.marker);
        assertEquals("2.", lines.get(lineWith(lines, "Where do you record", false)).right.marker, "a list of its own");
        assertEquals("•", lines.get(lineWith(lines, "Replacement of the suction filter", true)).left.marker);
    }

    @Test
    void onlyDifferencesLeavesOutTheLinesThatAreTheSame() throws IOException {
        Comparison comparison = compare("procedure-revB.docx");
        List<Line> all = Lines.of(comparison, false, FONTS);
        List<Line> only = Lines.of(comparison, true, FONTS);
        assertTrue(only.size() < all.size());
        assertTrue(only.stream().allMatch(line -> line.isTitle() || line.differs()));
        assertEquals(all.stream().filter(Line::differs).count(), only.stream().filter(Line::differs).count());
        int left = lineWith(only, "Wear safety glasses", true);
        assertEquals(left, only.get(only.get(left).left.counterpartLine).right.counterpartLine, "links still right");
    }

    @Test
    void theViewIsDrawnWithoutAScreen() throws IOException {
        ComparisonView view = new ComparisonView();
        view.setLines(Lines.of(compare("training-handbook.docx"), false, view.fonts()), 3);
        view.setSize(1200, 10);
        view.setSize(1200, view.getPreferredSize().height);
        assertTrue(view.getHeight() > 1000);
        BufferedImage image = new BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        view.paint(g);
        g.dispose();
        assertFalse(view.lines().isEmpty());
        assertTrue(view.difference(true) > 3, "the next difference after the current line");
    }

    private static int indexOf(List<Line> lines, String title) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).isTitle() && lines.get(i).title.startsWith(title)) {
                return i;
            }
        }
        throw new AssertionError("no title " + title);
    }

    private static int lineWith(List<Line> lines, String start, boolean onLeft) {
        for (int i = 0; i < lines.size(); i++) {
            Cell cell = onLeft ? lines.get(i).left : lines.get(i).right;
            if (cell != null && cell.text != null && cell.text.text().startsWith(start)) {
                return i;
            }
        }
        throw new AssertionError("no line with " + start);
    }

    private static Comparison compare(String right) throws IOException {
        return DocumentComparer.compare(DocxReader.read(Path.of("samples", "procedure-revA.docx")),
                DocxReader.read(Path.of("samples", right)), CompareOptions.DEFAULT);
    }
}
