/*
 * Copyright 2026 j4ckk0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jtools.doccomparator.compare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.jtools.doccomparator.i18n.I18n;
import org.jtools.doccomparator.i18n.I18nLanguage;
import org.junit.jupiter.api.Test;

/** Word's property names as a reader says them, in each language: every name of ours has its text in each file. */
class PropertyNamesTest {

    @Test
    void everyNameOfOursIsInEachLanguage() {
        for (I18nLanguage language : I18nLanguage.values()) {
            I18n.use(language);
            for (String name : PropertyNames.NAMED) {
                assertNotEquals("property." + name, I18n.text("property." + name), language + ": " + name);
            }
            for (String part : PropertyNames.PARTS) {
                assertNotEquals("property.part." + part, I18n.text("property.part." + part), language + ": " + part);
            }
        }
    }

    @Test
    void namesAndPartsAreSaidInTheLanguageInUse() {
        assertEquals("indent left", PropertyNames.of("ind.left"));
        assertEquals("bottom border", PropertyNames.of("tcBorders.bottom"));
        assertEquals("w14Glow", PropertyNames.of("w14Glow"), "a name not known: Word's own");
        I18n.use(I18nLanguage.FRENCH);
        assertEquals("retrait gauche", PropertyNames.of("ind.left"));
        assertEquals("bordure bas", PropertyNames.of("tcBorders.bottom"));
    }
}
