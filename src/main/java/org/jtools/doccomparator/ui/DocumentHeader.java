package org.jtools.doccomparator.ui;

import java.awt.BorderLayout;
import java.awt.Toolkit;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.jtools.doccomparator.document.WordDocument;

/**
 * The head of a side, as Beyond Compare has it: the path of its document, which can be typed (Enter opens it), a
 * button to choose one, what can be said of the document under them; documents dropped on it open on its side.
 */
final class DocumentHeader extends JPanel {

    private final boolean onLeft;
    private final DocumentOpener opener;
    private final JTextField path = new JTextField();
    private final JLabel about = new FaintLabel(" ", JLabel.LEADING, true);

    /** The head of the left side or the right; open chooses its document; area is what documents are dropped on. */
    DocumentHeader(boolean onLeft, Action open, DocumentOpener opener, JComponent area) {
        super(new BorderLayout(4, 2));
        this.onLeft = onLeft;
        this.opener = opener;
        path.setToolTipText("The " + (onLeft ? "left" : "right") + " document: type its path and press Enter, "
                + "choose it with Open…, or drop it here");
        path.addActionListener(e -> typed());
        JButton choose = new JButton(open);
        choose.setText("Open…");
        choose.setFocusable(false);
        add(path, BorderLayout.CENTER);
        add(choose, BorderLayout.EAST);
        add(about, BorderLayout.SOUTH);
        // documents dropped on the head, or its path, open on its side; text dropped on the path is typed in it
        setTransferHandler(new DropHandler(opener, area, onLeft));
        path.setTransferHandler(new DropHandler(opener, area, onLeft, path.getTransferHandler()));
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
                opener.refuse("No such document: " + text);
                Toolkit.getDefaultToolkit().beep();
                return;
            }
            opener.open(onLeft, typed);
        } catch (InvalidPathException e) {
            opener.refuse("Not a path: " + text);
            Toolkit.getDefaultToolkit().beep();
        }
    }

    /** Shows the side's document: its path, and what is worth saying of it once read (read null until then). */
    void show(Path document, WordDocument read) {
        path.setText(document == null ? "" : document.toAbsolutePath().toString());
        if (document == null) {
            about.setText(" ");
            return;
        }
        List<String> said = new ArrayList<>();
        if (read != null && read.template().named() != null) {
            said.add("template " + read.template().named());
        }
        if (read != null && read.hasTrackedChanges()) {
            said.add("tracked changes, read as accepted");
        }
        about.setText(said.isEmpty() ? " " : String.join("  ·  ", said));
    }
}
