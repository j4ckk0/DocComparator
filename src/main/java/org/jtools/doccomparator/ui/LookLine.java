package org.jtools.doccomparator.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import javax.swing.border.MatteBorder;

/** A line on some sides of a component, in the look's separator colour of the time. */
final class LookLine extends MatteBorder {

    LookLine(int top, int left, int bottom, int right) {
        super(top, left, bottom, right, Color.GRAY);
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        color = Palette.ofLook("Separator.foreground", Color.GRAY);
        super.paintBorder(c, g, x, y, width, height);
    }
}
