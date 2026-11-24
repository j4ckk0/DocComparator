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

import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.jtools.doccomparator.i18n.I18n;

/**
 * The search, under the two sides: words typed are found in both documents as they are typed, every place shown, the
 * first after the current line gone to; Enter (or ▼, F3) goes to the next, Shift+Enter (▲, Shift+F3) to the previous,
 * round from the last to the first; Escape gives the view its keys back. Kept out of the ribbon, which can be
 * collapsed. The places stay shown as the comparison is shown again — another option, another view, another look.
 */
final class SearchBar extends JPanel {

    private final ComparisonView view;
    private final JTextField words = new JTextField(22);
    private final JCheckBox matchCase = new JCheckBox();
    private final JLabel count = new JLabel(" ");
    private final I18nLabels labels;
    private List<Search.Found> found = List.of();
    /** The place gone to, its index in found; -1 for none. */
    private int current = -1;

    /** The search of what this view shows, its labels bound with these. */
    SearchBar(ComparisonView view, I18nLabels labels) {
        super(new FlowLayout(FlowLayout.LEFT, 6, 0));
        this.view = view;
        this.labels = labels;
        setBorder(BorderFactory.createEmptyBorder(3, 2, 0, 6));
        JLabel title = labels.text(new JLabel(), "search.label");
        title.setLabelFor(words);
        labels.tip(words, "search.words.tip");
        add(title);
        add(words);
        add(button("search.previous", "search.previous.tip", () -> go(false)));
        add(button("search.next", "search.next.tip", () -> go(true)));
        labels.text(matchCase, "search.matchCase");
        labels.tip(matchCase, "search.matchCase.tip");
        matchCase.setFocusable(false);
        matchCase.addActionListener(_ -> search(true));
        add(matchCase);
        add(count);
        labels.on(this::sayCount);

        words.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                search(true);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                search(true);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                search(true);
            }
        });
        words.addActionListener(_ -> go(true));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "previousPlace", () -> go(false));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "leaveSearch", view::requestFocusInWindow);
        // the comparison shown again, its lines made anew: the places found in them, the view left where it is
        view.addPropertyChangeListener(ComparisonView.LINES, _ -> search(false));
    }

    /** Puts the keys in the words, all of them chosen, to type others in their place. */
    void focusWords() {
        words.requestFocusInWindow();
        words.selectAll();
    }

    /** Goes to the next place found, or the previous one, round from the last to the first; a beep when none is. */
    void go(boolean next) {
        if (found.isEmpty()) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        int size = found.size();
        current = current < 0 ? (next ? 0 : size - 1) : Math.floorMod(current + (next ? 1 : -1), size);
        showCurrent();
    }

    /** The words searched, as typed. */
    String words() {
        return words.getText();
    }

    /** How many places are found, and which is gone to (-1 for none): for the tests. */
    int[] position() {
        return new int[] {found.size(), current};
    }

    /**
     * Finds the words again in the lines shown. Asked to go (the words changed), goes to the first place on or after
     * the current line; otherwise (the lines made anew), stays at the place it was at, the view where it is.
     */
    private void search(boolean go) {
        found = Search.in(view.lines(), words.getText(), matchCase.isSelected());
        if (found.isEmpty()) {
            current = -1;
        } else if (go) {
            int from = Math.max(0, view.current());
            current = 0;
            while (current < found.size() && found.get(current).line() < from) {
                current++;
            }
            current %= found.size();
        } else {
            current = Math.min(current, found.size() - 1);
        }
        Search.show(view.lines(), found, current);
        sayCount();
        if (go && current >= 0) {
            showCurrent();
        } else {
            view.repaint();
        }
    }

    private void showCurrent() {
        Search.Found place = found.get(current);
        Search.show(view.lines(), found, current);
        view.totalHeight();   // laid out, for the place's height in its line
        view.setCurrentAt(place.line(), place.top());
        sayCount();
    }

    /** "3 of 12", "No match" for words found nowhere, nothing while there are none. */
    private void sayCount() {
        if (words.getText().isBlank()) {
            count.setText(" ");
        } else if (found.isEmpty()) {
            count.setText(I18n.text("search.none"));
        } else {
            count.setText(I18n.text("search.count", current + 1, found.size()));
        }
    }

    private JButton button(String key, String tipKey, Runnable run) {
        JButton button = new JButton(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                run.run();
            }
        });
        labels.text(button, key);
        labels.tip(button, tipKey);
        button.setFocusable(false);
        return button;
    }

    /** A key of the words' field, while it has the keys. */
    private void bind(KeyStroke key, String name, Runnable run) {
        words.getInputMap(JComponent.WHEN_FOCUSED).put(key, name);
        words.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                run.run();
            }
        });
    }
}
