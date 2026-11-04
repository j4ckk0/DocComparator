package org.jtools.doccomparator;

import java.awt.BorderLayout;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import javax.swing.Action;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import org.jtools.doccomparator.ui.ComparatorPanel;

/** The program's only window: the comparison and its menus. Closing it, or File ▸ Exit, ends the program. */
final class MainWindow extends JFrame {

    static final String APP_NAME = "DocComparator";

    private final ComparatorPanel panel = new ComparatorPanel();

    MainWindow() {
        super(title());
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
        bar.add(panel.viewMenu());
        bar.add(panel.compareMenu());
        bar.add(go);
        return bar;
    }
}
