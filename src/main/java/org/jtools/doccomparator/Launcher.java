package org.jtools.doccomparator;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/** The entry point: opens the main window, in the look of the system it runs on. */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            useSystemLook();
            new MainWindow().setVisible(true);
        });
    }

    /** Swing's own look when the system's cannot be had: the program works the same in either. */
    private static void useSystemLook() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
            // keeps the default look
        }
    }
}
