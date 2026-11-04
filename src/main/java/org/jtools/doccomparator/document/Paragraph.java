package org.jtools.doccomparator.document;

import java.util.List;

/**
 * A paragraph: its runs, the paragraph style it is in (null for none), the properties set on it directly, and its
 * place in the structure — a heading's level (0 for none), a list item (null for none), the end of a section — and
 * where Word's pages began in it when Word last laid the document out (w:lastRenderedPageBreak), as offsets in its
 * text: a record of Word's, not part of the document's content, never compared.
 */
public record Paragraph(String styleId, Formatting formatting, int headingLevel, ListItem listItem, boolean endsSection,
                        List<Run> runs, List<Integer> pageStarts) implements Block {

    public Paragraph {
        runs = List.copyOf(runs);
        pageStarts = List.copyOf(pageStarts);
    }

    public Paragraph(String styleId, Formatting formatting, int headingLevel, ListItem listItem, boolean endsSection,
                     List<Run> runs) {
        this(styleId, formatting, headingLevel, listItem, endsSection, runs, List.of());
    }

    /** The text of its runs, end to end. */
    public String text() {
        StringBuilder text = new StringBuilder();
        for (Run run : runs) {
            text.append(run.text());
        }
        return text.toString();
    }
}
