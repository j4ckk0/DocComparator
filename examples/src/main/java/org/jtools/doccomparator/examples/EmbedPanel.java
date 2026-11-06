package org.jtools.doccomparator.examples;

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

/*
 * Shows doccomparator-ui's comparison view in a window of one's own: two documents side by side, the ribbon of what
 * is compared, the differences marked — the left one a file, the right one text held in memory.
 *
 *   java -cp doccomparator-core.jar:doccomparator-ui.jar:doccomparator-examples.jar org.jtools.doccomparator.examples.EmbedPanel samples/procedure-revA.docx
 *
 * The view takes the look and feel in use (FlatLaf or another, or Swing's own) and the language I18n speaks.
 */

import java.awt.BorderLayout;
import java.nio.file.Path;
import java.util.List;
import javax.swing.Action;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.SwingUtilities;
import org.jtools.doccomparator.document.Document;
import org.jtools.doccomparator.i18n.I18n;
import org.jtools.doccomparator.i18n.I18nLanguage;
import org.jtools.doccomparator.text.TextReader;
import org.jtools.doccomparator.ui.ComparatorPanel;
import org.jtools.doccomparator.ui.DocumentSource;

public class EmbedPanel {

    /** A document the program reads its own way — from memory here; from a database or a service as well. */
    record Memory(String name, String text) implements DocumentSource {

        @Override
        public Document read() {
            return TextReader.read(name, text);   // a paragraph a line; any Document will do
        }

        @Override
        public String readingNote() {
            return "from memory";   // said in the side's head
        }
    }

    public static void main(String[] args) {
        I18n.use(I18nLanguage.ENGLISH);
        SwingUtilities.invokeLater(() -> {
            ComparatorPanel panel = new ComparatorPanel();

            // The panel's commands, in the window's own menus: open, paste, swap; previous and next difference.
            JMenu file = new JMenu("File");
            panel.fileActions().forEach(file::add);
            JMenu go = new JMenu("Go");
            List<Action> actions = panel.goActions();
            actions.forEach(go::add);
            JMenuBar menus = new JMenuBar();
            menus.add(file);
            menus.add(panel.compareMenu());
            menus.add(panel.viewMenu());
            menus.add(go);

            JFrame window = new JFrame("Compared");
            window.setJMenuBar(menus);
            window.add(panel, BorderLayout.CENTER);
            window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            window.setSize(1200, 800);
            window.setVisible(true);

            // Told each comparison once both sides are read and compared, away from the window's thread.
            panel.onCompared(comparison -> window.setTitle("Compared — "
                    + (comparison.same() ? "the same" : comparison.stories().stream()
                            .mapToLong(story -> story.differences()).sum() + " differences")));

            // A file on the left, text from memory on the right.
            panel.open(new DocumentSource.File(Path.of(args[0])),
                    new Memory("draft", "Maintenance Procedure — KP-40 Hydraulic Pump\nA paragraph of our own."));
        });
    }
}
