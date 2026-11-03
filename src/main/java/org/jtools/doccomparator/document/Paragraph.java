package org.jtools.doccomparator.document;

import java.util.List;

/**
 * A paragraph: its runs, the paragraph style it is in (null for none), the properties set on it directly, and its
 * place in the structure — a heading's level (0 for none), a list item (null for none), the end of a section.
 */
public record Paragraph(String styleId, Formatting formatting, int headingLevel, ListItem listItem, boolean endsSection,
                        List<Run> runs) implements Block {

    public Paragraph {
        runs = List.copyOf(runs);
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
