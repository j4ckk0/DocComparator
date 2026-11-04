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

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import org.jtools.doccomparator.document.Picture;
import org.jtools.doccomparator.i18n.I18n;
import org.jtools.doccomparator.i18n.I18nLabels;

/**
 * The view's right-click menu: a side's text of the line clicked, copied; what is copied, pasted on the side clicked.
 * A right click makes its line current, and its side the one pasted on.
 */
final class ClipboardMenu extends JPopupMenu {

    private final ComparisonView view;

    /** The menu of this view, pasting with this action; its labels bound with these. */
    ClipboardMenu(ComparisonView view, Action paste, I18nLabels labels) {
        this.view = view;
        JMenuItem copyLeft = labels.text(new JMenuItem(), "copy.left");
        copyLeft.addActionListener(e -> copy(true));
        JMenuItem copyRight = labels.text(new JMenuItem(), "copy.right");
        copyRight.addActionListener(e -> copy(false));
        add(copyLeft);
        add(copyRight);
        addSeparator();
        add(new JMenuItem(paste));
        view.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int line = view.lineAt(e.getY());
                    if (line >= 0 && !view.onWaitingSide(e.getX())) {
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
        String copied = text.toString().replace(String.valueOf(Picture.IN_TEXT), I18n.text("copy.picture"));
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(copied), null);
    }
}
