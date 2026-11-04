package org.jtools.doccomparator.ui;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import org.jtools.doccomparator.document.Picture;

/** The view's right-click menu: a side's text of the line clicked, copied; a right click makes its line current. */
final class CopyMenu extends JPopupMenu {

    private final ComparisonView view;

    CopyMenu(ComparisonView view) {
        this.view = view;
        JMenuItem copyLeft = new JMenuItem("Copy the left text");
        copyLeft.addActionListener(e -> copy(true));
        JMenuItem copyRight = new JMenuItem("Copy the right text");
        copyRight.addActionListener(e -> copy(false));
        add(copyLeft);
        add(copyRight);
        view.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int line = view.lineAt(e.getY());
                    if (line >= 0) {
                        view.setCurrent(line, false);
                    }
                }
            }
        });
    }

    /** The current line's text on a side: a paragraph's, or a row's cells, a tab between them; "[picture]" for each. */
    private void copy(boolean left) {
        int current = view.current();
        if (current < 0) {
            return;
        }
        Line line = view.lines().get(current);
        LineSide side = left ? line.left : line.right;
        if (side == null) {
            return;
        }
        StringBuilder text = new StringBuilder();
        if (side.columns != null) {
            for (LineSide column : side.columns) {
                if (column != null && column.text != null) {
                    text.append(text.isEmpty() ? "" : "\t").append(column.text.text());
                }
            }
        } else {
            text.append(side.text.text());
        }
        String copied = text.toString().replace(String.valueOf(Picture.IN_TEXT), "[picture]");
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(copied), null);
    }
}
