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
import java.awt.Insets;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.jtools.doccomparator.compare.CompareOptions;
import org.jtools.doccomparator.compare.CompareOptions.Aspect;
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


    private final ComparisonView view = new ComparisonView();
    private final JScrollPane scroll = new JScrollPane(view);
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
    // after the actions: their Open… buttons are made of them (fields are made in the order they are written)
    private final DocumentHeader leftHeader = new DocumentHeader(true);
    private final DocumentHeader rightHeader = new DocumentHeader(false);

    private Path leftPath;
    private Path rightPath;
    private WordDocument leftDocument;
    private WordDocument rightDocument;
    private CompareOptions options = CompareOptions.DEFAULT;
    private boolean onlyDifferences;
    private boolean onlyComparable;
    /** The document view, each paragraph laid out as in Word; the diff view otherwise. */
    private boolean documentView;
    private final List<AbstractButton> viewButtons = new ArrayList<>();
    private Comparison comparison;
    private SwingWorker<?, ?> running;
    private Consumer<String> titleListener = title -> { };
    private File lastDirectory;

    public ComparatorPanel() {
        super(new BorderLayout());
        showEmptySides();
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel headers = new JPanel(new GridLayout(1, 2, 26, 0));
        headers.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4 + 16));
        headers.add(leftHeader);
        headers.add(rightHeader);
        JPanel top = new JPanel(new BorderLayout());
        top.add(ribbon(), BorderLayout.NORTH);
        top.add(headers, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout());
        center.add(scroll, BorderLayout.CENTER);
        center.add(new DifferenceMap(view, scroll), BorderLayout.EAST);
        add(center, BorderLayout.CENTER);
        add(statusBar(), BorderLayout.SOUTH);

        view.addPropertyChangeListener(ComparisonView.CURRENT_LINE, e -> updatePosition());
        view.setComponentPopupMenu(popup());
        TransferHandler drop = new DropHandler(null);
        view.setTransferHandler(drop);
        setTransferHandler(drop);
        leftHeader.acceptDrops();
        rightHeader.acceptDrops();
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
            view.setLines(List.of(), -1);
            view.setWaitingSide(-1);
            status.setText(" ");
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

    /**
     * One document open, the other not yet: the one shown on its side as it is, nothing compared; the other side a
     * place for its document — as Beyond Compare shows a file before the other.
     */
    private void showAlone(boolean onLeft) {
        comparison = null;
        Path path = onLeft ? leftPath : rightPath;
        WordDocument known = onLeft ? leftDocument : rightDocument;
        status.setText("Reading\u2026");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        SwingWorker<WordDocument, Void> worker = new SwingWorker<>() {
            @Override
            protected WordDocument doInBackground() throws IOException {
                return known != null ? known : read(path);
            }

            @Override
            protected void done() {
                if (isCancelled() || running != this) {
                    return;
                }
                running = null;
                setCursor(null);
                try {
                    WordDocument document = get();
                    if (onLeft) {
                        leftDocument = document;
                        leftHeader.show(path, document);
                    } else {
                        rightDocument = document;
                        rightHeader.show(path, document);
                    }
                    view.setLines(Lines.alone(document, onLeft, documentView, view.fonts()), -1);
                    view.setWaitingSide(onLeft ? 1 : 0);
                    status.setText("Open the other document to compare.");
                    updateActions();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    status.setText("Not read: " + cause.getMessage());
                    JOptionPane.showMessageDialog(ComparatorPanel.this, cause.getMessage(), "Cannot read",
                            JOptionPane.ERROR_MESSAGE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        running = worker;
        worker.execute();
    }

    /** While a side has no document: what goes there, and how to open it; the side opened, its document's name. */
    private void showEmptySides() {
        view.setEmptySides(emptySide(leftPath, "Left", "Ctrl+O"), emptySide(rightPath, "Right", "Ctrl+Shift+O"));
    }

    private static ComparisonView.EmptySide emptySide(Path path, String side, String key) {
        if (path != null) {
            return new ComparisonView.EmptySide(true, String.valueOf(path.getFileName()),
                    List.of("opened \u2014 now the other document"));
        }
        return new ComparisonView.EmptySide(false, side + " document", List.of("Drop a Word document here,",
                "or choose it with Open\u2026 above (" + key + "),", "or type its path above.", "",
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
        view.setLines(Lines.of(result, onlyDifferences, onlyComparable, documentView, view.fonts()), -1);
        SwingUtilities.invokeLater(() -> scroll.getViewport().setViewPosition(new Point(0, Math.min(at.y,
                Math.max(0, view.getHeight() - scroll.getViewport().getHeight())))));
        int[] count = view.differenceCount();
        status.setText(result.same() ? "The documents are the same, as compared."
                : count[0] + (count[0] == 1 ? " difference" : " differences"));
        updatePosition();
        updateActions();
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

    /**
     * The ribbon, as Word has it: groups side by side, each its name under its buttons, set in columns — going
     * through the differences, then what is compared (the text, then the layout by group), then what is shown.
     */
    private JPanel ribbon() {
        JPanel ribbon = new JPanel();
        ribbon.setLayout(new BoxLayout(ribbon, BoxLayout.X_AXIS));
        ribbon.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Palette.GRID),
                BorderFactory.createEmptyBorder(4, 4, 2, 4)));
        ribbon.add(group("Go", 3, button(previous), button(next), button(swap)));
        ButtonGroup views = new ButtonGroup();
        JRadioButton diff = viewButton("Diff", false,
                "The text, plain and compact: the differences at a glance, as Beyond Compare shows them");
        JRadioButton document = viewButton("Document", true,
                "Each paragraph as Word lays it out: fonts, sizes, colours, alignment, indents, spacing, shading");
        views.add(diff);
        views.add(document);
        ribbon.add(group("View", 3, diff, document));
        ribbon.add(group("Text", 3, aspectBox(Aspect.TEXT), aspectBox(Aspect.BLANKS)));
        for (Map.Entry<String, List<Aspect>> layout : LAYOUT) {
            ribbon.add(group(layout.getKey(), 3,
                    layout.getValue().stream().map(this::aspectBox).toArray(JComponent[]::new)));
        }
        JCheckBox only = new JCheckBox("Only differences");
        only.setToolTipText("Hide the lines that are the same");
        only.addItemListener(e -> {
            onlyDifferences = only.isSelected();
            if (comparison != null) {
                show(comparison);
            }
        });
        JCheckBox comparable = new JCheckBox("Only what is comparable");
        comparable.setToolTipText("Hide what has nothing in common with the other side: paragraphs, tables, rows and "
                + "columns on one side only; the same paragraphs stay as they are, the close ones with their differences");
        comparable.addItemListener(e -> {
            onlyComparable = comparable.isSelected();
            if (comparison != null) {
                show(comparison);
            }
        });
        ribbon.add(group("Show", 3, only, comparable));
        ribbon.add(group("Presets", 3, preset("Text only", CompareOptions.TEXT_ONLY),
                preset("Layout only", CompareOptions.LAYOUT_ONLY), preset("All", CompareOptions.ALL)));
        ribbon.add(Box.createHorizontalGlue());
        return ribbon;
    }

    /** A button choosing a view, kept in step with the others choosing the same. */
    private JRadioButton viewButton(String name, boolean document, String tip) {
        JRadioButton button = new JRadioButton(name, document == documentView);
        button.setToolTipText(tip);
        button.putClientProperty("documentView", document);
        button.addItemListener(e -> {
            if (button.isSelected()) {
                setDocumentView(document);
            }
        });
        viewButtons.add(button);
        return button;
    }

    /** Shows the comparison, or the document alone, in this view. */
    private void setDocumentView(boolean document) {
        if (document == documentView) {
            return;
        }
        documentView = document;
        for (AbstractButton button : viewButtons) {
            button.setSelected(Boolean.valueOf(document).equals(button.getClientProperty("documentView")));
        }
        if (comparison != null) {
            show(comparison);
        } else if (leftPath != null || rightPath != null) {
            compare();
        }
    }

    /** The views, as a menu of the window. */
    public JMenu viewMenu() {
        JMenu menu = new JMenu("View");
        menu.setMnemonic(KeyEvent.VK_V);
        ButtonGroup group = new ButtonGroup();
        for (boolean document : new boolean[] {false, true}) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(document ? "Document view" : "Diff view",
                    document == documentView);
            item.putClientProperty("documentView", document);
            item.addItemListener(e -> {
                if (item.isSelected()) {
                    setDocumentView(document);
                }
            });
            viewButtons.add(item);
            group.add(item);
            menu.add(item);
        }
        return menu;
    }

    /** A group of the ribbon: its items in columns of so many, top to bottom, then left to right; its name under them. */
    private static JPanel group(String name, int rows, JComponent... items) {
        int columns = (items.length + rows - 1) / rows;
        JPanel grid = new JPanel(new GridLayout(rows, columns, 6, 0));
        JComponent[] cells = new JComponent[rows * columns];
        for (int i = 0; i < items.length; i++) {
            cells[(i % rows) * columns + i / rows] = items[i];   // down the column first, as Word sets them
        }
        for (JComponent cell : cells) {
            grid.add(cell == null ? new JLabel() : cell);
        }
        for (JComponent item : items) {
            item.setFocusable(false);
            if (item instanceof AbstractButton button) {
                button.setMargin(new Insets(1, 4, 1, 4));
            }
        }
        JLabel title = new JLabel(name, JLabel.CENTER);
        title.setFont(title.getFont().deriveFont(title.getFont().getSize2D() - 1));
        title.setForeground(Palette.FAINT);
        JPanel group = new JPanel(new BorderLayout(0, 2));
        group.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Palette.GRID),
                BorderFactory.createEmptyBorder(0, 5, 0, 5)));
        group.add(grid, BorderLayout.CENTER);
        group.add(title, BorderLayout.SOUTH);
        // as wide as it needs, as high as the highest group: every name on the same line
        group.setMaximumSize(new Dimension(group.getPreferredSize().width, Integer.MAX_VALUE));
        return group;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // The options: a check box for each kind of difference, wherever it is shown, all kept alike

    /** The kinds of layout, by group, in the ribbon and the Compare menu. */
    private static final List<Map.Entry<String, List<Aspect>>> LAYOUT = List.of(
            Map.entry("Structure", List.of(Aspect.HEADINGS, Aspect.LISTS, Aspect.SECTIONS, Aspect.TABLE_LAYOUT)),
            Map.entry("Formatting", List.of(Aspect.STYLES, Aspect.CHARACTERS, Aspect.INDENTATION, Aspect.SPACING,
                    Aspect.ALIGNMENT, Aspect.PARAGRAPHS, Aspect.CELLS)),
            Map.entry("Document", List.of(Aspect.TEMPLATE, Aspect.HEADERS_AND_FOOTERS)));

    private final Map<Aspect, List<AbstractButton>> aspectButtons = new EnumMap<>(Aspect.class);
    private boolean showingOptions;

    /** A check box for a kind of difference, kept in step with the options. */
    private JCheckBox aspectBox(Aspect aspect) {
        return register(aspect, new JCheckBox(aspect.label()));
    }

    private <B extends AbstractButton> B register(Aspect aspect, B button) {
        button.setToolTipText(aspect.description());
        button.setSelected(options.has(aspect));
        button.addItemListener(e -> {
            if (!showingOptions) {
                setOptions(options.with(aspect, button.isSelected()));
            }
        });
        aspectButtons.computeIfAbsent(aspect, a -> new ArrayList<>()).add(button);
        return button;
    }

    /** Compares with these options, every check box showing them. */
    private void setOptions(CompareOptions changed) {
        options = changed;
        showingOptions = true;
        try {
            aspectButtons.forEach((aspect, buttons) -> buttons.forEach(b -> b.setSelected(options.has(aspect))));
        } finally {
            showingOptions = false;
        }
        compare();
    }

    private JButton preset(String name, CompareOptions preset) {
        JButton button = new JButton(name);
        button.addActionListener(e -> setOptions(preset));
        return button;
    }

    /** The same options, as a menu of the window. */
    public JMenu compareMenu() {
        JMenu menu = new JMenu("Compare");
        menu.setMnemonic(KeyEvent.VK_C);
        menu.add(register(Aspect.TEXT, new JCheckBoxMenuItem(Aspect.TEXT.label())));
        menu.add(register(Aspect.BLANKS, new JCheckBoxMenuItem(Aspect.BLANKS.label())));
        for (Map.Entry<String, List<Aspect>> group : LAYOUT) {
            menu.addSeparator();
            JMenuItem title = new JMenuItem(group.getKey());
            title.setEnabled(false);
            menu.add(title);
            for (Aspect aspect : group.getValue()) {
                menu.add(register(aspect, new JCheckBoxMenuItem(aspect.label())));
            }
        }
        menu.addSeparator();
        for (Map.Entry<String, CompareOptions> preset : List.of(Map.entry("Text only", CompareOptions.TEXT_ONLY),
                Map.entry("Layout only", CompareOptions.LAYOUT_ONLY), Map.entry("All", CompareOptions.ALL),
                Map.entry("None", CompareOptions.TEXT_ONLY.with(Aspect.TEXT, false)))) {
            JMenuItem item = new JMenuItem(preset.getKey());
            item.addActionListener(e -> setOptions(preset.getValue()));
            menu.add(item);
        }
        return menu;
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
        legend.add(legend("layout", Palette.OTHERWISE_MARK));
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

    /**
     * The head of a side, as Beyond Compare has it: the path of its document, which can be typed (Enter opens it), a
     * button to choose one, what can be said of the document under them; documents dropped on it open on its side.
     */
    private final class DocumentHeader extends JPanel {

        private final boolean onLeft;
        private final JTextField path = new JTextField();
        private final JLabel about = new JLabel(" ");

        DocumentHeader(boolean onLeft) {
            super(new BorderLayout(4, 2));
            this.onLeft = onLeft;
            path.setToolTipText("The " + (onLeft ? "left" : "right") + " document: type its path and press Enter, "
                    + "choose it with Open\u2026, or drop it here");
            path.addActionListener(e -> typed());
            JButton choose = new JButton(onLeft ? openLeft : openRight);
            choose.setText("Open\u2026");
            choose.setFocusable(false);
            about.setFont(about.getFont().deriveFont(about.getFont().getSize2D() - 1));
            about.setForeground(Palette.FAINT);
            add(path, BorderLayout.CENTER);
            add(choose, BorderLayout.EAST);
            add(about, BorderLayout.SOUTH);
        }

        /** Documents dropped on the head, or its path, open on its side; text dropped on the path is typed in it. */
        void acceptDrops() {
            DropHandler drop = new DropHandler(onLeft);
            setTransferHandler(drop);
            path.setTransferHandler(new DropHandler(onLeft, path.getTransferHandler()));
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
                    status.setText("No such document: " + text);
                    Toolkit.getDefaultToolkit().beep();
                    return;
                }
                open(onLeft, typed);
            } catch (InvalidPathException e) {
                status.setText("Not a path: " + text);
                Toolkit.getDefaultToolkit().beep();
            }
        }

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
            about.setText(said.isEmpty() ? " " : String.join("  \u00B7  ", said));
        }
    }

    /** A Linux desktop's way of dropping files: their URIs, one a line (FileUris). */
    private static final DataFlavor URI_LIST = uriList();

    private static DataFlavor uriList() {
        try {
            return new DataFlavor("text/uri-list;class=java.lang.String");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Documents dropped: two at once, left and right; one, on the side it is dropped on (or the side given). As a list
     * of files (Windows, macOS) or of file URIs (Linux). Anything else goes to the handler given, if any: text dropped
     * on a path field is typed in it.
     */
    private final class DropHandler extends TransferHandler {

        private final Boolean side;
        private final TransferHandler otherwise;

        DropHandler(Boolean side) {
            this(side, null);
        }

        DropHandler(Boolean side, TransferHandler otherwise) {
            this.side = side;
            this.otherwise = otherwise;
        }

        private boolean hasFiles(TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                    || support.isDataFlavorSupported(URI_LIST);
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (hasFiles(support)) {
                if (support.isDrop()) {
                    support.setDropAction(COPY);
                }
                return true;
            }
            return otherwise != null && otherwise.canImport(support);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!hasFiles(support)) {
                return otherwise != null && otherwise.importData(support);
            }
            List<Path> files = files(support);
            if (files.isEmpty()) {
                return false;
            }
            if (files.size() >= 2) {
                open(files.get(0), files.get(1));
            } else {
                boolean onLeft = side != null ? side : leftHalf(support);
                open(onLeft, files.getFirst());
            }
            return true;
        }

        private boolean leftHalf(TransferSupport support) {
            if (!support.isDrop()) {
                return leftPath == null;   // pasted: the side not open yet, the left first
            }
            Point at = SwingUtilities.convertPoint(support.getComponent(), support.getDropLocation().getDropPoint(),
                    ComparatorPanel.this);
            return at.x < getWidth() / 2;
        }

        private List<Path> files(TransferSupport support) {
            try {
                Transferable data = support.getTransferable();
                if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) data.getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) {
                        return files.stream().map(File::toPath).toList();
                    }
                }
                if (support.isDataFlavorSupported(URI_LIST)) {
                    return FileUris.paths((String) data.getTransferData(URI_LIST));
                }
            } catch (UnsupportedFlavorException | IOException e) {
                status.setText("Not dropped: " + e.getMessage());
            }
            return List.of();
        }

        @Override
        public int getSourceActions(JComponent component) {
            return otherwise != null ? otherwise.getSourceActions(component) : NONE;
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
