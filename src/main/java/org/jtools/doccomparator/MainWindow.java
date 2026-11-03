package org.jtools.doccomparator;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingConstants;

/** The program's only window. Closing it, or File ▸ Exit, ends the program. */
final class MainWindow extends JFrame {

    static final String APP_NAME = "DocComparator";

    MainWindow() {
        super(title());
        // the JVM ends with its last window: nothing else keeps it running
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setJMenuBar(menuBar());
        add(new JLabel("(to come)", SwingConstants.CENTER), BorderLayout.CENTER);
        setPreferredSize(new Dimension(900, 600));
        pack();
        setLocationRelativeTo(null);
    }

    /** The name and the version, which the jar's manifest holds; the name alone when run from the classes. */
    static String title() {
        String version = MainWindow.class.getPackage().getImplementationVersion();
        return version == null ? APP_NAME : APP_NAME + " " + version;
    }

    private JMenuBar menuBar() {
        JMenuItem exit = new JMenuItem("Exit", KeyEvent.VK_X);
        exit.addActionListener(e -> dispose());
        JMenu file = new JMenu("File");
        file.setMnemonic(KeyEvent.VK_F);
        file.add(exit);
        JMenuBar bar = new JMenuBar();
        bar.add(file);
        return bar;
    }
}
