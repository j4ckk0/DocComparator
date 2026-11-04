package org.jtools.doccomparator.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The status line, under the sides: what the comparison says, on the left; on the right, the current difference and
 * the legend of the colours.
 */
final class StatusLine extends JPanel {

    private final JLabel status = new JLabel(" ");
    private final JLabel position = new JLabel(" ");

    StatusLine() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.add(status);
        add(left, BorderLayout.CENTER);
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        legend.add(position);
        legend.add(legend("text differs", Palette.CHANGED_MARK));
        legend.add(legend("one side only", Palette.ORPHAN_MARK));
        legend.add(legend("moved", Palette.MOVED_MARK));
        legend.add(legend("layout", Palette.OTHERWISE_MARK));
        add(legend, BorderLayout.EAST);
    }

    /** Says this; nothing for blank. */
    void say(String text) {
        status.setText(text.isEmpty() ? " " : text);
    }

    /** Which difference is the current one, from 1, of how many; nothing when it is none of them (0). */
    void showPosition(int current, int count) {
        position.setText(current == 0 ? " " : "difference " + current + " of " + count);
    }

    private static JLabel legend(String text, Color color) {
        JLabel label = new JLabel(text, new Swatch(color), JLabel.LEFT);
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
