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

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
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
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import org.jtools.doccomparator.compare.CompareOptions;
import org.jtools.doccomparator.compare.CompareOptions.Aspect;
import org.jtools.doccomparator.i18n.I18n;

/**
 * What is compared and what is shown, chosen in the ribbon or in the Compare and View menus: the options, the lines
 * shown, the view, the dark mode. Every check box and button showing the same choice is kept in step with it; a
 * choice made is told to the comparison. The ribbon collapses to a strip, and opens again (View ▸ Ribbon, Ctrl+F1).
 */
final class OptionControls {

    /** The kinds of layout, by group (the key of its name), in the ribbon and the Compare menu. */
    private static final List<Map.Entry<String, List<Aspect>>> LAYOUT = List.of(
            Map.entry("ribbon.group.structure",
                    List.of(Aspect.HEADINGS, Aspect.LISTS, Aspect.SECTIONS, Aspect.TABLE_LAYOUT)),
            Map.entry("ribbon.group.formatting", List.of(Aspect.STYLES, Aspect.CHARACTERS, Aspect.INDENTATION,
                    Aspect.SPACING, Aspect.ALIGNMENT, Aspect.PARAGRAPHS, Aspect.CELLS)),
            Map.entry("ribbon.group.document", List.of(Aspect.TEMPLATE, Aspect.HEADERS_AND_FOOTERS)));
    /** The client property telling which view a button chooses. */
    private static final String DOCUMENT_VIEW = "documentView";

    private final I18nLabels labels;
    private final Runnable optionsChanged;
    private final Consumer<Boolean> shownChanged;
    private CompareOptions options = CompareOptions.DEFAULT;
    private Lines.Shown shown = Lines.Shown.ALL;
    private final Map<Aspect, List<AbstractButton>> aspectButtons = new EnumMap<>(Aspect.class);
    private final List<AbstractButton> viewButtons = new ArrayList<>();
    /** While the buttons are set to the options: their changes are not choices. */
    private boolean showingOptions;
    /** Whether the look is dark, as the ribbon's Dark box and the View menu's Dark item show it. */
    private boolean darkMode;
    private final List<AbstractButton> darkButtons = new ArrayList<>();
    private Consumer<Boolean> darkModeListener = _ -> { };
    /** While the Dark buttons are set to the look: their changes are not choices. */
    private boolean showingDarkMode;
    /** Whether the ribbon is open, its groups shown; collapsed, its strip says where its options are. */
    private boolean ribbonOpen = true;
    private Consumer<Boolean> ribbonListener = _ -> { };
    private final JPanel ribbonGroups = new JPanel();
    private final JLabel ribbonCollapsed = new FaintLabel(" ", JLabel.LEADING, true);
    private final JButton ribbonToggle = new JButton();
    /** View ▸ Ribbon, ticked while the ribbon is open; Ctrl+F1 as in Word. */
    private final Action ribbonAction;

    /**
     * Controls telling optionsChanged when the options change, and shownChanged when the lines shown do (true when it
     * is the view); their labels bound with these.
     */
    OptionControls(I18nLabels labels, Runnable optionsChanged, Consumer<Boolean> shownChanged) {
        this.labels = labels;
        this.optionsChanged = optionsChanged;
        this.shownChanged = shownChanged;
        ribbonAction = labels.action(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {   // the menu item ticked or not; Ctrl+F1, the other way
                openRibbon(e.getSource() instanceof AbstractButton item ? item.isSelected() : !ribbonOpen);
            }
        }, "menu.view.ribbon", null);
        ribbonAction.putValue(Action.SELECTED_KEY, true);
        ribbonAction.putValue(Action.ACCELERATOR_KEY,
                KeyStroke.getKeyStroke(KeyEvent.VK_F1, InputEvent.CTRL_DOWN_MASK));
    }

    CompareOptions options() {
        return options;
    }

    Lines.Shown shown() {
        return shown;
    }

    /** Told when Dark is ticked or not, in the ribbon or the View menu: the window changes its look. */
    void onDarkMode(Consumer<Boolean> listener) {
        darkModeListener = listener;
    }

    /** Shows whether the look is dark, in the ribbon's Dark box and the View menu's Dark item, telling no one. */
    void showDarkMode(boolean dark) {
        darkMode = dark;
        showingDarkMode = true;
        try {
            for (AbstractButton button : darkButtons) {
                button.setSelected(dark);
            }
        } finally {
            showingDarkMode = false;
        }
    }

    /** A button choosing the dark look, kept in step with the others choosing it. */
    private <B extends AbstractButton> B registerDark(B button) {
        darkButtons.add(button);
        button.setSelected(darkMode);
        button.addItemListener(_ -> {
            if (!showingDarkMode) {
                showDarkMode(button.isSelected());
                darkModeListener.accept(darkMode);
            }
        });
        return button;
    }

    /** Told when the ribbon is opened (true) or collapsed. */
    void onRibbon(Consumer<Boolean> listener) {
        ribbonListener = listener;
    }

    /** The action opening and collapsing the ribbon, for its shortcut. */
    Action ribbonAction() {
        return ribbonAction;
    }

    /** Opens the ribbon, or collapses it to its strip, telling no one. */
    void showRibbon(boolean open) {
        ribbonOpen = open;
        ribbonGroups.setVisible(open);
        ribbonCollapsed.setVisible(!open);
        ribbonToggle.setIcon(new Chevron(open));
        ribbonToggle.setToolTipText(I18n.text(open ? "ribbon.collapse.tip" : "ribbon.open.tip"));
        ribbonAction.putValue(Action.SELECTED_KEY, open);
        ribbonToggle.revalidate();
    }

    private void openRibbon(boolean open) {
        showRibbon(open);
        ribbonListener.accept(open);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // The ribbon

    /**
     * The ribbon, as Word has it: groups side by side, each its name under its buttons, set in columns — going
     * through the differences (these actions), then the view, what is compared (the text, then the layout by group),
     * what is shown, then the presets; in its top right corner, the button collapsing it to a strip, and opening it
     * again.
     */
    JPanel ribbon(Action... go) {
        JPanel ribbon = ribbonGroups;
        ribbon.setLayout(new BoxLayout(ribbon, BoxLayout.X_AXIS));
        ribbon.setBorder(BorderFactory.createEmptyBorder(4, 4, 2, 0));
        List<JComponent> goButtons = new ArrayList<>();
        for (Action action : go) {
            goButtons.add(new JButton(action));
        }
        ribbon.add(group("ribbon.group.go", 3, goButtons.toArray(JComponent[]::new)));
        ButtonGroup views = new ButtonGroup();
        JRadioButton diff = viewButton("ribbon.view.diff", "ribbon.view.diff.tip", false);
        JRadioButton document = viewButton("ribbon.view.document", "ribbon.view.document.tip", true);
        views.add(diff);
        views.add(document);
        JCheckBox darkBox = registerDark(labels.tip(labels.text(new JCheckBox(), "ribbon.view.dark"),
                "ribbon.view.dark.tip"));
        ribbon.add(group("ribbon.group.view", 3, diff, document, darkBox));
        ribbon.add(group("ribbon.group.text", 3, aspectBox(Aspect.TEXT), aspectBox(Aspect.BLANKS)));
        for (Map.Entry<String, List<Aspect>> layout : LAYOUT) {
            ribbon.add(group(layout.getKey(), 3,
                    layout.getValue().stream().map(this::aspectBox).toArray(JComponent[]::new)));
        }
        JCheckBox only = labels.tip(labels.text(new JCheckBox(), "ribbon.show.differences"),
                "ribbon.show.differences.tip");
        only.addItemListener(_ -> {
            shown = shown.withOnlyDifferences(only.isSelected());
            shownChanged.accept(false);
        });
        JCheckBox comparable = labels.tip(labels.text(new JCheckBox(), "ribbon.show.comparable"),
                "ribbon.show.comparable.tip");
        comparable.addItemListener(_ -> {
            shown = shown.withOnlyComparable(comparable.isSelected());
            shownChanged.accept(false);
        });
        ribbon.add(group("ribbon.group.show", 3, only, comparable));
        ribbon.add(group("ribbon.group.presets", 3, preset("preset.text", CompareOptions.TEXT_ONLY),
                preset("preset.layout", CompareOptions.LAYOUT_ONLY), preset("preset.all", CompareOptions.ALL)));
        ribbon.add(Box.createHorizontalGlue());

        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createCompoundBorder(new RibbonEdge(),
                BorderFactory.createEmptyBorder(0, 0, 1, 0)));
        bar.add(ribbon, BorderLayout.CENTER);
        labels.text(ribbonCollapsed, "ribbon.collapsed");
        ribbonCollapsed.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        bar.add(ribbonCollapsed, BorderLayout.WEST);
        ribbonToggle.setFocusable(false);
        ribbonToggle.setMargin(new Insets(1, 3, 1, 3));
        ribbonToggle.addActionListener(_ -> openRibbon(!ribbonOpen));
        JPanel corner = new JPanel(new BorderLayout());
        corner.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 2));
        corner.add(ribbonToggle, BorderLayout.NORTH);
        bar.add(corner, BorderLayout.EAST);
        labels.on(() -> showRibbon(ribbonOpen));   // the toggle's tooltip, in the language of the time
        return bar;
    }

    /** A chevron pointing up (the ribbon collapses) or down (it opens), in the look's text colour of the time. */
    private record Chevron(boolean up) implements Icon {

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c.isEnabled() ? c.getForeground() : Color.GRAY);
                g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int top = up ? y + 7 : y + 4;
                int bottom = up ? y + 4 : y + 7;
                g2.drawPolyline(new int[] {x + 2, x + 6, x + 10}, new int[] {top, bottom, top}, 3);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return 12;
        }

        @Override
        public int getIconHeight() {
            return 11;
        }
    }

    /**
     * A group of the ribbon: its items in columns of so many, top to bottom, then left to right; its name under them.
     */
    private JPanel group(String name, int rows, JComponent... items) {
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
        JLabel title = labels.text(new FaintLabel(" ", JLabel.CENTER, true), name);

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
    private JRadioButton viewButton(String name, String tip, boolean document) {
        JRadioButton button = new JRadioButton((String) null, document == shown.documentView());
        labels.tip(labels.text(button, name), tip);
        return registerView(button, document);
    }

    private <B extends AbstractButton> B registerView(B button, boolean document) {
        button.putClientProperty(DOCUMENT_VIEW, document);
        button.addItemListener(_ -> {
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

    /** The views, the ribbon and the dark look, as a menu of the window. */
    JMenu viewMenu() {
        JMenu menu = labels.menu(new JMenu(), "menu.view");
        ButtonGroup group = new ButtonGroup();
        for (boolean document : new boolean[] {false, true}) {
            JRadioButtonMenuItem item = registerView(labels.text(new JRadioButtonMenuItem((String) null,
                    document == shown.documentView()), document ? "menu.view.document" : "menu.view.diff"), document);
            group.add(item);
            menu.add(item);
        }
        menu.addSeparator();
        menu.add(new JCheckBoxMenuItem(ribbonAction));
        menu.addSeparator();
        menu.add(registerDark(labels.menu(new JCheckBoxMenuItem(), "menu.view.dark")));
        return menu;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // The options: a check box for each kind of difference, wherever it is shown, all kept alike

    /** A check box for a kind of difference, kept in step with the options. */
    private JCheckBox aspectBox(Aspect aspect) {
        return register(aspect, new JCheckBox());
    }

    private <B extends AbstractButton> B register(Aspect aspect, B button) {
        labels.text(button, aspect.labelKey());
        labels.tip(button, aspect.descriptionKey());
        button.setSelected(options.has(aspect));
        button.addItemListener(_ -> {
            if (!showingOptions) {
                setOptions(options.with(aspect, button.isSelected()));
            }
        });
        aspectButtons.computeIfAbsent(aspect, _ -> new ArrayList<>()).add(button);
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
        JButton button = labels.text(new JButton(), name);
        button.addActionListener(_ -> setOptions(preset));
        return button;
    }

    /** The same options, as a menu of the window. */
    JMenu compareMenu() {
        JMenu menu = labels.menu(new JMenu(), "menu.compare");
        menu.add(register(Aspect.TEXT, new JCheckBoxMenuItem()));
        menu.add(register(Aspect.BLANKS, new JCheckBoxMenuItem()));
        for (Map.Entry<String, List<Aspect>> group : LAYOUT) {
            menu.addSeparator();
            JMenuItem title = labels.text(new JMenuItem(), group.getKey());
            title.setEnabled(false);
            menu.add(title);
            for (Aspect aspect : group.getValue()) {
                menu.add(register(aspect, new JCheckBoxMenuItem()));
            }
        }
        menu.addSeparator();
        for (Map.Entry<String, CompareOptions> preset : List.of(Map.entry("preset.text", CompareOptions.TEXT_ONLY),
                Map.entry("preset.layout", CompareOptions.LAYOUT_ONLY), Map.entry("preset.all", CompareOptions.ALL),
                Map.entry("preset.none", CompareOptions.TEXT_ONLY.with(Aspect.TEXT, false)))) {
            JMenuItem item = labels.text(new JMenuItem(), preset.getKey());
            item.addActionListener(_ -> setOptions(preset.getValue()));
            menu.add(item);
        }
        return menu;
    }
}
