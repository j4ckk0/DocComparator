package org.jtools.doccomparator;

import java.nio.file.Path;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 * The entry point: opens the main window, in the look of the system it runs on. Given two documents, compares them
 * at once: {@code doccomparator old.docx new.docx}.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            useSystemLook();
            MainWindow window = new MainWindow();
            window.setVisible(true);
            if (args.length == 2) {
                window.open(Path.of(args[0]), Path.of(args[1]));
            }
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
