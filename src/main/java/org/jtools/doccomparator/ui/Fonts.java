package org.jtools.doccomparator.ui;

import java.awt.Font;

/** The fonts of the view: the documents' text, headings in bold, and a smaller one for what is said about them. */
record Fonts(Font text, Font heading, Font small, Font notes) {

    static Fonts of(Font base) {
        Font text = base.deriveFont(Font.PLAIN, base.getSize2D() + 1);
        Font small = base.deriveFont(Font.PLAIN, base.getSize2D() - 1);
        return new Fonts(text, text.deriveFont(Font.BOLD), small, small.deriveFont(Font.ITALIC));
    }
}
