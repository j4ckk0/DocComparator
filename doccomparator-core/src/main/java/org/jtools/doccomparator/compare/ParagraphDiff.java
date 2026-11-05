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

import java.util.List;

/**
 * How two paired paragraphs differ: their text, stretch by stretch on each side (spans cover the whole text, in
 * order, with ABSENT marks between them); the formatting of the text they share, where it differs (with the styles
 * or the characters compared); their own properties — heading, list, place, style, indents… — where they differ, as
 * the options compare them.
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
     * A paragraph with no counterpart, on the left side or the right: its whole text is only there — no difference
     * when the text is not compared.
     */
    static ParagraphDiff alone(String text, boolean onLeft, boolean counted) {
        List<Span> spans = text.isEmpty() ? List.of()
                : List.of(new Span(0, text.length(), counted ? Span.Kind.ONLY_HERE : Span.Kind.SAME));
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
