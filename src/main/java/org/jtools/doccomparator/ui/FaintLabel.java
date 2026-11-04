package org.jtools.doccomparator.ui;

import java.awt.Color;
import javax.swing.JLabel;

/** A label in the look's greyed text colour, which it takes again when the look changes; a size smaller if asked. */
final class FaintLabel extends JLabel {

    FaintLabel(String text, int alignment, boolean smaller) {
        super(text, alignment);
        if (smaller) {
            setFont(getFont().deriveFont(getFont().getSize2D() - 1));
        }
    }

    @Override
    public void updateUI() {
        super.updateUI();
        setForeground(Palette.ofLook("Label.disabledForeground", Color.GRAY));
    }
}
