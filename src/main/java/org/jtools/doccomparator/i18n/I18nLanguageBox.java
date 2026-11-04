package org.jtools.doccomparator.i18n;

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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ItemEvent;
import java.util.function.Consumer;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;

/**
 * The drop list of the languages the program speaks, each named in itself ("Français", "English"), the one in use
 * chosen. Choosing another tells the listener; setting the one shown tells no one.
 */
public final class I18nLanguageBox extends JComboBox<I18nLanguage> {

    private Consumer<I18nLanguage> listener = _ -> { };
    private boolean showing;

    /** The languages, the one in use chosen; its tooltip bound with labels. */
    public I18nLanguageBox(I18nLabels labels) {
        super(I18nLanguage.values());
        setSelectedItem(I18n.language());
        setFocusable(false);
        labels.tip(this, "menu.language.tip");
        setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focused) {
                Object shown = value instanceof I18nLanguage language ? language.autonym() : value;
                return super.getListCellRendererComponent(list, shown, index, selected, focused);
            }
        });
        addItemListener(e -> {   // told twice a change: the language left, then the one chosen
            if (e.getStateChange() == ItemEvent.SELECTED && !showing && e.getItem() instanceof I18nLanguage chosen
                    && chosen != I18n.language()) {
                listener.accept(chosen);
            }
        });
    }

    /** Told the language chosen in the list. */
    public void onChoice(Consumer<I18nLanguage> chosen) {
        listener = chosen;
    }

    /** Shows this language as the one in use, telling no one. */
    public void show(I18nLanguage language) {
        showing = true;
        try {
            setSelectedItem(language);
        } finally {
            showing = false;
        }
    }

    /** As wide as its longest name, not more: in a menu bar it would take all the room left. */
    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }
}
