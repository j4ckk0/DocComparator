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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.jtools.doccomparator.i18n.texts.CoreTexts;
import org.jtools.doccomparator.testing.TextsCheck;
import org.junit.jupiter.api.Test;

/** The languages, and the texts of this jar, the comparison's (TextsCheck). */
class I18nTest {

    @Test
    void theComparisonsTextsAreWhole() throws IOException {
        TextsCheck.check(new CoreTexts(), Path.of("src", "main", "java"));
    }

    /** I18nEnglishInTests is found and run: the tests' expected texts are English ones. */
    @Test
    void testsSpeakEnglish() {
        assertEquals(I18nLanguage.ENGLISH, I18n.language());
    }

    @Test
    void eachLanguageIsNamedInItselfAnUnknownOneIsTheDefault() {
        I18n.use(I18nLanguage.FRENCH);
        assertEquals("Français", I18nLanguage.FRENCH.autonym());
        assertEquals("English", I18nLanguage.ENGLISH.autonym());
        assertEquals(I18nLanguage.FRENCH, I18nLanguage.ofCode("xx"), "unknown: the default");
        assertEquals("a.key.not.there", I18n.text("a.key.not.there"), "a key missing: itself, the program goes on");
    }

    @Test
    void literalsAreReadOutsideComments() {
        assertEquals(List.of("a.b", "c"),
                TextsCheck.literals("x(\"a.b\"); // \"not\"\n/* \"nor\" */ y('\"', \"c\");"));
    }
}
