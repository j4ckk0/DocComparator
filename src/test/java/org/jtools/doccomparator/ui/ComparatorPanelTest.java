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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JRadioButton;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.jtools.doccomparator.compare.CompareOptions;
import org.jtools.doccomparator.compare.DocumentComparer;
import org.jtools.doccomparator.docx.DocxReader;
import org.jtools.doccomparator.i18n.I18n;
import org.jtools.doccomparator.i18n.I18nLanguage;
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
                ComparisonView view = (ComparisonView) find(panel, ComparisonView.class);
                assertTrue(view.palette().dark, "the diff view, dark");
                buttons(panel);   // the panel made, its parts there
                for (Component radio : radios(panel)) {
                    if (radio instanceof JRadioButton button && button.getText().equals("Document")) {
                        button.doClick();
                    }
                }
                assertFalse(view.palette().dark, "the document view: a white page");
            });
        } finally {
            UIManager.setLookAndFeel(before);
        }
    }

    /**
     * Another language: every label of the panel, its menus and tooltips included, in it at once — but those its
     * files write the same in both ("Document", "Styles").
     */
    @Test
    void anotherLanguageRelabelsEverything() throws Exception {
        Properties english = texts("en");
        Properties french = texts("fr");
        Set<String> same = new HashSet<>();
        english.stringPropertyNames().stream().filter(key -> english.getProperty(key).equals(french.getProperty(key)))
                .forEach(key -> same.add(english.getProperty(key)));
        SwingUtilities.invokeAndWait(() -> {
            ComparatorPanel panel = new ComparatorPanel();
            List<Component> parts = new ArrayList<>(List.of(panel, panel.viewMenu(), panel.compareMenu()));
            Map<Component, List<String>> before = new IdentityHashMap<>();
            parts.forEach(part -> labels(part, before));
            I18n.use(I18nLanguage.FRENCH);
            panel.languageChanged();
            Map<Component, List<String>> after = new IdentityHashMap<>();
            parts.forEach(part -> labels(part, after));
            assertTrue(before.size() > 60, "the labels found: " + before.size());
            before.forEach((component, labels) -> {
                for (int i = 0; i < labels.size(); i++) {
                    String was = labels.get(i);
                    if (!same.contains(was)) {
                        assertNotEquals(was, after.get(component).get(i), "still in English");
                    }
                }
            });
        });
    }

    /** Each label and tooltip of a component and of those it holds, menus' items included, " " for none. */
    private static void labels(Component component, Map<Component, List<String>> found) {
        List<String> labels = new ArrayList<>();
        if (component instanceof AbstractButton button && button.getText() != null && !button.getText().isBlank()) {
            labels.add(button.getText());
        }
        if (component instanceof JLabel label && label.getText() != null && !label.getText().isBlank()) {
            labels.add(label.getText());
        }
        if (component instanceof JComponent part && part.getToolTipText() != null && !part.getToolTipText().isBlank()) {
            labels.add(part.getToolTipText());
        }
        if (!labels.isEmpty()) {
            found.put(component, labels);
        }
        if (component instanceof JMenu menu) {
            for (Component item : menu.getMenuComponents()) {
                labels(item, found);
            }
        }
        if (component instanceof Container container) {
            for (Component inner : container.getComponents()) {
                labels(inner, found);
            }
        }
    }

    private static Properties texts(String code) throws IOException {
        Properties texts = new Properties();
        try (InputStream in = I18n.class.getResourceAsStream("messages_" + code + ".properties")) {
            texts.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return texts;
    }

    /** Text pasted on each side: compared as two documents are, a paragraph a line; the title saying so. */
    @Test
    void pastedTextsAreCompared() throws Exception {
        ComparatorPanel[] panel = new ComparatorPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new ComparatorPanel();
            panel[0].paste(true, "one\ntwo\nthree");
            panel[0].paste(false, "one\r\n2\r\nthree\r\n");
        });
        int[] differences = new int[1];
        for (int wait = 0; wait < 500 && differences[0] == 0; wait++) {   // compared away from the window's thread
            Thread.sleep(10);
            SwingUtilities.invokeAndWait(() -> differences[0] = view(panel[0]).differenceCount()[0]);
        }
        assertEquals(1, differences[0], "two → 2");
        assertEquals("Pasted text ↔ Pasted text — DocComparator", panel[0].title());
    }

    /** No document open: the empty sides dark in a dark look, from the start and once the look turns dark. */
    @Test
    void theEmptyWindowTakesTheLooksColours() throws Exception {
        LookAndFeel before = UIManager.getLookAndFeel();
        System.setProperty("flatlaf.useNativeLibrary", "false");
        try {
            ComparatorPanel[] panel = new ComparatorPanel[1];
            SwingUtilities.invokeAndWait(() -> {
                try {
                    UIManager.setLookAndFeel(new FlatDarkLaf());
                } catch (UnsupportedLookAndFeelException e) {
                    throw new IllegalStateException(e);
                }
                panel[0] = new ComparatorPanel();
                assertTrue(view(panel[0]).palette().dark, "dark from the start");
                try {
                    UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
                } catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
                    throw new IllegalStateException(e);
                }
                SwingUtilities.updateComponentTreeUI(panel[0]);
            });
            SwingUtilities.invokeAndWait(() -> { });   // the look taken, after
            SwingUtilities.invokeAndWait(() -> assertFalse(view(panel[0]).palette().dark, "light once the look is"));
        } finally {
            UIManager.setLookAndFeel(before);
        }
    }

    private static ComparisonView view(ComparatorPanel panel) {
        return (ComparisonView) find(panel, ComparisonView.class);
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
