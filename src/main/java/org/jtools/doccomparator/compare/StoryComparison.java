package org.jtools.doccomparator.compare;

import java.util.List;

/** The rows of one kind of story compared — "Body", "Headers and footers", "Footnotes and endnotes". */
public record StoryComparison(String title, List<Row> rows) {

    public StoryComparison {
        rows = List.copyOf(rows);
    }

    /** The rows that show a difference; a moved paragraph counts once on each side. */
    public long differences() {
        return rows.stream().filter(Row::differs).count();
    }
}
