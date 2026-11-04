/*
 * Copyright 2026 j4ckk0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jtools.doccomparator.ui;

import com.formdev.flatlaf.FlatLaf;
import java.awt.Color;
import javax.swing.UIManager;

/**
 * The colours of the view, after Beyond Compare's: red for text that differs, purple for a paragraph on one side only,
 * and two of the program's own — teal for a moved paragraph, amber for one whose text is the same but whose layout is
 * not. LIGHT, or DARK for the diff view in a dark look; the document view keeps the light ones, a page being white.
 * The lines are built in a palette and drawn in it: each TextBlock keeps its own.
 */
final class Palette {

    static final Palette LIGHT = new Palette(false);
    static final Palette DARK = new Palette(true);

    /** In the margin map, the gutter and the legend: stronger, to be seen at a glance; the same light or dark. */
    static final Color CHANGED_MARK = new Color(0xE04848);
    static final Color ORPHAN_MARK = new Color(0x8A63D2);
    static final Color MOVED_MARK = new Color(0x1F9AAA);
    static final Color OTHERWISE_MARK = new Color(0xE0A400);

    final boolean dark;
    final Color background;
    final Color text;
    final Color faint;
    final Color link;
    final Color separator;
    final Color grid;
    final Color current;
    /** The side text pasted goes to: its frame while it waits, and its head's underline; their inside. */
    final Color chosenSide;
    final Color chosenSideInside;
    /** The gutter between the sides, where nothing is marked. */
    final Color gutter;
    final Color title;
    final Color titleText;
    final Color noSide;
    final Color noSideHatch;

    /** Behind a whole paragraph, by how it compares. */
    final Color changedLine;
    final Color orphanLine;
    final Color movedLine;
    final Color otherwiseLine;

    /** Behind the text that differs, within a paragraph. */
    final Color changedText;
    final Color absentMark;
    final Color formattingMark;

    private Palette(boolean dark) {
        this.dark = dark;
        background = dark ? new Color(0x1E1F22) : Color.WHITE;
        text = dark ? new Color(0xDFE1E5) : new Color(0x202020);
        faint = dark ? new Color(0x8C9096) : new Color(0x707070);
        link = dark ? new Color(0x6CA6FF) : new Color(0x0B5CB8);
        separator = dark ? new Color(0x2E3034) : new Color(0xE4E4E4);
        grid = dark ? new Color(0x4A4D52) : new Color(0xC8C8C8);
        current = dark ? new Color(0x4C8DFF) : new Color(0x2F6FDD);
        chosenSide = dark ? new Color(0x5B95F5) : new Color(0x5A8FE6);
        chosenSideInside = dark ? new Color(0x232B38) : new Color(0xEDF3FD);
        gutter = dark ? new Color(0x2A2C30) : new Color(0xF1F2F4);
        title = dark ? new Color(0x393D45) : new Color(0x4A5568);
        titleText = Color.WHITE;
        noSide = dark ? new Color(0x27292D) : new Color(0xF2F2F2);
        noSideHatch = dark ? new Color(0x34363B) : new Color(0xE2E2E2);
        changedLine = dark ? new Color(0x3A2326) : new Color(0xFFEDED);
        orphanLine = dark ? new Color(0x2D2740) : new Color(0xF0E8FF);
        movedLine = dark ? new Color(0x1C3336) : new Color(0xE3F4F6);
        otherwiseLine = dark ? new Color(0x3A3220) : new Color(0xFFF6D9);
        changedText = dark ? new Color(0x8C3A40) : new Color(0xFFADAD);
        absentMark = dark ? new Color(0xFF5A5A) : new Color(0xD01010);
        formattingMark = dark ? new Color(0xC77DFF) : new Color(0x9B30D9);
    }

    /** The palette of a view: dark in a dark look, unless it is the document view, a white page. */
    static Palette of(boolean documentView) {
        return lookIsDark() && !documentView ? DARK : LIGHT;
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

    Color line(LineSide.Marking marking) {
        return switch (marking) {
            case SAME -> background;
            case CHANGED -> changedLine;
            case ORPHAN -> orphanLine;
            case MOVED -> movedLine;
            case OTHERWISE -> otherwiseLine;
        };
    }

    Color mark(LineSide.Marking marking) {
        return switch (marking) {
            case SAME -> background;
            case CHANGED -> CHANGED_MARK;
            case ORPHAN -> ORPHAN_MARK;
            case MOVED -> MOVED_MARK;
            case OTHERWISE -> OTHERWISE_MARK;
        };
    }
}
