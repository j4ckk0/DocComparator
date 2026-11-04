package org.jtools.doccomparator.compare;

/** A stretch of a paragraph's text, [start, end), and how it compares with the paragraph it is paired with. */
public record Span(int start, int end, Kind kind) {

    public enum Kind {
        /** The same text on the other side. */
        SAME,
        /** Other text in its place on the other side. */
        CHANGED,
        /** Nothing in its place on the other side: removed, or added. */
        ONLY_HERE,
        /** Empty: where the other side has text this one has not (ONLY_HERE there). */
        ABSENT
    }

    /** Whether it is a difference that counts. */
    public boolean differs() {
        return kind == Kind.CHANGED || kind == Kind.ONLY_HERE || kind == Kind.ABSENT;
    }
}
