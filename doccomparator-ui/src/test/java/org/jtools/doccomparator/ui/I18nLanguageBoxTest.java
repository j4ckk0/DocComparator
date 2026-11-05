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

import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JList;
import org.jtools.doccomparator.i18n.I18nLanguage;
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
