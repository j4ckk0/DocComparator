package org.jtools.doccomparator;

import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.jtools.doccomparator.i18n.I18n;
import org.jtools.doccomparator.i18n.I18nLanguage;

/**
 * The entry point: opens the main window, in the look chosen (View ▸ Look; FlatLaf Light until one is) and the language
 * chosen (on the right of the menus; French until one is). Given two documents, compares them at once:
 * {@code doccomparator old.docx new.docx}.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        ImageIO.setUseCache(false);   // ImageIO caches on disk, in the temporary directory, unless told not to
        Settings settings = Settings.load();
        SwingUtilities.invokeLater(() -> {
            Looks.use(settings.get(Settings.LOOK, Looks.DEFAULT));
            I18n.use(I18nLanguage.ofCode(settings.get(Settings.LANGUAGE, I18nLanguage.DEFAULT.code())));
            MainWindow window = new MainWindow(settings);
            window.setVisible(true);
            if (args.length == 2) {
                window.open(Path.of(args[0]), Path.of(args[1]));
            }
        });
    }

}
