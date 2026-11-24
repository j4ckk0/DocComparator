package org.jtools.doccomparator.ui;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.SwingUtilities;
import javax.swing.event.MenuListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Each side's recent documents: what they keep, and when the panel records one and tells it. */
class RecentFilesTest {

    @TempDir
    Path folder;

    @Test
    void theLatestIsFirstEachOnceTenKept() {
        RecentFiles recent = new RecentFiles();
        for (int i = 1; i <= 12; i++) {
            assertTrue(recent.opened(Side.LEFT, folder.resolve(i + ".docx")));
        }
        assertEquals(RecentFiles.KEPT, recent.of(Side.LEFT).size());
        assertEquals(folder.resolve("12.docx"), recent.of(Side.LEFT).getFirst());
        assertEquals(folder.resolve("3.docx"), recent.of(Side.LEFT).getLast(), "the two oldest dropped");
        assertTrue(recent.opened(Side.LEFT, folder.resolve("sub/../5.docx")), "one file, written another way");
        assertEquals(folder.resolve("5.docx"), recent.of(Side.LEFT).getFirst());
        assertEquals(RecentFiles.KEPT, recent.of(Side.LEFT).size(), "moved up, not listed twice");
        assertFalse(recent.opened(Side.LEFT, folder.resolve("5.docx")), "first already: nothing changes");
        assertEquals(List.of(), recent.of(Side.RIGHT), "the other side's, apart");
    }

    @Test
    void aFileGoneIsTakenOutAndAListGivenIsKeptAsGiven() {
        RecentFiles recent = new RecentFiles();
        recent.set(Side.RIGHT, List.of(folder.resolve("a.docx"), folder.resolve("b.docx"), folder.resolve("a.docx")));
        assertEquals(List.of(folder.resolve("a.docx"), folder.resolve("b.docx")), recent.of(Side.RIGHT));
        assertTrue(recent.gone(Side.RIGHT, folder.resolve("a.docx")));
        assertFalse(recent.gone(Side.RIGHT, folder.resolve("a.docx")), "not in it any more");
        assertEquals(List.of(folder.resolve("b.docx")), recent.of(Side.RIGHT));
    }

    /**
     * Two files opened: each, once read, first on its side, the program told once a side; compared again (another
     * option, the sides swapped), nothing told; a list given by the program, not told back.
     */
    @Test
    void aFileReadGoesInItsSidesListAndIsToldOnce() throws Exception {
        Path left = Files.writeString(folder.resolve("old.txt"), "one\ntwo\n");
        Path right = Files.writeString(folder.resolve("new.txt"), "one\n2\n");
        List<String> told = new ArrayList<>();
        AtomicInteger comparisons = new AtomicInteger();
        ComparatorPanel[] panel = new ComparatorPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new ComparatorPanel();
            panel[0].onCompared(_ -> comparisons.incrementAndGet());
            panel[0].onRecentFiles((side, files) -> told.add(side + " " + files));
            panel[0].showRecentFiles(Side.LEFT, List.of(folder.resolve("earlier.docx")));
            panel[0].open(left, right);
        });
        compared(comparisons, 1);
        SwingUtilities.invokeAndWait(() -> {
            assertEquals(List.of(left, folder.resolve("earlier.docx")), panel[0].recentFiles(Side.LEFT));
            assertEquals(List.of(right), panel[0].recentFiles(Side.RIGHT));
            assertEquals(List.of("LEFT " + List.of(left, folder.resolve("earlier.docx")), "RIGHT " + List.of(right)),
                    told);
            assertTrue(recentButtons(panel[0]).stream().allMatch(JButton::isEnabled), "each head offers its list");
            panel[0].fileActions().getLast().actionPerformed(null);   // swap the sides
        });
        compared(comparisons, 2);
        SwingUtilities.invokeAndWait(() -> {
            assertEquals(2, told.size(), "documents read again are not opened again: " + told);
            assertEquals(List.of(right), panel[0].recentFiles(Side.RIGHT));
        });
    }

    /** A recent document no longer there: said so, out of the list, the program told; one there, opened again. */
    @Test
    void aRecentDocumentGoneIsTakenOutAndOneThereOpens() throws Exception {
        Path here = Files.writeString(folder.resolve("here.txt"), "one\n");
        Path gone = folder.resolve("gone.txt");
        List<List<Path>> told = new ArrayList<>();
        ComparatorPanel[] panel = new ComparatorPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new ComparatorPanel();
            panel[0].showRecentFiles(Side.RIGHT, List.of(gone, here));
            panel[0].onRecentFiles((_, files) -> told.add(files));
            JMenu menu = panel[0].recentMenu(Side.RIGHT);
            for (MenuListener listener : menu.getMenuListeners()) {
                listener.menuSelected(null);   // as it opens: its items made
            }
            assertEquals(2, menu.getItemCount());
            assertEquals("gone.txt — " + folder, menu.getItem(0).getText());
            menu.getItem(0).doClick();
            assertEquals(List.of(List.of(here)), told);
            menu.getMenuListeners()[0].menuSelected(null);
            menu.getItem(0).doClick();   // here.txt
        });
        for (int wait = 0; wait < 500; wait++) {
            String[] names = new String[1];
            SwingUtilities.invokeAndWait(() -> names[0] = panel[0].documentNames());
            if (names[0].equals("… ↔ here.txt")) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("here.txt not opened on the right");
    }

    /** Waits for the panel's comparisons, read and compared away from the window's thread, to be so many. */
    private static void compared(AtomicInteger comparisons, int count) throws Exception {
        for (int wait = 0; wait < 500 && comparisons.get() < count; wait++) {
            Thread.sleep(10);
        }
        assertEquals(count, comparisons.get(), "compared");
        SwingUtilities.invokeAndWait(() -> { });
    }

    /** The heads' ▾ buttons. */
    private static List<JButton> recentButtons(Container container) {
        List<JButton> found = new ArrayList<>();
        for (Component component : container.getComponents()) {
            if (component instanceof JButton button && "▾".equals(button.getText())) {
                found.add(button);
            }
            if (component instanceof Container inner) {
                found.addAll(recentButtons(inner));
            }
        }
        return found;
    }
}
