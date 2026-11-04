package org.jtools.doccomparator.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JList;
import org.junit.jupiter.api.Test;

/** The languages' drop list: each named in itself; a choice told, the language shown set without telling. */
class I18nLanguageBoxTest {

    @Test
    void aChoiceIsToldTheLanguageShownIsNot() {
        I18nLanguageBox box = new I18nLanguageBox(new I18nLabels());
        List<I18nLanguage> told = new ArrayList<>();
        box.onChoice(told::add);
        assertEquals(I18nLanguage.ENGLISH, box.getSelectedItem(), "the one in use");
        box.show(I18nLanguage.FRENCH);
        assertEquals(List.of(), told, "shown: no one told");
        box.show(I18nLanguage.ENGLISH);
        box.setSelectedItem(I18nLanguage.FRENCH);
        assertEquals(List.of(I18nLanguage.FRENCH), told, "chosen: told");
    }

    @Test
    void eachLanguageIsListedByItsOwnName() {
        I18nLanguageBox box = new I18nLanguageBox(new I18nLabels());
        JList<I18nLanguage> list = new JList<>();
        List<String> names = new ArrayList<>();
        for (I18nLanguage language : I18nLanguage.values()) {
            names.add(((JLabel) box.getRenderer().getListCellRendererComponent(list, language, 0, false, false))
                    .getText());
        }
        assertEquals(List.of("Français", "English"), names);
        assertEquals("The program's language", box.getToolTipText());
    }
}
