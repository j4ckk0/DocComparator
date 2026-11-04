/*
 * Copyright 2026 j4ckk0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
