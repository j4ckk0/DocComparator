package org.jtools.doccomparator.compare;

/*-
 * #%L
 * DocComparator
 * %%
 * Copyright (C) 2026 jtools.org
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

/**
 * A paragraph (or a table) of the left document and the one of the right it is paired with, or one of them alone: what
 * a line of the side-by-side view shows. A moved paragraph has a pairing where it is on each side, each naming the
 * other. The cells of compared tables are pairings too.
 *
 * @param left        the left paragraph, null for none
 * @param right       the right paragraph, null for none
 * @param counterpart for a moved paragraph, the pairing holding its match; -1 otherwise
 * @param diff        how the paragraph differs from its match; for one alone, its whole text only on its side; for a
 *                    table, its own properties only
 * @param table       for a table, how it compares with its match, row by row and column by column (or how it is laid
 *                    out, alone); null for a paragraph
 */
public record Pairing(Item left, Item right, Status status, int counterpart, ParagraphDiff diff,
                      TableComparison table) {

    public enum Status {
        /** The same, as the options compare it. */
        SAME,
        /** The same, as the options compare it, but moved (the text compared). */
        MOVED,
        /** Its text the same, or not compared, but another aspect compared differs: a heading level, a style… */
        OTHERWISE,
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

    /** Whether the pairing shows a difference: one to go to from the previous or the next. */
    public boolean differs() {
        return status != Status.SAME;
    }

    static Status statusOf(ParagraphDiff diff) {
        if (diff.textDiffers()) {
            return Status.CHANGED;
        }
        return diff.otherwiseDiffers() ? Status.OTHERWISE : Status.SAME;
    }
}
