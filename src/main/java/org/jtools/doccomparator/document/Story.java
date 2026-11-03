package org.jtools.doccomparator.document;

import java.util.List;

/**
 * One flow of text, as Word calls it: the body, a header, a footer, a footnote, an endnote. Its label names it for a
 * reader: "Body", "Header (first page), section 2", "Footnote 3".
 */
public record Story(Kind kind, String label, List<Block> blocks) {

    public enum Kind {
        BODY, HEADER, FOOTER, FOOTNOTE, ENDNOTE
    }

    public Story {
        blocks = List.copyOf(blocks);
    }
}
