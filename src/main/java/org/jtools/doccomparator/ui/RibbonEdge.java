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

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;
import javax.swing.border.AbstractBorder;

/**
 * The ribbon's lower edge, setting it apart from the documents: a line more marked than the look's separators, and a
 * shade fading under it; in the look's colours of the time, darker in a light look, lighter in a dark one.
 */
final class RibbonEdge extends AbstractBorder {

    /** The line's opacity, then the shade's, row by row from the top. */
    private static final int[] ROWS = {255, 70, 25};

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Color separator = Palette.ofLook("Separator.foreground", Color.GRAY);
        Color line = Palette.lookIsDark() ? separator.brighter() : separator.darker();
        int top = y + height - ROWS.length;
        for (int row = 0; row < ROWS.length; row++) {
            g.setColor(new Color(line.getRed(), line.getGreen(), line.getBlue(), ROWS[row]));
            g.fillRect(x, top + row, width, 1);
        }
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
        insets.set(0, 0, ROWS.length, 0);
        return insets;
    }
}
