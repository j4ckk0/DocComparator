package org.jtools.doccomparator.ui;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
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
import org.jtools.doccomparator.docx.DocxReader;

/**
 * The comparison of two documents, all of it but the window: the ribbon (OptionControls), the head of each side
 * (DocumentHeader), the two sides, the map of the differences, and the status line (StatusLine). Keeps the
 * documents, reads and compares them away from the window's thread, and goes through the differences. Documents are
 * opened from a side's head, by dropping them on their side, or given at the start.
 */
public final class ComparatorPanel extends JPanel {

    private final ComparisonView view = new ComparisonView();
    private final JScrollPane scroll = new JScrollPane(view);
    private final StatusLine statusLine = new StatusLine();
    private final Action openLeft = action("Open left…", "Open the left document",
            KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK), () -> choose(true));
    private final Action openRight = action("Open right…", "Open the right document",
            KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
            () -> choose(false));
    private final Action swap = action("Swap sides", "Put the left document on the right, and the right on the left",
            null, this::swap);
    private final Action previous = action("▲ Previous", "Go to the previous difference (Shift+F7)",
            KeyStroke.getKeyStroke(KeyEvent.VK_F7, InputEvent.SHIFT_DOWN_MASK), () -> goToDifference(false));
    private final Action next = action("Next ▼", "Go to the next difference (F7)",
            KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0), () -> goToDifference(true));
    private final OptionControls controls = new OptionControls(this::compare, this::shownChanged);
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
        public boolean leftOpened() {
            return leftPath != null;
        }

        @Override
        public void refuse(String why) {
            statusLine.say(why);
        }
    };
    private final DocumentHeader leftHeader;
    private final DocumentHeader rightHeader;

    private Path leftPath;
    private Path rightPath;
    private WordDocument leftDocument;
    private WordDocument rightDocument;
    private Comparison comparison;
    private SwingWorker<?, ?> running;
    private Consumer<String> titleListener = title -> { };
    private File lastDirectory;

    public ComparatorPanel() {
        super(new BorderLayout());
        leftHeader = new DocumentHeader(true, openLeft, opener, this);
        rightHeader = new DocumentHeader(false, openRight, opener, this);
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
        center.add(new DifferenceMap(view, scroll), BorderLayout.EAST);
        add(center, BorderLayout.CENTER);
        add(statusLine, BorderLayout.SOUTH);

        view.addPropertyChangeListener(ComparisonView.CURRENT_LINE, e -> updatePosition());
        view.setComponentPopupMenu(new CopyMenu(view));
        TransferHandler drop = new DropHandler(opener, this, null);
        view.setTransferHandler(drop);
        setTransferHandler(drop);
        for (Action action : List.of(openLeft, openRight, previous, next)) {
            KeyStroke key = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(key, action);
            getActionMap().put(action, action);
        }
        showEmptyView();
        updateActions();
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
        return List.of(openLeft, openRight, swap);
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
        leftPath = left;
        rightPath = right;
        leftDocument = null;
        rightDocument = null;
        compare();
    }

    void open(boolean onLeft, Path path) {
        if (onLeft) {
            leftPath = path;
            leftDocument = null;
        } else {
            rightPath = path;
            rightDocument = null;
        }
        compare();
    }

    private void choose(boolean onLeft) {
        JFileChooser chooser = new JFileChooser(lastDirectory);
        chooser.setDialogTitle(onLeft ? "Open the left document" : "Open the right document");
        chooser.setFileFilter(new FileNameExtensionFilter("Word documents (*.docx, *.docm, *.dotx, *.dotm)",
                "docx", "docm", "dotx", "dotm"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            lastDirectory = chooser.getCurrentDirectory();
            open(onLeft, chooser.getSelectedFile().toPath());
        }
    }

    private void swap() {
        Path path = leftPath;
        leftPath = rightPath;
        rightPath = path;
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
        leftHeader.show(leftPath, null);
        rightHeader.show(rightPath, null);
        titleListener.accept(title());
        updateActions();
        if (running != null) {
            running.cancel(false);
            running = null;
            setCursor(null);
        }
        showEmptySides();
        if (leftPath == null && rightPath == null) {
            comparison = null;
            showEmptyView();
            view.setWaitingSide(-1);
            statusLine.say("");
            return;
        }
        if (leftPath == null || rightPath == null) {
            showAlone(leftPath != null);
            return;
        }
        Path left = leftPath;
        Path right = rightPath;
        WordDocument knownLeft = leftDocument;
        WordDocument knownRight = rightDocument;
        CompareOptions options = controls.options();
        inBackground("Comparing…", () -> {
            WordDocument a = knownLeft != null ? knownLeft : read(left);
            WordDocument b = knownRight != null ? knownRight : read(right);
            PictureImages.prepare(a, b);   // decoded here, away from the window's thread
            return DocumentComparer.compare(a, b, options);
        }, result -> {
            leftDocument = result.left();
            rightDocument = result.right();
            show(result);
        }, "Not compared", "Cannot compare");
    }

    /**
     * One document open, the other not yet: the one shown on its side as it is, nothing compared; the other side a
     * place for its document — as Beyond Compare shows a file before the other.
     */
    private void showAlone(boolean onLeft) {
        comparison = null;
        Path path = onLeft ? leftPath : rightPath;
        WordDocument known = onLeft ? leftDocument : rightDocument;
        inBackground("Reading…", () -> {
            WordDocument document = known != null ? known : read(path);
            PictureImages.prepare(document);
            return document;
        }, document -> {
            if (onLeft) {
                leftDocument = document;
                leftHeader.show(path, document);
            } else {
                rightDocument = document;
                rightHeader.show(path, document);
            }
            Lines.Shown shown = controls.shown();
            Palette palette = Palette.of(shown.documentView());
            view.setLines(Lines.alone(document, onLeft, shown, view.fonts(), palette), -1, palette);
            view.setWaitingSide(onLeft ? 1 : 0);
            statusLine.say("Open the other document to compare.");
            updateActions();
        }, "Not read", "Cannot read");
    }

    /**
     * Runs work away from the window's thread, the status saying so and the cursor waiting, then gives its result to
     * done on the window's thread — unless other work has replaced it. A failure is said in the status line and a
     * message: "failed: why", under this title.
     */
    private <T> void inBackground(String saying, Callable<T> work, Consumer<T> done, String failed, String title) {
        statusLine.say(saying);
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
                    statusLine.say(failed + ": " + cause.getMessage());
                    JOptionPane.showMessageDialog(ComparatorPanel.this, cause.getMessage(), title,
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
        view.setEmptySides(emptySide(leftPath, "Left", "Ctrl+O"), emptySide(rightPath, "Right", "Ctrl+Shift+O"));
    }

    private static ComparisonView.EmptySide emptySide(Path path, String side, String key) {
        if (path != null) {
            return new ComparisonView.EmptySide(true, String.valueOf(path.getFileName()),
                    List.of("opened — now the other document"));
        }
        return new ComparisonView.EmptySide(false, side + " document", List.of("Drop a Word document here,",
                "or choose it with Open… above (" + key + "),", "or type its path above.", "",
                "Two dropped at once: the first on the left."));
    }

    private static WordDocument read(Path path) throws IOException {
        try {
            return DocxReader.read(path);
        } catch (IOException e) {
            throw new IOException(path.getFileName() + ": " + e.getMessage(), e);
        }
    }

    /** Shows a comparison, keeping the place the view was at. */
    public void show(Comparison result) {
        comparison = result;
        leftHeader.show(leftPath, result.left());
        rightHeader.show(rightPath, result.right());
        Point at = scroll.getViewport().getViewPosition();
        view.setWaitingSide(-1);
        Lines.Shown shown = controls.shown();
        Palette palette = Palette.of(shown.documentView());
        view.setLines(Lines.of(result, shown, view.fonts(), palette), -1, palette);
        SwingUtilities.invokeLater(() -> scroll.getViewport().setViewPosition(new Point(0, Math.min(at.y,
                Math.max(0, view.getHeight() - scroll.getViewport().getHeight())))));
        int[] count = view.differenceCount();
        statusLine.say(result.same() ? "The documents are the same, as compared."
                : count[0] + (count[0] == 1 ? " difference" : " differences"));
        updatePosition();
        updateActions();
    }

    /** The lines to show have changed (the view, if viewChanged): shown again, the document alone read again. */
    private void shownChanged(boolean viewChanged) {
        if (comparison != null) {
            show(comparison);
        } else if (viewChanged && (leftPath != null || rightPath != null)) {
            compare();
        }
    }

    String title() {
        String app = "DocComparator";
        if (leftPath == null && rightPath == null) {
            return app;
        }
        return name(leftPath) + " ↔ " + name(rightPath) + " — " + app;
    }

    private static String name(Path path) {
        return path == null ? "…" : String.valueOf(path.getFileName());
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
        swap.setEnabled(leftPath != null || rightPath != null);
    }

    private static Action action(String name, String tip, KeyStroke key, Runnable run) {
        Action action = new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent e) {
                run.run();
            }
        };
        action.putValue(Action.SHORT_DESCRIPTION, tip);
        if (key != null) {
            action.putValue(Action.ACCELERATOR_KEY, key);
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
            } else if (leftPath != null || rightPath != null) {
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
