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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.jtools.doccomparator.document.WordDocument;
import org.jtools.doccomparator.i18n.I18n;
import org.jtools.doccomparator.i18n.I18nLabels;

/**
 * The head of a side, as Beyond Compare has it: the path of its document, which can be typed (Enter opens it), a
 * button to choose one, what can be said of the document under them; documents dropped on it open on its side, text
 * dropped on it, but not on the path, is compared there. The head of the side text pasted goes to is tinted and
 * underlined, as a tab chosen.
 */
final class DocumentHeader extends JPanel {

    private final Side side;
    private final DocumentOpener opener;
    private final JTextField path = new JTextField();
    private final JLabel about = new FaintLabel(" ", JLabel.LEADING, true);
    private boolean chosen;
    /** The underline's height, under the head chosen. */
    private static final int UNDERLINE = 3;

    /**
     * The head of the left side or the right; open chooses its document; area is what documents are dropped on; its
     * labels bound with these.
     */
    DocumentHeader(Side side, Action open, DocumentOpener opener, JComponent area, I18nLabels labels) {
        super(new BorderLayout(4, 2));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4 + UNDERLINE, 4));
        this.side = side;
        this.opener = opener;
        labels.tip(path, side.of("header.path.left.tip", "header.path.right.tip"));
        path.addActionListener(e -> typed());
        JButton choose = new JButton(open);
        labels.text(choose, "header.open");
        choose.setFocusable(false);
        add(path, BorderLayout.CENTER);
        add(choose, BorderLayout.EAST);
        add(about, BorderLayout.SOUTH);
        // documents dropped on the head, or its path, open on its side; text dropped on the path is typed in it
        setTransferHandler(new DropHandler(opener, area, side));
        path.setTransferHandler(new DropHandler(opener, area, side, path.getTransferHandler()));
    }

    /** Whether its side is the one text pasted goes to: shown tinted and underlined. */
    void setChosen(boolean chosen) {
        this.chosen = chosen;
        repaint();
    }

    boolean chosen() {
        return chosen;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (!chosen) {
            return;
        }
        Palette palette = Palette.of(false);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(palette.chosenSideInside);
            g.fillRoundRect(0, 0, getWidth(), getHeight() + 8, 8, 8);   // rounded at the top only
            g.setColor(palette.chosenSide);
            g.fillRect(0, getHeight() - UNDERLINE, getWidth(), UNDERLINE);
        } finally {
            g.dispose();
        }
    }

    private void typed() {
        String text = path.getText().strip();
        if (text.startsWith("\"") && text.endsWith("\"") && text.length() > 1) {
            text = text.substring(1, text.length() - 1);   // a path copied with its quotes
        }
        if (text.isEmpty()) {
            return;
        }
        try {
            Path typed = Path.of(text);
            if (!Files.isRegularFile(typed)) {
                opener.refuse(I18n.text("header.noSuchDocument", text));
                Toolkit.getDefaultToolkit().beep();
                return;
            }
            opener.open(side, typed);
        } catch (InvalidPathException e) {
            opener.refuse(I18n.text("header.notAPath", text));
            Toolkit.getDefaultToolkit().beep();
        }
    }

    /**
     * Shows what the side compares: a document's path, and what is worth saying of it once read (read null until
     * then); or that it is text pasted, the path left empty to be typed.
     */
    void show(DocumentSource source, WordDocument read) {
        path.setText(source instanceof DocumentSource.File file ? file.path().toAbsolutePath().toString() : "");
        if (source == null) {
            about.setText(" ");
            return;
        }
        List<String> said = new ArrayList<>();
        if (source instanceof DocumentSource.Pasted) {
            said.add(I18n.text("header.pasted"));
        }
        if (read != null && read.template().named() != null) {
            said.add(I18n.text("header.template", read.template().named()));
        }
        if (read != null && read.hasTrackedChanges()) {
            said.add(I18n.text("header.trackedChanges"));
        }
        about.setText(said.isEmpty() ? " " : String.join("  ·  ", said));
    }
}
