package org.jtools.doccomparator.ui;

/** A line of the view: the title of a part (the body, the headers and footers…), or a cell on either side or both. */
final class Line {

    /** The title of a part; null for a line of cells. */
    final String title;
    final Cell left;
    final Cell right;
    /** Where it is in the view, once laid out. */
    int top;
    int height;

    private Line(String title, Cell left, Cell right) {
        this.title = title;
        this.left = left;
        this.right = right;
    }

    static Line title(String title) {
        return new Line(title, null, null);
    }

    static Line of(Cell left, Cell right) {
        return new Line(null, left, right);
    }

    boolean isTitle() {
        return title != null;
    }

    /** The stronger look of its sides. */
    Cell.Look look() {
        Cell.Look look = Cell.Look.SAME;
        if (left != null) {
            look = look.stronger(left.look);
        }
        if (right != null) {
            look = look.stronger(right.look);
        }
        return look;
    }

    boolean differs() {
        return !isTitle() && look() != Cell.Look.SAME;
    }
}
