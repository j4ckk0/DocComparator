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

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.jtools.doccomparator.i18n.I18n;
import org.jtools.doccomparator.i18n.I18nLanguage;

/**
 * The entry point: opens the main window, in the look chosen (View ▸ Look; FlatLaf Light until one is) and the language
 * chosen (on the right of the menus; French until one is). Given two documents, compares them at once:
 * {@code doccomparator old.docx new.docx}.
 */
public final class Launcher {

    /**
     * Set to make the AOT cache (pom.xml, profile dist): the program shows its window, compares the two documents
     * given, then exits, for Java to record what it loaded.
     */
    static final String TRAINING = "doccomparator.training";

    /** How long the training run waits for its comparison before it gives up, failing. */
    private static final int TRAINING_LIMIT_MS = 120_000;

    private Launcher() {
    }

    /**
     * The program's version: its module's, linked into the distributions' runtime image, or its jar's manifest's on
     * the class path; null when it runs from its classes.
     */
    static String version() {
        Module module = Launcher.class.getModule();
        if (module.isNamed() && module.getDescriptor().rawVersion().isPresent()) {
            return module.getDescriptor().rawVersion().get();
        }
        return Launcher.class.getPackage().getImplementationVersion();
    }

    public static void main(String[] args) {
        if (Boolean.getBoolean(TRAINING) && GraphicsEnvironment.isHeadless()) {
            // nothing shown, a cache of little use: failing says so (pom.xml, profile dist)
            System.err.println("training: no display");
            System.exit(1);
        }
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
            if (Boolean.getBoolean(TRAINING)) {
                exitOnceShown(window, args.length == 2);
            }
        });
    }

    /** Exits once the window is shown and, with documents, their comparison too, given a moment to be drawn. */
    private static void exitOnceShown(MainWindow window, boolean comparing) {
        long limit = System.currentTimeMillis() + TRAINING_LIMIT_MS;
        boolean[] ready = {false};
        new Timer(100, tick -> {
            if (ready[0]) {
                window.dispose();
                System.exit(0);
            }
            ready[0] = window.isShowing() && (!comparing || window.compared());
            if (System.currentTimeMillis() > limit) {
                System.err.println("training: nothing compared after " + TRAINING_LIMIT_MS / 1000 + " s");
                System.exit(1);
            }
        }).start();
    }

}
