package org.jtools.doccomparator;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import org.jtools.doccomparator.i18n.I18nLabels;
import org.junit.jupiter.api.Test;

/** The Help menu: the program's licence and the runtime's, read offline, and what the program is. */
class HelpMenuTest {

    @Test
    void theMenuHoldsTheLicencesAndAbout() {
        JMenu menu = HelpMenu.of(null, new I18nLabels());
        assertEquals("Help", menu.getText());
        assertEquals(List.of("Licence…", "Embedded Java runtime…", "About DocComparator…"),
                Arrays.stream(menu.getMenuComponents()).filter(JMenuItem.class::isInstance)
                        .map(item -> ((JMenuItem) item).getText()).toList());
    }

    /** The program's licence is inside it: shown with nothing else to open. */
    @Test
    void theProgramsLicenceIsReadFromTheProgram() {
        String licence = HelpMenu.licenceFile();
        assertNotNull(licence, "packed beside the classes (pom.xml)");
        assertTrue(licence.contains("Apache License") && licence.contains("Version 2.0"), licence.substring(0, 200));
        assertTrue(HelpMenu.licenceText().contains("Apache License, Version 2.0"));
    }

    /** The runtime's licence, from the runtime itself, wherever it runs: GPLv2 with the Classpath Exception. */
    @Test
    void theRuntimesLicenceIsReadFromTheRuntime() {
        String licence = HelpMenu.runtimeLicence();
        assertNotNull(licence, "java.home/legal/java.base/LICENSE");
        assertTrue(licence.contains("GNU General Public License") && licence.contains("\"CLASSPATH\" EXCEPTION"),
                licence.substring(0, 200));
        assertTrue(HelpMenu.runtimeText().contains(System.getProperty("java.runtime.version")));
    }

    /** The home page is said, to be copied: the program opens no browser. */
    @Test
    void aboutSaysTheVersionAndTheHomePage() {
        String about = HelpMenu.aboutText();
        assertTrue(about.startsWith("DocComparator"), about);
        assertTrue(about.contains(HelpMenu.HOME_PAGE), about);
        assertTrue(about.contains("never reaches the network"), about);
    }
}
