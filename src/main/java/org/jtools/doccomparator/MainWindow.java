package org.jtools.doccomparator;

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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.jtools.doccomparator.i18n.I18n;
import org.jtools.doccomparator.i18n.I18nLabels;
import org.jtools.doccomparator.i18n.I18nLanguage;
import org.jtools.doccomparator.i18n.I18nLanguageBox;
import org.jtools.doccomparator.ui.ComparatorPanel;

/** The program's only window: the comparison and its menus. Closing it, or File ▸ Exit, ends the program. */
final class MainWindow extends JFrame {

    private static final String APP_NAME = "DocComparator";
    /** Between the documents' names and the program's, in the title. */
    private static final String TITLE_SEPARATOR = " — ";
    /** The client property of a View ▸ Look item: the class of the look it chooses. */
    private static final String LOOK_CLASS = "look";
    private final ComparatorPanel panel = new ComparatorPanel();
    private final Settings settings;
    /** The menus' own labels; the panel's are its own. */
    private final I18nLabels labels = new I18nLabels();
    private final I18nLanguageBox language = new I18nLanguageBox(labels);
    private final ButtonGroup looks = new ButtonGroup();
    private final List<JRadioButtonMenuItem> lookItems = new ArrayList<>();

    MainWindow(Settings settings) {
        super(title(""));
        this.settings = settings;
        // the JVM ends with its last window: nothing else keeps it running
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        useSwingLocale();
        setJMenuBar(menuBar());
        add(panel, BorderLayout.CENTER);
        panel.onDocumentNames(names -> setTitle(title(names)));
        panel.onDarkMode(dark -> applyLook(Looks.inMode(currentLook(), dark)));
        panel.showDarkMode(Looks.isDark());
        panel.showRibbon(Boolean.parseBoolean(settings.get(Settings.RIBBON, "true")));
        panel.onRibbon(open -> settings.set(Settings.RIBBON, String.valueOf(open)));
        pack();
        setLocationRelativeTo(null);
    }

    /**
     * Speaks the language in use: the menus, the panel, the comparison; Swing's own dialogs too (the file chooser's
     * buttons), which take the default locale.
     */
    void languageChanged() {
        useSwingLocale();
        labels.apply();
        language.show(I18n.language());
        panel.languageChanged();
    }

    /** Speaks this language from now on, and the next time (Settings). */
    private void useLanguage(I18nLanguage chosen) {
        I18n.use(chosen);
        settings.set(Settings.LANGUAGE, chosen.code());
        languageChanged();
    }

    /** Swing's own dialogs (the file chooser's buttons, a message's OK) in the language in use. */
    private static void useSwingLocale() {
        Locale.setDefault(I18n.language().locale());
        JComponent.setDefaultLocale(I18n.language().locale());
    }

    /** Opens two documents and compares them, as given at the start. */
    void open(Path left, Path right) {
        panel.open(left, right);
    }

    /** Whether a comparison is shown. */
    boolean compared() {
        return panel.compared();
    }

    /**
     * View ▸ Look: FlatLaf's looks, light and dark, and those Java has here — the system's, Nimbus, Metal… —, the one
     * chosen kept for the next time (Settings), the window shown in it at once.
     */
    private JMenu lookMenu() {
        JMenu menu = labels.menu(new JMenu(), "menu.look");
        String current = currentLook();
        String system = UIManager.getSystemLookAndFeelClassName();
        List<UIManager.LookAndFeelInfo> installed = new ArrayList<>(List.of(UIManager.getInstalledLookAndFeels()));
        // FlatLaf's first, then the system's, then the others as Java lists them
        installed.sort(Comparator.comparingInt((UIManager.LookAndFeelInfo look) ->
                look.getClassName().startsWith("com.formdev.flatlaf") ? 0
                        : look.getClassName().equals(system) ? 1 : 2));
        for (UIManager.LookAndFeelInfo look : installed) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(look.getName(), look.getClassName().equals(current));
            if (look.getClassName().equals(system)) {
                labels.on(() -> item.setText(I18n.text("menu.look.system", look.getName())));
            }
            item.putClientProperty(LOOK_CLASS, look.getClassName());
            item.addActionListener(e -> applyLook(look.getClassName()));
            looks.add(item);
            lookItems.add(item);
            menu.add(item);
        }
        return menu;
    }

    private static String currentLook() {
        return UIManager.getLookAndFeel().getClass().getName();
    }

    /** Shows the window in this look, keeps it for the next time, and says so in the menus and the ribbon. */
    private void applyLook(String className) {
        Looks.use(className);
        SwingUtilities.updateComponentTreeUI(this);
        settings.set(Settings.LOOK, currentLook());
        panel.showDarkMode(Looks.isDark());
        for (JRadioButtonMenuItem item : lookItems) {
            item.setSelected(currentLook().equals(item.getClientProperty(LOOK_CLASS)));
        }
    }

    /**
     * The window's title: the documents' names, if any are open, then the program's name and its version, which the
     * jar's manifest holds (the name alone when run from the classes).
     */
    static String title(String documentNames) {
        String version = MainWindow.class.getPackage().getImplementationVersion();
        String program = version == null ? APP_NAME : APP_NAME + " " + version;
        return documentNames.isEmpty() ? program : documentNames + TITLE_SEPARATOR + program;
    }

    private JMenuBar menuBar() {
        JMenu file = labels.menu(new JMenu(), "menu.file");
        for (Action action : panel.fileActions()) {
            file.add(new JMenuItem(action));
        }
        file.addSeparator();
        JMenuItem exit = labels.menu(new JMenuItem(), "menu.file.exit");
        exit.addActionListener(e -> dispose());
        file.add(exit);
        JMenu go = labels.menu(new JMenu(), "menu.go");
        for (Action action : panel.goActions()) {
            go.add(new JMenuItem(action));
        }
        JMenuBar bar = new JMenuBar();
        bar.add(file);
        JMenu view = panel.viewMenu();   // its Dark item the panel's, kept in step with the ribbon's box
        view.add(lookMenu());
        bar.add(view);
        bar.add(panel.compareMenu());
        bar.add(go);
        bar.add(HelpMenu.of(this, labels));
        // the language on the right of the menus
        bar.add(Box.createHorizontalGlue());
        language.onChoice(this::useLanguage);
        bar.add(language);
        bar.add(Box.createHorizontalStrut(4));
        return bar;
    }
}
