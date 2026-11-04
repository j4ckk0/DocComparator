package org.jtools.doccomparator.compare;

import java.util.List;

/** A stretch of text, [start, end), the same on both sides but formatted otherwise: how. */
public record FormatSpan(int start, int end, List<PropertyDifference> differences) {

    public FormatSpan {
        differences = List.copyOf(differences);
    }
}
