package org.jtools.doccomparator.ui;

import com.formdev.flatlaf.FlatLaf;
import java.awt.Color;
import javax.swing.UIManager;

/**
 * The colours of the view, after Beyond Compare's: red for text that differs, purple for a paragraph on one side only,
 * and two of the program's own — teal for a moved paragraph, amber for one whose text is the same but whose layout is
 * not. Light, or dark in a dark look (use): the diff view's; the document view keeps the light ones, a page being
 * white.
 */
final class Palette {

    static Color BACKGROUND;
    static Color TEXT;
    static Color FAINT;
    static Color LINK;
    static Color SEPARATOR;
    static Color GRID;
    static Color CURRENT;
    static Color TITLE;
    static Color TITLE_TEXT;
    static Color NO_SIDE;
    static Color NO_SIDE_HATCH;

    /** Behind a whole paragraph, by how it compares. */
    static Color CHANGED_LINE;
    static Color ORPHAN_LINE;
    static Color MOVED_LINE;
    static Color OTHERWISE_LINE;

    /** Behind the text that differs, within a paragraph. */
    static Color CHANGED_TEXT;
    static Color ABSENT_MARK;
    static Color FORMATTING_MARK;

    /** In the margin map, the gutter and the legend: stronger, to be seen at a glance; the same light or dark. */
    static final Color CHANGED_MARK = new Color(0xE04848);
    static final Color ORPHAN_MARK = new Color(0x8A63D2);
    static final Color MOVED_MARK = new Color(0x1F9AAA);
    static final Color OTHERWISE_MARK = new Color(0xE0A400);

    private static boolean dark;

    static {
        use(false);
    }

    private Palette() {
    }

    /** The light colours, or the dark. */
    static void use(boolean darkOnes) {
        dark = darkOnes;
        BACKGROUND = dark ? new Color(0x1E1F22) : Color.WHITE;
        TEXT = dark ? new Color(0xDFE1E5) : new Color(0x202020);
        FAINT = dark ? new Color(0x8C9096) : new Color(0x707070);
        LINK = dark ? new Color(0x6CA6FF) : new Color(0x0B5CB8);
        SEPARATOR = dark ? new Color(0x2E3034) : new Color(0xE4E4E4);
        GRID = dark ? new Color(0x4A4D52) : new Color(0xC8C8C8);
        CURRENT = dark ? new Color(0x4C8DFF) : new Color(0x2F6FDD);
        TITLE = dark ? new Color(0x393D45) : new Color(0x4A5568);
        TITLE_TEXT = Color.WHITE;
        NO_SIDE = dark ? new Color(0x27292D) : new Color(0xF2F2F2);
        NO_SIDE_HATCH = dark ? new Color(0x34363B) : new Color(0xE2E2E2);
        CHANGED_LINE = dark ? new Color(0x3A2326) : new Color(0xFFEDED);
        ORPHAN_LINE = dark ? new Color(0x2D2740) : new Color(0xF0E8FF);
        MOVED_LINE = dark ? new Color(0x1C3336) : new Color(0xE3F4F6);
        OTHERWISE_LINE = dark ? new Color(0x3A3220) : new Color(0xFFF6D9);
        CHANGED_TEXT = dark ? new Color(0x8C3A40) : new Color(0xFFADAD);
        ABSENT_MARK = dark ? new Color(0xFF5A5A) : new Color(0xD01010);
        FORMATTING_MARK = dark ? new Color(0xC77DFF) : new Color(0x9B30D9);
    }

    static boolean isDark() {
        return dark;
    }

    /** Whether the look in use is dark: FlatLaf says; for another, its panels' background. */
    static boolean lookIsDark() {
        if (UIManager.getLookAndFeel() instanceof FlatLaf) {
            return FlatLaf.isLafDark();
        }
        Color background = UIManager.getColor("Panel.background");
        return background != null
                && (background.getRed() * 299 + background.getGreen() * 587 + background.getBlue() * 114) / 1000 < 128;
    }

    /** A colour of the look in use, by its key; this one when it has none. */
    static Color ofLook(String key, Color otherwise) {
        Color color = UIManager.getColor(key);
        return color != null ? color : otherwise;
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
