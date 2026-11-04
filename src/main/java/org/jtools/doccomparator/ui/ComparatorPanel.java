package org.jtools.doccomparator.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
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
 * The comparison of two documents, all of it but the window: a toolbar (the sides, the differences, the options), the
 * name of each document above its side, the two sides, the map of the differences, and a status line. Documents are
 * opened from the toolbar, by dropping them on their side, or given at the start.
 */
public final class ComparatorPanel extends JPanel {

    private static final String HINT = "Open the two documents to compare: the buttons above, or drop them here\n"
            + "(both at once, or each on its side).";

    private final ComparisonView view = new ComparisonView();
    private final JScrollPane scroll = new JScrollPane(view);
    private final DocumentHeader leftHeader = new DocumentHeader("Left");
    private final DocumentHeader rightHeader = new DocumentHeader("Right");
    private final JLabel status = new JLabel(" ");
    private final JLabel position = new JLabel(" ");
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

    private Path leftPath;
    private Path rightPath;
    private WordDocument leftDocument;
    private WordDocument rightDocument;
    private CompareOptions options = CompareOptions.DEFAULT;
    private boolean onlyDifferences;
    private Comparison comparison;
    private SwingWorker<?, ?> running;
    private Consumer<String> titleListener = title -> { };
    private File lastDirectory;

    public ComparatorPanel() {
        super(new BorderLayout());
        view.setEmptyMessage(HINT);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel headers = new JPanel(new GridLayout(1, 2, 26, 0));
        headers.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4 + 16));
        headers.add(leftHeader);
        headers.add(rightHeader);
        JPanel top = new JPanel(new BorderLayout());
        top.add(toolBar(), BorderLayout.NORTH);
        top.add(headers, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout());
        center.add(scroll, BorderLayout.CENTER);
        center.add(new DifferenceMap(view, scroll), BorderLayout.EAST);
        add(center, BorderLayout.CENTER);
        add(statusBar(), BorderLayout.SOUTH);

        view.addPropertyChangeListener(ComparisonView.CURRENT_LINE, e -> updatePosition());
        view.setComponentPopupMenu(popup());
        TransferHandler drop = new DropHandler();
        view.setTransferHandler(drop);
        setTransferHandler(drop);
        for (Action action : List.of(openLeft, openRight, previous, next)) {
            KeyStroke key = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(key, action);
            getActionMap().put(action, action);
        }
        updateActions();
    }

    /** Told the window's title each time the documents change. */
    public void onTitle(Consumer<String> listener) {
        titleListener = listener;
        listener.accept(title());
    }

    public List<Action> fileActions() {
        return List.of(openLeft, openRight, swap);
    }

    public List<Action> goActions() {
        return List.of(previous, next);
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

    private void open(boolean onLeft, Path path) {
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
        if (leftPath == null || rightPath == null) {
            comparison = null;
            view.setLines(List.of(), -1);
            status.setText(leftPath == null && rightPath == null ? " " : "Open the other document to compare.");
            return;
        }
        if (running != null) {
            running.cancel(false);
        }
        Path left = leftPath;
        Path right = rightPath;
        WordDocument knownLeft = leftDocument;
        WordDocument knownRight = rightDocument;
        CompareOptions with = options;
        status.setText("Comparing…");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        SwingWorker<Comparison, Void> worker = new SwingWorker<>() {
            @Override
            protected Comparison doInBackground() throws IOException {
                WordDocument a = knownLeft != null ? knownLeft : read(left);
                WordDocument b = knownRight != null ? knownRight : read(right);
                return DocumentComparer.compare(a, b, with);
            }

            @Override
            protected void done() {
                if (isCancelled() || running != this) {
                    return;
                }
                running = null;
                setCursor(null);
                try {
                    Comparison result = get();
                    leftDocument = result.left();
                    rightDocument = result.right();
                    show(result);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    status.setText("Not compared: " + cause.getMessage());
                    JOptionPane.showMessageDialog(ComparatorPanel.this, cause.getMessage(), "Cannot compare",
                            JOptionPane.ERROR_MESSAGE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        running = worker;
        worker.execute();
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
        view.setLines(Lines.of(result, onlyDifferences, view.fonts()), -1);
        SwingUtilities.invokeLater(() -> scroll.getViewport().setViewPosition(new Point(0, Math.min(at.y,
                Math.max(0, view.getHeight() - scroll.getViewport().getHeight())))));
        int[] count = view.differenceCount();
        status.setText(result.same() ? "The documents are the same, as compared."
                : count[0] + (count[0] == 1 ? " difference" : " differences") + summary(result));
        updatePosition();
        updateActions();
    }

    private static String summary(Comparison comparison) {
        StringBuilder summary = new StringBuilder();
        if (comparison.left().hasTrackedChanges() || comparison.right().hasTrackedChanges()) {
            summary.append("  ·  tracked changes read as accepted (");
            summary.append(comparison.left().hasTrackedChanges() && comparison.right().hasTrackedChanges() ? "both sides"
                    : comparison.left().hasTrackedChanges() ? "left side" : "right side").append(')');
        }
        return summary.toString();
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
        position.setText(count[1] == 0 ? " " : "difference " + count[1] + " of " + count[0]);
        updateActions();
    }

    private void updateActions() {
        boolean compared = comparison != null;
        previous.setEnabled(compared && view.difference(false) >= 0);
        next.setEnabled(compared && view.difference(true) >= 0);
        swap.setEnabled(leftPath != null || rightPath != null);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // The parts of the panel

    private JToolBar toolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.add(button(openLeft));
        bar.add(button(openRight));
        bar.add(button(swap));
        bar.addSeparator();
        bar.add(button(previous));
        bar.add(button(next));
        bar.addSeparator();
        bar.add(new JLabel("Compare:  "));
        JCheckBox text = new JCheckBox("Text", true);
        text.setEnabled(false);
        text.setToolTipText("The text is always compared");
        bar.add(text);
        bar.add(option("Structure", "Headings, lists, tables' rows and columns counted, sections",
                options.structure(), on -> options = options.withStructure(on)));
        bar.add(option("Blanks", "Spaces, tabs, breaks and empty paragraphs; left out, they are shown but do not count",
                options.blanks(), on -> options = options.withBlanks(on)));
        bar.add(option("Template", "The template attached, and the one named", options.template(),
                on -> options = options.withTemplate(on)));
        bar.add(option("Headers and footers", "Their text, as a part of its own", options.headersAndFooters(),
                on -> options = options.withHeadersAndFooters(on)));
        bar.add(option("Styles", "Paragraph and character styles by name, the formatting set on the text, style definitions",
                options.styles(), on -> options = options.withStyles(on)));
        bar.addSeparator();
        JCheckBox only = new JCheckBox("Only differences");
        only.setToolTipText("Hide the lines that are the same");
        only.addItemListener(e -> {
            onlyDifferences = only.isSelected();
            if (comparison != null) {
                show(comparison);
            }
        });
        bar.add(only);
        bar.add(Box.createHorizontalGlue());
        return bar;
    }

    private JCheckBox option(String name, String tip, boolean on, Consumer<Boolean> set) {
        JCheckBox box = new JCheckBox(name, on);
        box.setToolTipText(tip);
        box.addItemListener(e -> {
            set.accept(box.isSelected());
            compare();
        });
        return box;
    }

    private static JButton button(Action action) {
        JButton button = new JButton(action);
        button.setFocusable(false);
        return button;
    }

    private JPanel statusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.add(status);
        bar.add(left, BorderLayout.CENTER);
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        legend.add(position);
        legend.add(legend("text differs", Palette.CHANGED_MARK));
        legend.add(legend("one side only", Palette.ORPHAN_MARK));
        legend.add(legend("moved", Palette.MOVED_MARK));
        legend.add(legend("structure, style", Palette.OTHERWISE_MARK));
        legend.add(legend("blanks", new Color(0x7FA6F0)));
        bar.add(legend, BorderLayout.EAST);
        return bar;
    }

    private static JLabel legend(String text, Color color) {
        JLabel label = new JLabel(text, new Swatch(color), JLabel.LEFT);
        label.setFont(label.getFont().deriveFont(label.getFont().getSize2D() - 1));
        return label;
    }

    private JPopupMenu popup() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem copyLeft = new JMenuItem("Copy the left text");
        copyLeft.addActionListener(e -> copy(true));
        JMenuItem copyRight = new JMenuItem("Copy the right text");
        copyRight.addActionListener(e -> copy(false));
        menu.add(copyLeft);
        menu.add(copyRight);
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
        return menu;
    }

    /** The text of the current line's side: a paragraph's, or a table row's cells, a tab between them. */
    private void copy(boolean left) {
        int current = view.current();
        if (current < 0) {
            return;
        }
        Line line = view.lines().get(current);
        Cell cell = left ? line.left : line.right;
        if (cell == null) {
            return;
        }
        StringBuilder text = new StringBuilder();
        if (cell.columns != null) {
            for (Cell column : cell.columns) {
                if (column != null && column.text != null) {
                    text.append(text.isEmpty() ? "" : "\t").append(column.text.text());
                }
            }
        } else {
            text.append(cell.text.text());
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text.toString()), null);
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

    // -----------------------------------------------------------------------------------------------------------------

    /** The name of a side's document, its folder, and what can be said of it. */
    private static final class DocumentHeader extends JPanel {

        private final String side;
        private final JLabel name = new JLabel();
        private final JLabel folder = new JLabel();

        DocumentHeader(String side) {
            super(new BorderLayout());
            this.side = side;
            name.setFont(name.getFont().deriveFont(Font.BOLD));
            folder.setFont(folder.getFont().deriveFont(folder.getFont().getSize2D() - 1));
            folder.setForeground(Palette.FAINT);
            add(name, BorderLayout.NORTH);
            add(folder, BorderLayout.SOUTH);
            show(null, null);
        }

        void show(Path path, WordDocument document) {
            if (path == null) {
                name.setText(side + ": no document");
                folder.setText(" ");
                setToolTipText(null);
                return;
            }
            name.setText(side + ": " + path.getFileName());
            Path parent = path.toAbsolutePath().getParent();
            String template = document == null || document.template().named() == null ? ""
                    : "  ·  template " + document.template().named();
            folder.setText((parent == null ? "" : parent.toString()) + template);
            setToolTipText(path.toAbsolutePath().toString());
        }
    }

    /** Documents dropped on the panel: two at once, left and right; one, on the side it is dropped on. */
    private final class DropHandler extends TransferHandler {

        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDrop() && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            try {
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                if (files.size() >= 2) {
                    open(files.get(0).toPath(), files.get(1).toPath());
                } else if (files.size() == 1) {
                    Component target = support.getComponent();
                    Point at = SwingUtilities.convertPoint(target, support.getDropLocation().getDropPoint(),
                            ComparatorPanel.this);
                    open(at.x < getWidth() / 2, files.getFirst().toPath());
                }
                return true;
            } catch (UnsupportedFlavorException | IOException e) {
                return false;
            }
        }
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

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(1280, 820);
    }
}
