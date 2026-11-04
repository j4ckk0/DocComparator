package org.jtools.doccomparator;

import java.awt.BorderLayout;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.jtools.doccomparator.ui.ComparatorPanel;

/** The program's only window: the comparison and its menus. Closing it, or File ▸ Exit, ends the program. */
final class MainWindow extends JFrame {

    static final String APP_NAME = "DocComparator";
    /** The look until one is chosen: Swing's most modern, the same on every system. */
    static final String DEFAULT_LOOK = "javax.swing.plaf.nimbus.NimbusLookAndFeel";

    private final ComparatorPanel panel = new ComparatorPanel();
    private final Settings settings;

    MainWindow(Settings settings) {
        super(title());
        this.settings = settings;
        // the JVM ends with its last window: nothing else keeps it running
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setJMenuBar(menuBar());
        add(panel, BorderLayout.CENTER);
        String version = title();
        panel.onTitle(title -> setTitle(title.replace(APP_NAME, version)));
        pack();
        setLocationRelativeTo(null);
    }

    /** Opens two documents and compares them, as given at the start. */
    void open(Path left, Path right) {
        panel.open(left, right);
    }

    /**
     * View ▸ Look: the looks Java has here — the system's, Nimbus, Metal… —, the one chosen kept for the next time
     * (Settings), the window shown in it at once.
     */
    private JMenu lookMenu() {
        JMenu menu = new JMenu("Look");
        menu.setMnemonic(KeyEvent.VK_L);
        ButtonGroup group = new ButtonGroup();
        String current = UIManager.getLookAndFeel().getClass().getName();
        String system = UIManager.getSystemLookAndFeelClassName();
        List<UIManager.LookAndFeelInfo> looks = new ArrayList<>(List.of(UIManager.getInstalledLookAndFeels()));
        // the system's first, then Nimbus, then the others as Java lists them
        looks.sort(Comparator.comparingInt((UIManager.LookAndFeelInfo look) -> look.getClassName().equals(system) ? 0
                : look.getClassName().equals(DEFAULT_LOOK) ? 1 : 2));
        for (UIManager.LookAndFeelInfo look : looks) {
            String name = look.getName() + (look.getClassName().equals(system) ? " (the system's)" : "");
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(name, look.getClassName().equals(current));
            item.addActionListener(e -> {
                Launcher.useLook(look.getClassName());
                SwingUtilities.updateComponentTreeUI(this);
                settings.set(Settings.LOOK, look.getClassName());
            });
            group.add(item);
            menu.add(item);
        }
        return menu;
    }

    /** The name and the version, which the jar's manifest holds; the name alone when run from the classes. */
    static String title() {
        String version = MainWindow.class.getPackage().getImplementationVersion();
        return version == null ? APP_NAME : APP_NAME + " " + version;
    }

    private JMenuBar menuBar() {
        JMenu file = new JMenu("File");
        file.setMnemonic(KeyEvent.VK_F);
        for (Action action : panel.fileActions()) {
            file.add(new JMenuItem(action));
        }
        file.addSeparator();
        JMenuItem exit = new JMenuItem("Exit", KeyEvent.VK_X);
        exit.addActionListener(e -> dispose());
        file.add(exit);
        JMenu go = new JMenu("Go");
        go.setMnemonic(KeyEvent.VK_G);
        for (Action action : panel.goActions()) {
            go.add(new JMenuItem(action));
        }
        JMenuBar bar = new JMenuBar();
        bar.add(file);
        JMenu view = panel.viewMenu();
        view.addSeparator();
        view.add(lookMenu());
        bar.add(view);
        bar.add(panel.compareMenu());
        bar.add(go);
        return bar;
    }
}
