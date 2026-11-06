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
public record ParagraphDifference(List<TextSpan> left, List<TextSpan> right, List<FormatSpan> leftFormatting,
                            List<FormatSpan> rightFormatting, List<PropertyDifference> properties) {

    public ParagraphDifference {
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
    static ParagraphDifference alone(String text, boolean onLeft, boolean counted) {
        List<TextSpan> spans = text.isEmpty() ? List.of()
                : List.of(new TextSpan(0, text.length(), counted ? TextSpan.Kind.ONLY_HERE : TextSpan.Kind.SAME));
        return onLeft ? new ParagraphDifference(spans, List.of(), List.of(), List.of(), List.of())
                : new ParagraphDifference(List.of(), spans, List.of(), List.of(), List.of());
    }

    /** Whether their text differs, blanks aside when they are left out. */
    public boolean textDiffers() {
        return left.stream().anyMatch(TextSpan::differs) || right.stream().anyMatch(TextSpan::differs);
    }

    /** Whether anything else compared differs: formatting, properties. */
    public boolean otherwiseDiffers() {
        return !leftFormatting.isEmpty() || !properties.isEmpty();
    }
}
