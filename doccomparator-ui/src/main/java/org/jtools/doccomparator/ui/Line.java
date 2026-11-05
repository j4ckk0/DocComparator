package org.jtools.doccomparator.ui;

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

/** A line of the view: the title of a part (the body, the headers and footers…), or what is on either side, or both. */
final class Line {

    /** The title of a part; null for a line of sides. */
    final String title;
    final LineSide left;
    final LineSide right;
    /** Where it is in the view, once laid out. */
    int top;
    int height;

    private Line(String title, LineSide left, LineSide right) {
        this.title = title;
        this.left = left;
        this.right = right;
    }

    static Line title(String title) {
        return new Line(title, null, null);
    }

    static Line of(LineSide left, LineSide right) {
        return new Line(null, left, right);
    }

    boolean isTitle() {
        return title != null;
    }

    /** The stronger marking of its sides. */
    LineSide.Marking marking() {
        LineSide.Marking marking = LineSide.Marking.SAME;
        if (left != null) {
            marking = marking.stronger(left.marking);
        }
        if (right != null) {
            marking = marking.stronger(right.marking);
        }
        return marking;
    }

    boolean differs() {
        return !isTitle() && marking() != LineSide.Marking.SAME;
    }
}
