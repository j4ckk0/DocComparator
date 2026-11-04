package org.jtools.doccomparator.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Action;
import javax.swing.JButton;
import org.junit.jupiter.api.Test;

/** The panel, made without a screen: what its buttons do. */
class ComparatorPanelTest {

    @Test
    void eachSidesOpenButtonOpensItsSide() {
        List<JButton> open = buttons(new ComparatorPanel()).stream()
                .filter(button -> "Open…".equals(button.getText())).toList();
        assertEquals(2, open.size(), "one on each side's head");
        assertNotNull(open.get(0).getAction(), "the left one does something");
        assertNotNull(open.get(1).getAction(), "the right one does something");
        assertEquals("Open left…", open.get(0).getAction().getValue(Action.NAME));
        assertEquals("Open right…", open.get(1).getAction().getValue(Action.NAME));
    }

    /** Every button of ours, each with its label (not the scroll bars' arrows, Swing's own), does something. */
    @Test
    void everyButtonDoesSomething() {
        for (JButton button : buttons(new ComparatorPanel())) {
            if (button.getText() == null || button.getText().isEmpty()) {
                continue;
            }
            assertNotNull(button.getAction() != null ? button.getAction()
                    : button.getActionListeners().length > 0 ? button : null, button.getText());
        }
    }

    private static List<JButton> buttons(Container container) {
        List<JButton> buttons = new ArrayList<>();
        for (Component component : container.getComponents()) {
            if (component instanceof JButton button) {
                buttons.add(button);
            }
            if (component instanceof Container inner) {
                buttons.addAll(buttons(inner));
            }
        }
        return buttons;
    }
}
