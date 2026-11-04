package org.jtools.doccomparator.compare;

/**
 * What the comparison takes into account besides the text, which it always compares:
 * <ul>
 * <li>structure: the headings and their levels, the lists, where a paragraph is (body, table cell, text box), the
 * sections;</li>
 * <li>blanks: spaces, tabs and breaks as they are, and empty paragraphs; left out, a difference of blanks alone is
 * shown, but does not count;</li>
 * <li>template: the template each document is attached to, and names;</li>
 * <li>headers and footers;</li>
 * <li>styles: the styles of paragraphs and of text, the formatting set on them, the documents' style definitions.</li>
 * </ul>
 */
public record CompareOptions(boolean structure, boolean blanks, boolean template, boolean headersAndFooters,
                             boolean styles) {

    /** Everything but the blanks. */
    public static final CompareOptions DEFAULT = new CompareOptions(true, false, true, true, true);

    public CompareOptions withStructure(boolean on) {
        return new CompareOptions(on, blanks, template, headersAndFooters, styles);
    }

    public CompareOptions withBlanks(boolean on) {
        return new CompareOptions(structure, on, template, headersAndFooters, styles);
    }

    public CompareOptions withTemplate(boolean on) {
        return new CompareOptions(structure, blanks, on, headersAndFooters, styles);
    }

    public CompareOptions withHeadersAndFooters(boolean on) {
        return new CompareOptions(structure, blanks, template, on, styles);
    }

    public CompareOptions withStyles(boolean on) {
        return new CompareOptions(structure, blanks, template, headersAndFooters, on);
    }
}
