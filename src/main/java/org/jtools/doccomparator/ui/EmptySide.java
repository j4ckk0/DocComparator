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

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.KeyStroke;
import org.jtools.doccomparator.i18n.I18n;

/**
 * What a side says while there are no lines: whether its document is open, a title, then lines of text — which
 * document goes there and how to open it, or the one opened, waiting for the other. Drawn as a place framed with
 * dashes; the side chosen, where text pasted goes, framed in its colour.
 */
record EmptySide(boolean opened, String title, List<String> lines) {

    /** Between a shortcut's keys, as the menus show it: "Ctrl+O". */
    private static final String BETWEEN_KEYS = "+";
    /** The empty line between the ways to open a side and the word on dropping two at once. */
    private static final String SPACER = "";

    /** A side whose document is open, waiting for the other: its document's name. */
    static EmptySide opened(String name) {
        return new EmptySide(true, name, List.of(I18n.text("empty.opened")));
    }

    /**
     * A side without its document: its name (the text of nameKey), then the ways to open it — its shortcut, open,
     * said — or to paste a text there (paste).
     */
    static EmptySide waiting(String nameKey, KeyStroke open, KeyStroke paste) {
        return new EmptySide(false, I18n.text(nameKey), List.of(I18n.text("empty.drop"),
                I18n.text("empty.choose", said(open)), I18n.text("empty.type"), I18n.text("empty.paste", said(paste)),
                SPACER, I18n.text("empty.two")));
    }

    /** A shortcut, its keys named in the language in use: "Ctrl+Shift+O"; "Ctrl+Maj+O" in French. */
    static String said(KeyStroke key) {
        List<String> keys = new ArrayList<>();
        if ((key.getModifiers() & InputEvent.CTRL_DOWN_MASK) != 0) {
            keys.add(I18n.text("key.ctrl"));
        }
        if ((key.getModifiers() & InputEvent.SHIFT_DOWN_MASK) != 0) {
            keys.add(I18n.text("key.shift"));
        }
        keys.add(KeyEvent.getKeyText(key.getKeyCode()));   // a letter, a function key: the same in every language
        return String.join(BETWEEN_KEYS, keys);
    }

    /** Draws it in this place: its frame, then its title and its lines, centred. */
    void paint(Graphics2D g, Rectangle place, boolean chosen, Palette palette, Fonts fonts) {
        int x = place.x;
        int y = place.y;
        int width = place.width;
        int height = place.height;
        g.setColor(opened ? palette.background : chosen ? palette.chosenSideInside : palette.noSide);
        g.fillRoundRect(x, y, width, height, 16, 16);
        g.setColor(chosen ? palette.chosenSide : opened ? palette.grid : palette.faint);
        g.setStroke(new BasicStroke(chosen ? 3 : 2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1,
                new float[] {8, 6}, 0));
        g.drawRoundRect(x, y, width, height, 16, 16);
        g.setStroke(new BasicStroke(1));
        Font heading = fonts.heading().deriveFont(fonts.heading().getSize2D() + 4);
        int lineY = y + height / 2 - ((lines.size() + 1) * g.getFontMetrics(fonts.text()).getHeight()) / 2;
        g.setFont(heading);
        g.setColor(palette.text);
        g.drawString(title, x + Math.max(8, (width - g.getFontMetrics().stringWidth(title)) / 2), lineY);
        lineY += g.getFontMetrics().getHeight() + 8;
        g.setFont(fonts.text());
        g.setColor(palette.faint);
        for (String line : lines) {
            g.drawString(line, x + Math.max(8, (width - g.getFontMetrics().stringWidth(line)) / 2), lineY);
            lineY += g.getFontMetrics().getHeight();
        }
    }
}
