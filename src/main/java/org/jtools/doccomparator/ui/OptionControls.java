package org.jtools.doccomparator.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JRadioButtonMenuItem;
import org.jtools.doccomparator.compare.CompareOptions;
import org.jtools.doccomparator.compare.CompareOptions.Aspect;

/**
 * What is compared and what is shown, chosen in the ribbon or in the Compare and View menus: the options, the lines
 * shown, the view, the dark mode. Every check box and button showing the same choice is kept in step with it; a
 * choice made is told to the comparison.
 */
final class OptionControls {

    /** The kinds of layout, by group, in the ribbon and the Compare menu. */
    private static final List<Map.Entry<String, List<Aspect>>> LAYOUT = List.of(
            Map.entry("Structure", List.of(Aspect.HEADINGS, Aspect.LISTS, Aspect.SECTIONS, Aspect.TABLE_LAYOUT)),
            Map.entry("Formatting", List.of(Aspect.STYLES, Aspect.CHARACTERS, Aspect.INDENTATION, Aspect.SPACING,
                    Aspect.ALIGNMENT, Aspect.PARAGRAPHS, Aspect.CELLS)),
            Map.entry("Document", List.of(Aspect.TEMPLATE, Aspect.HEADERS_AND_FOOTERS)));
    /** The client property telling which view a button chooses. */
    private static final String DOCUMENT_VIEW = "documentView";

    private final Runnable optionsChanged;
    private final Consumer<Boolean> shownChanged;
    private CompareOptions options = CompareOptions.DEFAULT;
    private Lines.Shown shown = Lines.Shown.ALL;
    private final Map<Aspect, List<AbstractButton>> aspectButtons = new EnumMap<>(Aspect.class);
    private final List<AbstractButton> viewButtons = new ArrayList<>();
    /** While the buttons are set to the options: their changes are not choices. */
    private boolean showingOptions;
    private final JCheckBox darkBox = new JCheckBox("Dark");
    private Consumer<Boolean> darkModeListener = dark -> { };
    private boolean showingDarkMode;

    /**
     * Controls telling optionsChanged when the options change, and shownChanged when the lines shown do (true when it
     * is the view).
     */
    OptionControls(Runnable optionsChanged, Consumer<Boolean> shownChanged) {
        this.optionsChanged = optionsChanged;
        this.shownChanged = shownChanged;
    }

    CompareOptions options() {
        return options;
    }

    Lines.Shown shown() {
        return shown;
    }

    /** Told when the Dark box of the ribbon is ticked or not: the window changes its look. */
    void onDarkMode(Consumer<Boolean> listener) {
        darkModeListener = listener;
    }

    /** Shows whether the look is dark in the ribbon's Dark box, telling no one. */
    void showDarkMode(boolean dark) {
        showingDarkMode = true;
        try {
            darkBox.setSelected(dark);
        } finally {
            showingDarkMode = false;
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // The ribbon

    /**
     * The ribbon, as Word has it: groups side by side, each its name under its buttons, set in columns — going
     * through the differences (these actions), then the view, what is compared (the text, then the layout by group),
     * then what is shown.
     */
    JPanel ribbon(Action... go) {
        JPanel ribbon = new JPanel();
        ribbon.setLayout(new BoxLayout(ribbon, BoxLayout.X_AXIS));
        ribbon.setBorder(BorderFactory.createCompoundBorder(new LookLine(0, 0, 1, 0),
                BorderFactory.createEmptyBorder(4, 4, 2, 4)));
        List<JComponent> goButtons = new ArrayList<>();
        for (Action action : go) {
            goButtons.add(new JButton(action));
        }
        ribbon.add(group("Go", 3, goButtons.toArray(JComponent[]::new)));
        ButtonGroup views = new ButtonGroup();
        JRadioButton diff = viewButton("Diff", false,
                "The text, plain and compact: the differences at a glance, as Beyond Compare shows them");
        JRadioButton document = viewButton("Document", true,
                "Each paragraph as Word lays it out: fonts, sizes, colours, alignment, indents, spacing, shading");
        views.add(diff);
        views.add(document);
        darkBox.setToolTipText("The window dark, or light: FlatLaf's dark look, or its light one (View ▸ Look)");
        darkBox.addItemListener(e -> {
            if (!showingDarkMode) {
                darkModeListener.accept(darkBox.isSelected());
            }
        });
        ribbon.add(group("View", 3, diff, document, darkBox));
        ribbon.add(group("Text", 3, aspectBox(Aspect.TEXT), aspectBox(Aspect.BLANKS)));
        for (Map.Entry<String, List<Aspect>> layout : LAYOUT) {
            ribbon.add(group(layout.getKey(), 3,
                    layout.getValue().stream().map(this::aspectBox).toArray(JComponent[]::new)));
        }
        JCheckBox only = new JCheckBox("Only differences");
        only.setToolTipText("Hide the lines that are the same");
        only.addItemListener(e -> {
            shown = shown.withOnlyDifferences(only.isSelected());
            shownChanged.accept(false);
        });
        JCheckBox comparable = new JCheckBox("Only what is comparable");
        comparable.setToolTipText("Hide what has nothing in common with the other side: paragraphs, tables, rows and "
                + "columns on one side only; the same paragraphs stay as they are, the close ones with their differences");
        comparable.addItemListener(e -> {
            shown = shown.withOnlyComparable(comparable.isSelected());
            shownChanged.accept(false);
        });
        ribbon.add(group("Show", 3, only, comparable));
        ribbon.add(group("Presets", 3, preset("Text only", CompareOptions.TEXT_ONLY),
                preset("Layout only", CompareOptions.LAYOUT_ONLY), preset("All", CompareOptions.ALL)));
        ribbon.add(Box.createHorizontalGlue());
        return ribbon;
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
        JLabel title = new FaintLabel(name, JLabel.CENTER, true);

        JPanel group = new JPanel(new BorderLayout(0, 2)) {
            @Override
            public Dimension getMaximumSize() {   // as wide as it needs in the look of the time, as high as the highest
                return new Dimension(getPreferredSize().width, Integer.MAX_VALUE);
            }
        };
        group.setBorder(BorderFactory.createCompoundBorder(new LookLine(0, 0, 0, 1),
                BorderFactory.createEmptyBorder(0, 5, 0, 5)));
        group.add(grid, BorderLayout.CENTER);
        group.add(title, BorderLayout.SOUTH);
        return group;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // The view

    /** A button choosing a view, kept in step with the others choosing the same. */
    private JRadioButton viewButton(String name, boolean document, String tip) {
        JRadioButton button = new JRadioButton(name, document == shown.documentView());
        button.setToolTipText(tip);
        return registerView(button, document);
    }

    private <B extends AbstractButton> B registerView(B button, boolean document) {
        button.putClientProperty(DOCUMENT_VIEW, document);
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
        if (document == shown.documentView()) {
            return;
        }
        shown = shown.withDocumentView(document);
        for (AbstractButton button : viewButtons) {
            button.setSelected(Boolean.valueOf(document).equals(button.getClientProperty(DOCUMENT_VIEW)));
        }
        shownChanged.accept(true);
    }

    /** The views, as a menu of the window. */
    JMenu viewMenu() {
        JMenu menu = new JMenu("View");
        menu.setMnemonic(KeyEvent.VK_V);
        ButtonGroup group = new ButtonGroup();
        for (boolean document : new boolean[] {false, true}) {
            JRadioButtonMenuItem item = registerView(new JRadioButtonMenuItem(document ? "Document view" : "Diff view",
                    document == shown.documentView()), document);
            group.add(item);
            menu.add(item);
        }
        return menu;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // The options: a check box for each kind of difference, wherever it is shown, all kept alike

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
        optionsChanged.run();
    }

    private JButton preset(String name, CompareOptions preset) {
        JButton button = new JButton(name);
        button.addActionListener(e -> setOptions(preset));
        return button;
    }

    /** The same options, as a menu of the window. */
    JMenu compareMenu() {
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
}
