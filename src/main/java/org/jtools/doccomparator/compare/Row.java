package org.jtools.doccomparator.compare;

/**
 * A line of the side-by-side view: a paragraph (or a table) of the left document and the one of the right it is paired
 * with, or one of them alone. A moved paragraph is shown alone where it is on each side, each row naming the other.
 * The cells of compared tables are rows too.
 *
 * @param left        the left paragraph, null for none
 * @param right       the right paragraph, null for none
 * @param counterpart for a moved paragraph, the row holding its match; -1 otherwise
 * @param diff        how the paragraph differs from its match; for one alone, its whole text only on its side; for a
 *                    table, its own properties only
 * @param table       for a table, how it compares with its match, row by row and column by column (or how it is laid
 *                    out, alone); null for a paragraph
 */
public record Row(Item left, Item right, Status status, int counterpart, ParagraphDiff diff, TableComparison table) {

    public enum Status {
        /** The same, as the options compare it. */
        SAME,
        /** The same text, but its structure or style differs (for a table: rows or columns moved). */
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
