package org.jtools.doccomparator.i18n;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JLabel;

/**
 * The labels of a part of the window, each bound to its key: set as they are bound, and set again, in the language
 * then in use, by apply() — which the window calls on each part when the language changes.
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

    public <B extends AbstractButton> B text(B button, String key) {
        bind(button::setText, key);
        return button;
    }

    public JLabel text(JLabel label, String key) {
        bind(label::setText, key);
        return label;
    }

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
