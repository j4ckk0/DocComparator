package org.jtools.doccomparator.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.formdev.flatlaf.FlatDarkLaf;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JRadioButton;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.jtools.doccomparator.compare.CompareOptions;
import org.jtools.doccomparator.compare.DocumentComparer;
import org.jtools.doccomparator.docx.DocxReader;
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

    /** Another look while a comparison is shown: the window takes it, the comparison laid out again in its fonts. */
    @Test
    void anotherLookIsTakenAtOnce() throws Exception {
        LookAndFeel before = UIManager.getLookAndFeel();
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
            ComparatorPanel[] panel = new ComparatorPanel[1];
            SwingUtilities.invokeAndWait(() -> {
                panel[0] = new ComparatorPanel();
                try {
                    panel[0].show(DocumentComparer.compare(
                            DocxReader.read(Path.of("samples", "procedure-revA.docx")),
                            DocxReader.read(Path.of("samples", "procedure-revB.docx")), CompareOptions.DEFAULT));
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
            ComparisonView view = (ComparisonView) find(panel[0], ComparisonView.class);
            Font metal = view.fonts().text();
            SwingUtilities.invokeAndWait(() -> {
                try {
                    UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
                } catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
                    throw new IllegalStateException(e);
                }
                SwingUtilities.updateComponentTreeUI(panel[0]);
            });
            SwingUtilities.invokeAndWait(() -> { });   // the comparison shown again, after
            assertNotEquals(metal, view.fonts().text(), "Nimbus's fonts");
            assertFalse(view.lines().isEmpty(), "the comparison still shown");
        } finally {
            UIManager.setLookAndFeel(before);
        }
    }

    /** A dark look: the diff view in dark colours; the document view on its white page still. */
    @Test
    void aDarkLookDarkensTheDiffViewNotThePage() throws Exception {
        LookAndFeel before = UIManager.getLookAndFeel();
        System.setProperty("flatlaf.useNativeLibrary", "false");
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
            SwingUtilities.invokeAndWait(() -> {
                ComparatorPanel panel = new ComparatorPanel();
                try {
                    panel.show(DocumentComparer.compare(DocxReader.read(Path.of("samples", "procedure-revA.docx")),
                            DocxReader.read(Path.of("samples", "procedure-revB.docx")), CompareOptions.DEFAULT));
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
                assertTrue(Palette.isDark(), "the diff view, dark");
                buttons(panel);   // the panel made, its parts there
                for (Component radio : radios(panel)) {
                    if (radio instanceof JRadioButton button && button.getText().equals("Document")) {
                        button.doClick();
                    }
                }
                assertFalse(Palette.isDark(), "the document view: a white page");
            });
        } finally {
            UIManager.setLookAndFeel(before);
            Palette.use(false);
        }
    }

    private static List<Component> radios(Container container) {
        List<Component> found = new ArrayList<>();
        for (Component component : container.getComponents()) {
            if (component instanceof JRadioButton) {
                found.add(component);
            }
            if (component instanceof Container inner) {
                found.addAll(radios(inner));
            }
        }
        return found;
    }

    private static Component find(Container container, Class<?> type) {
        for (Component component : container.getComponents()) {
            if (type.isInstance(component)) {
                return component;
            }
            if (component instanceof Container inner && find(inner, type) != null) {
                return find(inner, type);
            }
        }
        return null;
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
