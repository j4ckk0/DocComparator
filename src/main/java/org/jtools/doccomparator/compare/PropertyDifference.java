package org.jtools.doccomparator.compare;

/** Something compared besides the text — a style, a heading level, the template — and its value on each side. */
public record PropertyDifference(String property, String left, String right) {

    /** What stands for a value a side does not have. */
    public static final String NONE = "(none)";

    /** The difference, null when the values are the same; a null value is NONE. */
    static PropertyDifference of(String property, String left, String right) {
        String l = left == null ? NONE : left;
        String r = right == null ? NONE : right;
        return l.equals(r) ? null : new PropertyDifference(property, l, r);
    }

    @Override
    public String toString() {
        return property + ": " + left + " → " + right;
    }
}
