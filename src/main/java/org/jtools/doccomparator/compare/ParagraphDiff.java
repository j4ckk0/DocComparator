package org.jtools.doccomparator.compare;

import java.util.List;

/**
 * How two paired paragraphs differ: their text, stretch by stretch on each side (spans cover the whole text, in
 * order, with ABSENT marks between them); the formatting of the text they share, where it differs (with the styles
 * compared); their own properties — heading, list, place, style — where they differ (with structure and styles).
 */
public record ParagraphDiff(List<Span> left, List<Span> right, List<FormatSpan> leftFormatting,
                            List<FormatSpan> rightFormatting, List<PropertyDifference> properties) {

    public ParagraphDiff {
        left = List.copyOf(left);
        right = List.copyOf(right);
        leftFormatting = List.copyOf(leftFormatting);
        rightFormatting = List.copyOf(rightFormatting);
        properties = List.copyOf(properties);
    }

    /**
     * A paragraph with no counterpart, on the left side or the right: its whole text is only there — which does not
     * count when the text does not.
     */
    static ParagraphDiff alone(String text, boolean onLeft, boolean counted) {
        List<Span> spans = text.isEmpty() ? List.of()
                : List.of(new Span(0, text.length(), counted ? Span.Kind.ONLY_HERE : Span.Kind.UNIMPORTANT));
        return onLeft ? new ParagraphDiff(spans, List.of(), List.of(), List.of(), List.of())
                : new ParagraphDiff(List.of(), spans, List.of(), List.of(), List.of());
    }

    /** Whether their text differs, blanks aside when they are left out. */
    public boolean textDiffers() {
        return left.stream().anyMatch(Span::differs) || right.stream().anyMatch(Span::differs);
    }

    /** Whether anything else compared differs: formatting, properties. */
    public boolean otherwiseDiffers() {
        return !leftFormatting.isEmpty() || !properties.isEmpty();
    }
}
