package org.jtools.doccomparator.document;

import java.util.List;

/**
 * A Word document as the comparison sees it: its stories — the body, the headers and footers, the footnotes and
 * endnotes — its styles and its template. Tracked changes are read as if accepted: what was inserted is in the text,
 * what was deleted is not; hasTrackedChanges says the document had some.
 */
public record WordDocument(String fileName, Story body, List<Story> headersAndFooters, List<Story> notes,
                           StyleSheet styles, Template template, boolean hasTrackedChanges) {

    public WordDocument {
        headersAndFooters = List.copyOf(headersAndFooters);
        notes = List.copyOf(notes);
    }
}
