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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.jtools.doccomparator.i18n.I18n;

/**
 * The labels of a part of the window, each bound to its key: set as they are bound, and set again, in the language
 * then in use, by apply() — which the program calls on each of its own when the language changes; the panel's, by
 * ComparatorPanel.languageChanged().
 */
public final class I18nLabels {

    private final List<Runnable> labels = new ArrayList<>();

    /** Sets a label with this text, now and at each apply(). */
    public void bind(Consumer<String> label, String key) {
        on(() -> label.accept(I18n.text(key)));
    }

    /** Runs this now and at each apply(): for a label made of several texts, or of arguments. */
    public void on(Runnable label) {
        labels.add(label);
        label.run();
    }

    /** A button's text, now and at each apply(); the button returned. */
    public <B extends AbstractButton> B text(B button, String key) {
        bind(button::setText, key);
        return button;
    }

    /** A menu or a menu item: its text, and its first letter as its mnemonic (Alt+F, File; Alt+F, Fichier). */
    public <B extends AbstractButton> B menu(B item, String key) {
        on(() -> {
            String text = I18n.text(key);
            item.setText(text);
            item.setMnemonic(Character.toUpperCase(text.charAt(0)));
        });
        return item;
    }

    /** A label's text, now and at each apply(); the label returned. */
    public JLabel text(JLabel label, String key) {
        bind(label::setText, key);
        return label;
    }

    /** A component's tooltip, now and at each apply(); the component returned. */
    public <C extends JComponent> C tip(C component, String key) {
        bind(component::setToolTipText, key);
        return component;
    }

    /** An action's name, and its tooltip if a key is given: its buttons and menu items follow. */
    public Action action(Action action, String nameKey, String tipKey) {
        bind(name -> action.putValue(Action.NAME, name), nameKey);
        if (tipKey != null) {
            bind(tip -> action.putValue(Action.SHORT_DESCRIPTION, tip), tipKey);
        }
        return action;
    }

    /** Sets every label again, in the language in use. */
    public void apply() {
        labels.forEach(Runnable::run);
    }
}
