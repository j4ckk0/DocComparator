package org.jtools.doccomparator.ui;

import java.awt.Color;

/**
 * The colours of the view, after Beyond Compare's: red for text that differs, purple for a paragraph on one side only,
 * and two of the program's own — teal for a moved paragraph, amber for one
 * whose text is the same but whose structure or style is not.
 */
final class Palette {

    static final Color BACKGROUND = Color.WHITE;
    static final Color TEXT = new Color(0x202020);
    static final Color FAINT = new Color(0x707070);
    static final Color LINK = new Color(0x0B5CB8);
    static final Color SEPARATOR = new Color(0xE4E4E4);
    static final Color GRID = new Color(0xC8C8C8);
    static final Color CURRENT = new Color(0x2F6FDD);
    static final Color TITLE = new Color(0x4A5568);
    static final Color TITLE_TEXT = Color.WHITE;
    static final Color NO_SIDE = new Color(0xF2F2F2);
    static final Color NO_SIDE_HATCH = new Color(0xE2E2E2);

    /** Behind a whole paragraph, by how it compares. */
    static final Color CHANGED_LINE = new Color(0xFFEDED);
    static final Color ORPHAN_LINE = new Color(0xF0E8FF);
    static final Color MOVED_LINE = new Color(0xE3F4F6);
    static final Color OTHERWISE_LINE = new Color(0xFFF6D9);

    /** Behind the text that differs, within a paragraph. */
    static final Color CHANGED_TEXT = new Color(0xFFADAD);
    static final Color ABSENT_MARK = new Color(0xD01010);
    static final Color FORMATTING_MARK = new Color(0x9B30D9);

    /** In the margin map and the gutter: stronger, to be seen at a glance. */
    static final Color CHANGED_MARK = new Color(0xE04848);
    static final Color ORPHAN_MARK = new Color(0x8A63D2);
    static final Color MOVED_MARK = new Color(0x1F9AAA);
    static final Color OTHERWISE_MARK = new Color(0xE0A400);

    private Palette() {
    }

    /** A colour to lay over text drawn in its own colours, which shows through. */
    static Color translucent(Color color) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), 150);
    }

    static Color line(Cell.Look look) {
        return switch (look) {
            case SAME -> BACKGROUND;
            case CHANGED -> CHANGED_LINE;
            case ORPHAN -> ORPHAN_LINE;
            case MOVED -> MOVED_LINE;
            case OTHERWISE -> OTHERWISE_LINE;
        };
    }

    static Color mark(Cell.Look look) {
        return switch (look) {
            case SAME -> BACKGROUND;
            case CHANGED -> CHANGED_MARK;
            case ORPHAN -> ORPHAN_MARK;
            case MOVED -> MOVED_MARK;
            case OTHERWISE -> OTHERWISE_MARK;
        };
    }
}
