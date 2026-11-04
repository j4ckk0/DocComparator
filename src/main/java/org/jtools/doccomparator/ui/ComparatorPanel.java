package org.jtools.doccomparator.ui;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.jtools.doccomparator.compare.CompareOptions;
import org.jtools.doccomparator.compare.Comparison;
import org.jtools.doccomparator.compare.DocumentComparer;
import org.jtools.doccomparator.document.WordDocument;
import org.jtools.doccomparator.i18n.I18n;
import org.jtools.doccomparator.i18n.I18nLabels;

/**
 * The comparison of two documents, all of it but the window: the ribbon (OptionControls), the head of each side
 * (DocumentHeader), the two sides, the map of the differences, and the status line (StatusLine). Keeps the
 * documents, reads and compares them away from the window's thread, and goes through the differences. Documents are
 * opened from a side's head, by dropping them on their side, or given at the start; text pasted or dropped on a side
 * is compared as a document.
 */
public final class ComparatorPanel extends JPanel {

    private final ComparisonView view = new ComparisonView();
    private final JScrollPane scroll = new JScrollPane(view);
    /** The labels of the panel and of its parts, which it gives them: set again together when the language changes. */
    private final I18nLabels labels = new I18nLabels();
    private final StatusLine statusLine = new StatusLine(labels);
    private final Action openLeft = action("action.openLeft", "action.openLeft.tip",
            KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK), () -> choose(true));
    private final Action openRight = action("action.openRight", "action.openRight.tip",
            KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
            () -> choose(false));
    private final Action pasteLeft = action("action.pasteLeft", "action.pasteLeft.tip",
            KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), () -> paste(true));
    private final Action pasteRight = action("action.pasteRight", "action.pasteRight.tip",
            KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
            () -> paste(false));
    private final Action swap = action("action.swap", "action.swap.tip", null, this::swap);
    private final Action previous = action("action.previous", "action.previous.tip",
            KeyStroke.getKeyStroke(KeyEvent.VK_F7, InputEvent.SHIFT_DOWN_MASK), () -> goToDifference(false));
    private final Action next = action("action.next", "action.next.tip", KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0),
            () -> goToDifference(true));
    private final OptionControls controls = new OptionControls(labels, this::compare, this::shownChanged);
    private final DocumentOpener opener = new DocumentOpener() {
        @Override
        public void open(boolean onLeft, Path path) {
            ComparatorPanel.this.open(onLeft, path);
        }

        @Override
        public void open(Path left, Path right) {
            ComparatorPanel.this.open(left, right);
        }

        @Override
        public void paste(boolean onLeft, String text) {
            ComparatorPanel.this.paste(onLeft, text);
        }

        @Override
        public boolean leftOpened() {
            return left != null;
        }

        @Override
        public void refuse(String why) {
            statusLine.say(why);
        }
    };
    private final DocumentHeader leftHeader;
    private final DocumentHeader rightHeader;

    private DocumentSource left;
    private DocumentSource right;
    private WordDocument leftDocument;
    private WordDocument rightDocument;
    private Comparison comparison;
    private SwingWorker<?, ?> running;
    private Consumer<String> titleListener = title -> { };
    private File lastDirectory;

    public ComparatorPanel() {
        super(new BorderLayout());
        leftHeader = new DocumentHeader(true, openLeft, opener, this, labels);
        rightHeader = new DocumentHeader(false, openRight, opener, this, labels);
        showEmptySides();
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel headers = new JPanel(new GridLayout(1, 2, 26, 0));
        headers.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4 + 16));
        headers.add(leftHeader);
        headers.add(rightHeader);
        JPanel top = new JPanel(new BorderLayout());
        top.add(controls.ribbon(previous, next, swap), BorderLayout.NORTH);
        top.add(headers, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout());
        center.add(scroll, BorderLayout.CENTER);
        center.add(new DifferenceMap(view, scroll, labels), BorderLayout.EAST);
        add(center, BorderLayout.CENTER);
        add(statusLine, BorderLayout.SOUTH);

        view.addPropertyChangeListener(ComparisonView.CURRENT_LINE, e -> updatePosition());
        view.setComponentPopupMenu(new CopyMenu(view, labels));
        TransferHandler drop = new DropHandler(opener, this, null);
        view.setTransferHandler(drop);
        setTransferHandler(drop);
        for (Action action : List.of(openLeft, openRight, pasteLeft, pasteRight, previous, next)) {
            KeyStroke key = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(key, action);
            getActionMap().put(action, action);
        }
        showEmptyView();
        updateActions();
    }

    /**
     * The language has changed: every label in it, and the documents read again — the names of their parts, and what
     * the comparison says of them, are written in it as they are read and compared.
     */
    public void languageChanged() {
        labels.apply();
        leftDocument = null;
        rightDocument = null;
        compare();
    }

    /** Told the window's title each time the documents change. */
    public void onTitle(Consumer<String> listener) {
        titleListener = listener;
        listener.accept(title());
    }

    /** Told when the Dark box of the ribbon is ticked or not: the window changes its look. */
    public void onDarkMode(Consumer<Boolean> listener) {
        controls.onDarkMode(listener);
    }

    /** Shows whether the look is dark in the ribbon's Dark box, telling no one. */
    public void showDarkMode(boolean dark) {
        controls.showDarkMode(dark);
    }

    public List<Action> fileActions() {
        return List.of(openLeft, openRight, pasteLeft, pasteRight, swap);
    }

    public List<Action> goActions() {
        return List.of(previous, next);
    }

    /** The views, as a menu of the window. */
    public JMenu viewMenu() {
        return controls.viewMenu();
    }

    /** What is compared, as a menu of the window. */
    public JMenu compareMenu() {
        return controls.compareMenu();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // The documents

    /** Opens these two documents and compares them. */
    public void open(Path left, Path right) {
        this.left = left == null ? null : new DocumentSource.File(left);
        this.right = right == null ? null : new DocumentSource.File(right);
        leftDocument = null;
        rightDocument = null;
        compare();
    }

    void open(boolean onLeft, Path path) {
        put(onLeft, new DocumentSource.File(path));
    }

    /** Compares this text on a side, as a document of a paragraph a line (TextReader). */
    void paste(boolean onLeft, String text) {
        put(onLeft, new DocumentSource.Pasted(text));
    }

    private void put(boolean onLeft, DocumentSource source) {
        if (onLeft) {
            left = source;
            leftDocument = null;
        } else {
            right = source;
            rightDocument = null;
        }
        compare();
    }

    /** What the clipboard holds, on a side: text compared as a document, documents opened as dropped ones are. */
    private void paste(boolean onLeft) {
        Transferable held;
        try {
            held = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
        } catch (IllegalStateException e) {   // another program has it at that moment
            held = null;
        }
        if (held == null) {
            statusLine.say(I18n.text("paste.nothing"));
            return;
        }
        new DropHandler(opener, this, onLeft).importData(new TransferHandler.TransferSupport(this, held));
    }

    private void choose(boolean onLeft) {
        JFileChooser chooser = new JFileChooser(lastDirectory);
        chooser.setDialogTitle(I18n.text(onLeft ? "chooser.left" : "chooser.right"));
        chooser.setFileFilter(new FileNameExtensionFilter(I18n.text("chooser.wordDocuments"),
                "docx", "docm", "dotx", "dotm"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            lastDirectory = chooser.getCurrentDirectory();
            open(onLeft, chooser.getSelectedFile().toPath());
        }
    }

    private void swap() {
        DocumentSource source = left;
        left = right;
        right = source;
        WordDocument document = leftDocument;
        leftDocument = rightDocument;
        rightDocument = document;
        compare();
    }

    /**
     * Reads what is not read yet, then compares, away from the window's thread; shows the result. A comparison asked
     * for while another runs replaces it.
     */
    private void compare() {
        leftHeader.show(left, null);
        rightHeader.show(right, null);
        titleListener.accept(title());
        updateActions();
        if (running != null) {
            running.cancel(false);
            running = null;
            setCursor(null);
        }
        showEmptySides();
        if (left == null && right == null) {
            comparison = null;
            showEmptyView();
            view.setWaitingSide(-1);
            statusLine.say("");
            return;
        }
        if (left == null || right == null) {
            showAlone(left != null);
            return;
        }
        DocumentSource leftSource = left;
        DocumentSource rightSource = right;
        WordDocument knownLeft = leftDocument;
        WordDocument knownRight = rightDocument;
        CompareOptions options = controls.options();
        inBackground("status.comparing", () -> {
            WordDocument a = knownLeft != null ? knownLeft : leftSource.read();
            WordDocument b = knownRight != null ? knownRight : rightSource.read();
            PictureImages.prepare(a, b);   // decoded here, away from the window's thread
            return DocumentComparer.compare(a, b, options);
        }, result -> {
            leftDocument = result.left();
            rightDocument = result.right();
            show(result);
        }, "status.notCompared", "dialog.cannotCompare");
    }

    /**
     * One document open, the other not yet: the one shown on its side as it is, nothing compared; the other side a
     * place for its document — as Beyond Compare shows a file before the other.
     */
    private void showAlone(boolean onLeft) {
        comparison = null;
        DocumentSource source = onLeft ? left : right;
        WordDocument known = onLeft ? leftDocument : rightDocument;
        inBackground("status.reading", () -> {
            WordDocument document = known != null ? known : source.read();
            PictureImages.prepare(document);
            return document;
        }, document -> {
            if (onLeft) {
                leftDocument = document;
                leftHeader.show(source, document);
            } else {
                rightDocument = document;
                rightHeader.show(source, document);
            }
            Lines.Shown shown = controls.shown();
            Palette palette = Palette.of(shown.documentView());
            view.setLines(Lines.alone(document, onLeft, shown, view.fonts(), palette), -1, palette);
            view.setWaitingSide(onLeft ? 1 : 0);
            statusLine.say(I18n.text("status.openTheOther"));
            updateActions();
        }, "status.notRead", "dialog.cannotRead");
    }

    /**
     * Runs work away from the window's thread, the status saying so (the text of saying) and the cursor waiting, then
     * gives its result to done on the window's thread — unless other work has replaced it. A failure is said in the
     * status line (failed's text, why as its argument) and a message, under title's text.
     */
    private <T> void inBackground(String saying, Callable<T> work, Consumer<T> done, String failed, String title) {
        statusLine.say(I18n.text(saying));
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        SwingWorker<T, Void> worker = new SwingWorker<>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.call();
            }

            @Override
            protected void done() {
                if (isCancelled() || running != this) {
                    return;
                }
                running = null;
                setCursor(null);
                try {
                    done.accept(get());
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    statusLine.say(I18n.text(failed, cause.getMessage()));
                    JOptionPane.showMessageDialog(ComparatorPanel.this, cause.getMessage(), I18n.text(title),
                            JOptionPane.ERROR_MESSAGE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        running = worker;
        worker.execute();
    }

    /** No document open: the two empty sides, in the colours of the look in use. */
    private void showEmptyView() {
        view.setLines(List.of(), -1, Palette.of(controls.shown().documentView()));
    }

    /** While a side has no document: what goes there, and how to open it; the side opened, its document's name. */
    private void showEmptySides() {
        view.setEmptySides(emptySide(left, "empty.left", "Ctrl+O", "Ctrl+V"),
                emptySide(right, "empty.right", "Ctrl+Shift+O", "Ctrl+Shift+V"));
    }

    /**
     * A side without its document: its name (the text of side), then how to open it, or paste a text, their shortcuts
     * said; or opened.
     */
    private static ComparisonView.EmptySide emptySide(DocumentSource source, String side, String open, String paste) {
        if (source != null) {
            return new ComparisonView.EmptySide(true, source.name(), List.of(I18n.text("empty.opened")));
        }
        return new ComparisonView.EmptySide(false, I18n.text(side), List.of(I18n.text("empty.drop"),
                I18n.text("empty.choose", open), I18n.text("empty.type"), I18n.text("empty.paste", paste), "",
                I18n.text("empty.two")));
    }

    /** Shows a comparison, keeping the place the view was at. */
    public void show(Comparison result) {
        comparison = result;
        leftHeader.show(left, result.left());
        rightHeader.show(right, result.right());
        Point at = scroll.getViewport().getViewPosition();
        view.setWaitingSide(-1);
        Lines.Shown shown = controls.shown();
        Palette palette = Palette.of(shown.documentView());
        view.setLines(Lines.of(result, shown, view.fonts(), palette), -1, palette);
        SwingUtilities.invokeLater(() -> scroll.getViewport().setViewPosition(new Point(0, Math.min(at.y,
                Math.max(0, view.getHeight() - scroll.getViewport().getHeight())))));
        int[] count = view.differenceCount();
        statusLine.say(result.same() ? I18n.text("status.same") : Lines.count(count[0]));
        updatePosition();
        updateActions();
    }

    /** The lines to show have changed (the view, if viewChanged): shown again, the document alone read again. */
    private void shownChanged(boolean viewChanged) {
        if (comparison != null) {
            show(comparison);
        } else if (viewChanged && (left != null || right != null)) {
            compare();
        }
    }

    String title() {
        String app = "DocComparator";
        if (left == null && right == null) {
            return app;
        }
        return name(left) + " ↔ " + name(right) + " — " + app;
    }

    private static String name(DocumentSource source) {
        return source == null ? "…" : source.name();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Going through the differences

    private void goToDifference(boolean forward) {
        int line = view.difference(forward);
        if (line >= 0) {
            view.setCurrent(line, true);
        } else {
            Toolkit.getDefaultToolkit().beep();
        }
    }

    private void updatePosition() {
        int[] count = view.differenceCount();
        statusLine.showPosition(count[1], count[0]);
        updateActions();
    }

    private void updateActions() {
        boolean compared = comparison != null;
        previous.setEnabled(compared && view.difference(false) >= 0);
        next.setEnabled(compared && view.difference(true) >= 0);
        swap.setEnabled(left != null || right != null);
    }

    /** An action named by the text of nameKey, its tooltip tipKey's. */
    private Action action(String nameKey, String tipKey, KeyStroke shortcut, Runnable run) {
        Action action = labels.action(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                run.run();
            }
        }, nameKey, tipKey);
        if (shortcut != null) {
            action.putValue(Action.ACCELERATOR_KEY, shortcut);
        }
        return action;
    }

    /**
     * Another look (View ▸ Look): once every part of the window has taken it, the comparison shown again, laid out in
     * its fonts; the right-click menu, not a part of the window, told too.
     */
    @Override
    public void updateUI() {
        super.updateUI();
        if (view == null) {
            return;   // called as the panel is made, before its parts
        }
        SwingUtilities.invokeLater(() -> {
            if (view.getComponentPopupMenu() != null) {
                SwingUtilities.updateComponentTreeUI(view.getComponentPopupMenu());
            }
            if (comparison != null) {
                show(comparison);
            } else if (left != null || right != null) {
                compare();
            } else {
                showEmptyView();
            }
        });
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(1280, 820);
    }
}
