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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.jtools.doccomparator.compare.CompareOptions;
import org.jtools.doccomparator.compare.DocumentComparer;
import org.jtools.doccomparator.docx.DocxReader;
import org.jtools.doccomparator.i18n.I18n;
import org.jtools.doccomparator.i18n.I18nLanguage;
import org.jtools.doccomparator.text.TextReader;
import org.junit.jupiter.api.Test;

/** The search: where it finds the words, how it goes through them, and that it outlives the lines it was made on. */
class SearchBarTest {

    /**
     * Words in both documents, whatever their capitals: every place found, line by line, the left before the right,
     * the first gone to; with Match case, only those written so; F3 round from the last to the first.
     */
    @Test
    void theWordsAreFoundOnBothSidesAndGoneThrough() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ComparatorPanel panel = shown("one\ntwo apples\nthree", "one\nTwo pears\nthree two");
            SearchBar bar = find(panel, SearchBar.class);
            field(bar).setText("two");
            assertArrayEquals(new int[] {3, 0}, bar.position(), "two, Two, two; the first gone to");
            ComparisonView view = find(panel, ComparisonView.class);
            Search.Found first = Search.in(view.lines(), "two", false).getFirst();
            assertEquals(first.line(), view.current(), "its line the current one: F7 goes on from there");
            assertEquals(1, first.block().found().size(), "shown in its text");
            bar.go(true);
            bar.go(true);
            bar.go(true);
            assertArrayEquals(new int[] {3, 0}, bar.position(), "round from the last to the first");
            bar.go(false);
            assertArrayEquals(new int[] {3, 2}, bar.position(), "and back from the first to the last");
            find(bar, JCheckBox.class).doClick();
            assertArrayEquals(new int[] {2, 1}, bar.position(),
                    "Match case: Two is not two; the place on the current line (three two) gone to, not the first");
        });
    }

    /** The comparison shown again (another language here, another option or look the same): the places still shown. */
    @Test
    void thePlacesOutliveTheLinesTheyWereFoundIn() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ComparatorPanel panel = shown("alpha beta\ngamma", "alpha\ngamma beta");
            SearchBar bar = find(panel, SearchBar.class);
            field(bar).setText("beta");
            bar.go(true);
            ComparisonView view = find(panel, ComparisonView.class);
            List<Line> before = view.lines();
            I18n.use(I18nLanguage.FRENCH);
            try {
                panel.languageChanged();
            } finally {
                I18n.use(I18nLanguage.ENGLISH);
            }
            assertNotSame(before, view.lines(), "the lines made anew");
            assertArrayEquals(new int[] {2, 1}, bar.position(), "still at the place it was at");
            long shown = view.lines().stream().filter(line -> !line.isTitle())
                    .flatMap(line -> java.util.stream.Stream.of(line.left, line.right))
                    .filter(side -> side != null && side.text != null && !side.text.found().isEmpty()).count();
            assertEquals(2, shown, "each place shown in the new lines' texts");
        });
    }

    /** The text of the documents only: a table's cells are searched, the view's own words — a part's title — are not. */
    @Test
    void cellsAreSearchedTheViewsOwnWordsAreNot() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ComparatorPanel panel = new ComparatorPanel();
            try {
                panel.show(DocumentComparer.compare(new DocxReader().read(Path.of("..", "samples", "procedure-revA.docx")),
                        new DocxReader().read(Path.of("..", "samples", "procedure-revB.docx")), CompareOptions.DEFAULT));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            ComparisonView view = find(panel, ComparisonView.class);
            List<Search.Found> found = Search.in(view.lines(), "KP-F-110", false);
            assertTrue(found.stream().anyMatch(place -> place.paragraph() == null), "in a table's cells: " + found);
            assertTrue(found.stream().anyMatch(place -> place.paragraph() != null), "and in the paragraph naming it");
            String title = view.lines().getFirst().title;
            assertTrue(title.startsWith("Body"), title);
            assertEquals(List.of(), Search.in(view.lines(), "Body —", false), "a part's title is the view's");
            assertEquals(List.of(), Search.in(view.lines(), "  ", false), "blank words find nothing");
        });
    }

    /**
     * A place in a long paragraph lies below its line's top by the lines of text above it: how far down a line taller
     * than the view the search scrolls.
     */
    @Test
    void aPlaceKnowsHowFarDownItsLineItIs() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            String paragraph = "word ".repeat(400) + "needle";
            ComparatorPanel panel = shown("start " + paragraph, "start " + paragraph);
            JScrollPane scroll = find(panel, JScrollPane.class);
            scroll.setSize(500, 300);
            scroll.doLayout();
            ComparisonView view = find(panel, ComparisonView.class);
            view.totalHeight();
            assertEquals(LineSide.PADDING, Search.in(view.lines(), "start", false).getFirst().top(),
                    "the paragraph's first line, under the side's padding");
            Search.Found needle = Search.in(view.lines(), "needle", false).getFirst();
            assertTrue(needle.top() > 300, "many lines down: " + needle.top());
            assertNull(needle.paragraph().caption, "no caption above it");
        });
    }

    /** A panel showing the comparison of these two texts, compared here, on the window's thread. */
    private static ComparatorPanel shown(String left, String right) {
        ComparatorPanel panel = new ComparatorPanel();
        panel.show(DocumentComparer.compare(TextReader.read("left", left), TextReader.read("right", right),
                CompareOptions.DEFAULT));
        return panel;
    }

    private static JTextField field(SearchBar bar) {
        return find(bar, JTextField.class);
    }

    @SuppressWarnings("unchecked")
    private static <T> T find(Container container, Class<T> type) {
        for (Component component : container.getComponents()) {
            if (type.isInstance(component)) {
                return (T) component;
            }
            if (component instanceof Container inner) {
                T found = find(inner, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
