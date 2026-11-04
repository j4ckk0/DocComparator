package org.jtools.doccomparator.ui;

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
