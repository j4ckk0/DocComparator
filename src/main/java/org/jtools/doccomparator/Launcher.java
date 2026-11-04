package org.jtools.doccomparator;

import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 * The entry point: opens the main window, in the look chosen (View ▸ Look; Nimbus until one is). Given two documents,
 * compares them at once: {@code doccomparator old.docx new.docx}.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        ImageIO.setUseCache(false);   // ImageIO caches on disk, in the temporary directory, unless told not to
        Settings settings = Settings.load();
        SwingUtilities.invokeLater(() -> {
            useLook(settings.get(Settings.LOOK, MainWindow.DEFAULT_LOOK));
            MainWindow window = new MainWindow(settings);
            window.setVisible(true);
            if (args.length == 2) {
                window.open(Path.of(args[0]), Path.of(args[1]));
            }
        });
    }

    /** A look, by its class; the system's when that one cannot be had, Swing's own when neither: all work alike. */
    static void useLook(String className) {
        for (String look : new String[] {className, UIManager.getSystemLookAndFeelClassName()}) {
            try {
                UIManager.setLookAndFeel(look);
                return;
            } catch (ReflectiveOperationException | UnsupportedLookAndFeelException | RuntimeException e) {
                // the next one
            }
        }
    }
}
