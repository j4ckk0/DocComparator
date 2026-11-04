package org.jtools.doccomparator;

import java.awt.BorderLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.jtools.doccomparator.i18n.I18n;
import org.jtools.doccomparator.i18n.I18nLabels;
import org.jtools.doccomparator.ui.ComparatorPanel;

/** The program's only window: the comparison and its menus. Closing it, or File ▸ Exit, ends the program. */
final class MainWindow extends JFrame {

    static final String APP_NAME = "DocComparator";
    private final ComparatorPanel panel = new ComparatorPanel();
    private final Settings settings;
    /** The menus' own labels; the panel's are its own. */
    private final I18nLabels labels = new I18nLabels();
    private final JCheckBoxMenuItem darkMode = new JCheckBoxMenuItem();
    private final ButtonGroup looks = new ButtonGroup();
    private final List<JRadioButtonMenuItem> lookItems = new ArrayList<>();

    MainWindow(Settings settings) {
        super(title());
        this.settings = settings;
        // the JVM ends with its last window: nothing else keeps it running
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setJMenuBar(menuBar());
        add(panel, BorderLayout.CENTER);
        String version = title();
        panel.onTitle(title -> setTitle(title.replace(APP_NAME, version)));
        panel.onDarkMode(dark -> applyLook(Looks.inMode(currentLook(), dark)));
        panel.showDarkMode(Looks.isDark());
        pack();
        setLocationRelativeTo(null);
    }

    /**
     * Speaks the language in use: the menus, the panel, the comparison; Swing's own dialogs too (the file chooser's
     * buttons), which take the default locale.
     */
    void languageChanged() {
        Locale.setDefault(I18n.language().locale());
        JComponent.setDefaultLocale(I18n.language().locale());
        labels.apply();
        panel.languageChanged();
    }

    /** Opens two documents and compares them, as given at the start. */
    void open(Path left, Path right) {
        panel.open(left, right);
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
                look.getClassName().startsWith("com.formdev.flatlaf") ? 0 : look.getClassName().equals(system) ? 1 : 2));
        for (UIManager.LookAndFeelInfo look : installed) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(look.getName(), look.getClassName().equals(current));
            if (look.getClassName().equals(system)) {
                labels.on(() -> item.setText(I18n.text("menu.look.system", look.getName())));
            }
            item.putClientProperty("look", look.getClassName());
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

    /** Shows the window in this look, keeps it for the next time, and says so in the menu and the ribbon. */
    private void applyLook(String className) {
        Looks.use(className);
        SwingUtilities.updateComponentTreeUI(this);
        settings.set(Settings.LOOK, currentLook());
        boolean dark = Looks.isDark();
        darkMode.setSelected(dark);
        panel.showDarkMode(dark);
        for (JRadioButtonMenuItem item : lookItems) {
            item.setSelected(currentLook().equals(item.getClientProperty("look")));
        }
    }

    /** The name and the version, which the jar's manifest holds; the name alone when run from the classes. */
    static String title() {
        String version = MainWindow.class.getPackage().getImplementationVersion();
        return version == null ? APP_NAME : APP_NAME + " " + version;
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
        JMenu view = panel.viewMenu();
        view.addSeparator();
        labels.menu(darkMode, "menu.view.dark");
        darkMode.setSelected(Looks.isDark());
        darkMode.addActionListener(e -> applyLook(Looks.inMode(currentLook(), darkMode.isSelected())));
        view.add(darkMode);
        view.add(lookMenu());
        bar.add(view);
        bar.add(panel.compareMenu());
        bar.add(go);
        return bar;
    }
}
