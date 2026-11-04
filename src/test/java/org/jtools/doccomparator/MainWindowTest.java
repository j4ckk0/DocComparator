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

import org.junit.jupiter.api.Test;

/** The window's title, without a window: the documents' names as the panel says them, then the program's. */
class MainWindowTest {

    /** The program's name, and its version when run from the jar: the title's end, whatever the documents. */
    private static final String PROGRAM = MainWindow.title("");

    @Test
    void theTitleNamesTheDocumentsThenTheProgram() {
        assertEquals("a.docx ↔ b.docx — " + PROGRAM, MainWindow.title("a.docx ↔ b.docx"));
    }

    /** A document named after the program keeps its name: the title is put together, nothing in it replaced. */
    @Test
    void aDocumentNamedAfterTheProgramKeepsItsName() {
        String names = "DocComparator notes.docx ↔ …";
        assertEquals(names + " — " + PROGRAM, MainWindow.title(names));
    }
}
