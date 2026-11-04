package org.jtools.doccomparator.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.jtools.doccomparator.compare.Span;
import org.jtools.doccomparator.docx.DocxReader;
import org.jtools.doccomparator.ui.Lines.Shown;
import org.junit.jupiter.api.Test;

/** The lines of the view, on the samples: what is shown where. Drawn too, without a screen. */
class LinesTest {

    private static final Fonts FONTS = Fonts.of(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

    @Test
    void eachPartHasItsTitleCountingItsLinesThatDiffer() throws IOException {
        List<Line> lines = Lines.of(compare("procedure-revB.docx"), Shown.ALL, FONTS, Palette.LIGHT);
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
        List<Line> lines = Lines.of(compare("procedure-revB.docx"), Shown.ALL, FONTS, Palette.LIGHT);
        int left = lineWith(lines, "Wear safety glasses", true);
        LineSide side = lines.get(left).left;
        assertTrue(side.moved());
        Line match = lines.get(side.counterpartLine);
        assertNull(match.left);
        assertEquals(left, match.right.counterpartLine);
        assertNotNull(side.link(), "said");
    }

    @Test
    void paragraphsAloneShareLinesSideBySide() throws IOException {
        List<Line> lines = Lines.of(compare("training-handbook.docx"), Shown.ALL, FONTS, Palette.LIGHT);
        int line = lineWith(lines, "Document KP-MP-012", true);
        assertEquals("Trainer: J. Petit. Duration: half a day, on the bench, in groups of four.",
                lines.get(line).right.text.text());
        assertEquals(LineSide.Marking.ORPHAN, lines.get(line).marking());
    }

    @Test
    void aTableHasALineForItsColumnsThenOneForEachRow() throws IOException {
        List<Line> lines = Lines.of(compare("training-handbook.docx"), Shown.ALL, FONTS, Palette.LIGHT);
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
        List<Line> lines = Lines.of(compare("training-handbook.docx"), Shown.ALL, FONTS, Palette.LIGHT);
        assertEquals("3.", lines.get(lineWith(lines, "Unscrew the cover of the filter", false)).right.marker);
        assertEquals("2.", lines.get(lineWith(lines, "Where do you record", false)).right.marker, "a list of its own");
        assertEquals("•", lines.get(lineWith(lines, "Replacement of the suction filter", true)).left.marker);
    }

    @Test
    void onlyDifferencesLeavesOutTheLinesThatAreTheSame() throws IOException {
        Comparison comparison = compare("procedure-revB.docx");
        List<Line> all = Lines.of(comparison, Shown.ALL, FONTS, Palette.LIGHT);
        List<Line> only = Lines.of(comparison, Shown.ALL.withOnlyDifferences(true), FONTS, Palette.LIGHT);
        assertTrue(only.size() < all.size());
        assertTrue(only.stream().allMatch(line -> line.isTitle() || line.differs()));
        assertEquals(all.stream().filter(Line::differs).count(), only.stream().filter(Line::differs).count());
        int left = lineWith(only, "Wear safety glasses", true);
        assertEquals(left, only.get(only.get(left).left.counterpartLine).right.counterpartLine, "links still right");
    }

    @Test
    void onlyWhatIsComparableLeavesOutWhatHasNoMatch() throws IOException {
        Comparison comparison = compare("training-handbook.docx");
        List<Line> comparable = Lines.of(comparison, Shown.ALL.withOnlyComparable(true), FONTS, Palette.LIGHT);
        for (Line line : comparable) {
            if (!line.isTitle() && line.left != null && line.right == null) {
                assertTrue(line.left.counterpartLine >= 0, "alone, it is moved: " + line.left.text);
            }
        }
        lineWith(comparable, "This module describes", false);
        lineWith(comparable, "The module does not cover", false);
        assertThrows(AssertionError.class, () -> lineWith(comparable, "Why must you wait", false), "the quiz");
        assertThrows(AssertionError.class, () -> lineWith(comparable, "Scope", true));
        int left = lineWith(comparable, "Wear safety glasses", true);
        LineSide moved = comparable.get(left).left;
        assertEquals(left, comparable.get(moved.counterpartLine).right.counterpartLine, "links still right");
        long tableRows = comparable.stream().filter(l -> l.left != null && l.left.columns != null).count();
        assertEquals(4, tableRows, "the columns' line and the three rows both tables have; not the oil's");
        assertTrue(comparable.stream().filter(l -> l.left != null && l.left.columns != null)
                .allMatch(l -> l.left.columns.size() == 2), "nor the quantities, a column the handbook has not got");
        assertEquals("", lines(comparable, "Stop the pump and lock out the main switch."),
                "the same paragraph is shown, and nothing marked in it");
    }

    @Test
    void aDocumentAloneIsShownOnItsSideAsItIs() throws IOException {
        List<Line> lines = Lines.alone(DocxReader.read(Path.of("samples", "procedure-revA.docx")), false, false, FONTS,
                Palette.LIGHT);
        assertEquals(List.of("Body", "Headers and footers", "Footnotes and endnotes"),
                lines.stream().filter(Line::isTitle).map(line -> line.title).toList(), "no difference counted");
        assertTrue(lines.stream().filter(line -> !line.isTitle())
                .allMatch(line -> line.left == null && line.right != null && line.marking() == LineSide.Marking.SAME));
        assertEquals("Maintenance Procedure \u2014 KP-40 Hydraulic Pump", lines.get(1).right.text.text());
        assertTrue(lines.stream().anyMatch(line -> line.right != null && line.right.columns != null), "its table");
    }

    /** Every switch off: the documents aligned, and nothing marked anywhere — no word, no space, no move said. */
    @Test
    void withNothingComparedNothingIsMarked() throws IOException {
        Comparison comparison = DocumentComparer.compare(DocxReader.read(Path.of("samples", "procedure-revA.docx")),
                DocxReader.read(Path.of("samples", "procedure-revB.docx")), CompareOptions.TEXT_ONLY.with(
                        CompareOptions.Aspect.TEXT, false));
        assertTrue(comparison.same());
        for (Line line : Lines.of(comparison, Shown.ALL, FONTS, Palette.LIGHT)) {
            assertFalse(line.differs(), String.valueOf(line.title));
            for (LineSide side : new LineSide[] {line.left, line.right}) {
                if (side != null && side.text != null) {
                    assertNull(side.link(), "no move said: " + side.text.text());
                    assertTrue(side.pairing == null || (side.pairing.left() == null ? List.<Span>of() : side.pairing.diff().left())
                            .stream().noneMatch(Span::differs), side.text.text());
                }
            }
        }
    }

    /** The document view: each paragraph as its styles lay it out, drawn without a screen. */
    @Test
    void theDocumentViewLaysParagraphsOutAsWordDoes() throws IOException {
        List<Line> lines = Lines.of(compare("procedure-revB.docx"), Shown.ALL.withDocumentView(true), FONTS,
                Palette.LIGHT);
        ComparisonView view = new ComparisonView();
        view.setLines(lines, -1, Palette.LIGHT);
        view.setSize(1200, 10);
        view.setSize(1200, view.getPreferredSize().height);
        BufferedImage image = new BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        view.paint(g);
        g.dispose();
        LineSide title = lines.get(lineWith(lines, "Maintenance Procedure", true)).left;
        LineSide body = lines.get(lineWith(lines, "Document KP-MP-012", true)).left;
        assertTrue(title.height() > body.height(), "the title's style is larger than Normal");
        LineSide indented = lines.get(lineWith(lines, "The parts needed", false)).right;
        assertNotNull(indented.text, "laid out");
    }

    /**
     * Pages in the document view, numbered on each side: a paragraph's "page break before", Word's own pages, a page
     * break alone in an empty paragraph said before the next — and Word's record of that same page said once.
     */
    @Test
    void theDocumentViewShowsWhereEachPageBegins() throws IOException {
        List<Line> lines = Lines.of(compare("procedure-revB.docx"), Shown.ALL.withDocumentView(true), FONTS,
                Palette.LIGHT);
        assertEquals(List.of("Page break \u2014 page 2"), lines.get(lineWith(lines, "Safety", true)).left.text.pageMarks(),
                "asked for, and Word's record of it at the same place said once");
        assertEquals(List.of("Page 3"), lines.get(lineWith(lines, "Remove the old filter", true)).left.text.pageMarks(),
                "Word's own");
        assertEquals(List.of("Page break \u2014 page 4"), lines.get(lineWith(lines, "Records", true)).left.text.pageMarks(),
                "an empty paragraph's, said before the next");
        assertEquals(List.of("Page 2"), lines.get(lineWith(lines, "Restart the pump", false)).right.text.pageMarks());
        assertEquals(List.of("Page break \u2014 page 3"), lines.get(lineWith(lines, "Safety", false)).right.text.pageMarks());
        assertEquals(List.of("Page break \u2014 page 4"), lines.get(lineWith(lines, "Records", false)).right.text.pageMarks());
        assertEquals(List.of(), lines.get(lineWith(lines, "Purpose", true)).left.text.pageMarks());
        List<Line> diff = Lines.of(compare("procedure-revB.docx"), Shown.ALL, FONTS, Palette.LIGHT);
        assertEquals(List.of(), diff.get(lineWith(diff, "Records", true)).left.text.pageMarks(), "the diff view has none");
    }

    /** Pictures: drawn at their size in the document view, a sign in the diff view. */
    @Test
    void picturesAreDrawnAtTheirSize() throws IOException {
        Comparison comparison = compare("procedure-revB.docx");
        PictureImages.prepare(comparison.left());
        List<Line> document = Lines.of(comparison, Shown.ALL.withDocumentView(true), FONTS, Palette.LIGHT);
        List<Line> diff = Lines.of(comparison, Shown.ALL, FONTS, Palette.LIGHT);
        ComparisonView view = new ComparisonView();
        view.setLines(document, -1, Palette.LIGHT);
        view.setSize(1200, 10);
        view.setSize(1200, view.getPreferredSize().height);
        Line figure = document.stream().filter(line -> line.left != null && line.left.text != null
                && line.left.text.text().equals("\uFFFC")).findFirst().orElseThrow();
        assertTrue(figure.left.height() > 100, "105 points high, at the view's scale: " + figure.left.height());
        ComparisonView plain = new ComparisonView();
        plain.setLines(diff, -1, Palette.LIGHT);
        plain.setSize(1200, 10);
        plain.setSize(1200, plain.getPreferredSize().height);
        Line sign = diff.stream().filter(line -> line.left != null && line.left.text != null
                && line.left.text.text().equals("\uFFFC")).findFirst().orElseThrow();
        assertTrue(sign.left.height() < 40, "a sign, a line high: " + sign.left.height());
        BufferedImage image = new BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        view.paint(g);
        g.dispose();
    }

    /** List items: in the document view, their number in their text, where Word puts it; in the diff view, in the margin. */
    @Test
    void theDocumentViewShowsListNumbersInTheText() throws IOException {
        List<Line> document = Lines.of(compare("procedure-revB.docx"), Shown.ALL.withDocumentView(true), FONTS,
                Palette.LIGHT);
        LineSide step = document.get(lineWith(document, "Unscrew the filter cover", false)).right;
        assertEquals("4.", step.text.number());
        assertNull(step.marker, "not in the margin as well");
        assertEquals("\u2022", document.get(lineWith(document, "Replacement of the suction", true)).left.text.number());
        List<Line> diff = Lines.of(compare("procedure-revB.docx"), Shown.ALL, FONTS, Palette.LIGHT);
        LineSide plain = diff.get(lineWith(diff, "Unscrew the filter cover", false)).right;
        assertEquals("4.", plain.marker);
        assertNull(plain.text.number());
    }

    @Test
    void theViewIsDrawnWithoutAScreen() throws IOException {
        ComparisonView view = new ComparisonView();
        view.setLines(Lines.of(compare("training-handbook.docx"), Shown.ALL, view.fonts(), Palette.LIGHT), 3,
                Palette.LIGHT);
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

    /** The differences marked in the left text of the line starting so: none, "". */
    private static String lines(List<Line> lines, String start) {
        Line line = lines.get(lineWith(lines, start, true));
        assertEquals(LineSide.Marking.SAME, line.marking());
        return line.left.text.text().equals(line.right.text.text()) ? "" : "differs";
    }

    private static int lineWith(List<Line> lines, String start, boolean onLeft) {
        for (int i = 0; i < lines.size(); i++) {
            LineSide side = onLeft ? lines.get(i).left : lines.get(i).right;
            if (side != null && side.text != null && side.text.text().startsWith(start)) {
                return i;
            }
        }
        throw new AssertionError("no line with " + start);
    }

    private static Comparison compare(String right) throws IOException {
        return DocumentComparer.compare(DocxReader.read(Path.of("samples", "procedure-revA.docx")),
                DocxReader.read(Path.of("samples", right)), CompareOptions.ALL.with(CompareOptions.Aspect.BLANKS, false));
    }
}
