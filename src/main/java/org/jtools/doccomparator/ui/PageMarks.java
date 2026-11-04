package org.jtools.doccomparator.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.jtools.doccomparator.document.Block;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.StyleSheet;
import org.jtools.doccomparator.document.Table;
import org.jtools.doccomparator.document.WordDocument;
import org.jtools.doccomparator.ui.DocumentLook.PageMark;
import org.jtools.doccomparator.i18n.I18n;

/**
 * Where a document's pages begin, for the document view, numbered: those it asks for — a page break in its text, a
 * paragraph's "page break before" — and those Word's layout gave it when Word last saved it (its record of them,
 * Paragraph.pageStarts); and where its sections end. By paragraph, the order of their offsets.
 *
 * <p>Word records again, at the next text, a page a page break began: the reader keeps it once; a paragraph with a page
 * break before it begins its page itself: Word's record of it at its start is the same page. A section's end is said,
 * but not counted as a page: it may go on on the same one; Word's record, after it, says if it did not. A page begun in
 * a table counts, but is not shown: a table's cells are not laid out as paragraphs. With the blanks left out, an empty
 * paragraph is not shown: what begins in it, or after it, is said before the paragraph after it.
 */
final class PageMarks {

    private final StyleSheet styles;
    private final boolean blanks;
    private final Map<Paragraph, List<PageMark>> marks = new IdentityHashMap<>();
    private final List<PageMark> pending = new ArrayList<>();
    private int page = 1;
    private boolean begun;

    private PageMarks(StyleSheet styles, boolean blanks) {
        this.styles = styles;
        this.blanks = blanks;
    }

    /** The marks of a document's body, by paragraph; with the blanks compared or not (empty paragraphs shown or not). */
    static Map<Paragraph, List<PageMark>> of(WordDocument document, boolean blanks) {
        PageMarks pages = new PageMarks(document.styles(), blanks);
        pages.walk(document.body().blocks(), true);
        return pages.marks;
    }

    private void walk(List<Block> blocks, boolean shown) {
        for (Block block : blocks) {
            switch (block) {
                case Paragraph paragraph -> paragraph(paragraph, shown);
                case Table table -> {
                    for (Table.Row row : table.rows()) {
                        for (Table.Cell cell : row.cells()) {
                            walk(cell.blocks(), false);
                        }
                    }
                }
                default -> {
                    // a text box is beside the text: no page begins in it
                }
            }
        }
    }

    private void paragraph(Paragraph paragraph, boolean shown) {
        String text = paragraph.text();
        record Start(int offset, boolean asked) {
        }
        List<Start> starts = new ArrayList<>();
        boolean breakBefore = begun && "true".equals(styles.effectiveParagraph(paragraph).get("pageBreakBefore"));
        if (breakBefore) {
            starts.add(new Start(0, true));
        }
        for (int offset : paragraph.pageStarts()) {
            if (!(offset == 0 && breakBefore)) {
                starts.add(new Start(offset, false));
            }
        }
        for (int i = text.indexOf('\f'); i >= 0; i = text.indexOf('\f', i + 1)) {
            starts.add(new Start(i + 1, true));
        }
        starts.sort(Comparator.comparingInt(Start::offset));
        List<PageMark> own = new ArrayList<>();
        for (Start start : starts) {
            page++;
            own.add(new PageMark(start.offset(), I18n.text(start.asked() ? "page.break" : "page.number", page)));
        }
        if (paragraph.endsSection()) {
            own.add(new PageMark(text.length(), I18n.text("page.sectionBreak")));
        }
        if (!text.isBlank()) {
            begun = true;
        }
        if (!shown) {
            return;
        }
        boolean leftOut = !blanks && text.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c));
        if (leftOut) {
            own.forEach(mark -> pending.add(new PageMark(0, mark.label())));
            return;
        }
        List<PageMark> all = new ArrayList<>(pending);
        all.addAll(own);
        pending.clear();
        if (!all.isEmpty()) {
            marks.put(paragraph, all);
        }
    }
}
