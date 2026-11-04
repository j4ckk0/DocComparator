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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jtools.doccomparator.i18n.I18n;
import org.jtools.doccomparator.i18n.I18nLabels;

/**
 * The status line, under the sides: what the comparison says, on the left; on the right, the current difference and
 * the legend of the colours.
 */
final class StatusLine extends JPanel {

    private final JLabel status = new JLabel(" ");
    private final JLabel position = new JLabel(" ");

    /** The status line, its legend's labels bound with these. */
    StatusLine(I18nLabels labels) {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.add(status);
        add(left, BorderLayout.CENTER);
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        legend.add(position);
        legend.add(labels.text(legend(Palette.CHANGED_MARK), "legend.changed"));
        legend.add(labels.text(legend(Palette.ORPHAN_MARK), "legend.orphan"));
        legend.add(labels.text(legend(Palette.MOVED_MARK), "legend.moved"));
        legend.add(labels.text(legend(Palette.OTHERWISE_MARK), "legend.layout"));
        add(legend, BorderLayout.EAST);
    }

    /** Says this; nothing for blank. */
    void say(String text) {
        status.setText(text.isEmpty() ? " " : text);
    }

    /** Which difference is the current one, from 1, of how many; nothing when it is none of them (0). */
    void showPosition(int current, int count) {
        position.setText(current == 0 ? " " : I18n.text("status.position", current, count));
    }

    private static JLabel legend(Color color) {
        JLabel label = new JLabel(" ", new Swatch(color), JLabel.LEFT);
        label.setFont(label.getFont().deriveFont(label.getFont().getSize2D() - 1));
        return label;
    }

    /** A small square of a colour, for the legend. */
    private record Swatch(Color color) implements Icon {

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(color);
            g.fillRect(x, y + 1, 10, 10);
        }

        @Override
        public int getIconWidth() {
            return 10;
        }

        @Override
        public int getIconHeight() {
            return 12;
        }
    }
}
