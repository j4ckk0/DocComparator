package org.jtools.doccomparator.compare;

/**
 * A line of the side-by-side view: a paragraph of the left document and the one of the right it is paired with, or
 * one of them alone. A moved paragraph is shown alone where it is on each side, each row naming the other.
 *
 * @param left        the left paragraph, null for none
 * @param right       the right paragraph, null for none
 * @param counterpart for a moved paragraph, the row holding its match; -1 otherwise
 * @param diff        how the paragraph differs from its match; for one alone, its whole text only on its side
 */
public record Row(Item left, Item right, Status status, int counterpart, ParagraphDiff diff) {

    public enum Status {
        /** The same, as the options compare it. */
        SAME,
        /** The same text, but its structure or style differs. */
        SAME_TEXT,
        /** Its text differs. */
        CHANGED,
        /** Only in the left document. */
        LEFT_ONLY,
        /** Only in the right document. */
        RIGHT_ONLY
    }

    public boolean moved() {
        return counterpart >= 0;
    }

    /** Whether the row shows a difference: one to go to from the previous or the next. */
    public boolean differs() {
        return status != Status.SAME || moved();
    }

    static Status statusOf(ParagraphDiff diff) {
        if (diff.textDiffers()) {
            return Status.CHANGED;
        }
        return diff.otherwiseDiffers() ? Status.SAME_TEXT : Status.SAME;
    }
}
