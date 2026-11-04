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
package org.jtools.doccomparator.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jtools.doccomparator.document.ListItem;
import org.junit.jupiter.api.Test;

/** List items numbered as Word numbers them, in the order of the paragraphs. */
class ListNumbersTest {

    @Test
    void levelsCountUnderTheOneAboveAndBeginAgainUnderTheNext() {
        ListNumbers numbers = new ListNumbers();
        assertEquals("1.", numbers.next(new ListItem("1", 0, "decimal", "%1.")));
        assertEquals("1.1.", numbers.next(new ListItem("1", 1, "decimal", "%1.%2.")));
        assertEquals("1.2.", numbers.next(new ListItem("1", 1, "decimal", "%1.%2.")));
        assertEquals("2.", numbers.next(new ListItem("1", 0, "decimal", "%1.")));
        assertEquals("2.1.", numbers.next(new ListItem("1", 1, "decimal", "%1.%2.")));
    }

    @Test
    void eachListCountsOnItsOwn() {
        ListNumbers numbers = new ListNumbers();
        assertEquals("1.", numbers.next(new ListItem("1", 0, "decimal", "%1.")));
        assertEquals("a)", numbers.next(new ListItem("2", 0, "lowerLetter", "%1)")));
        assertEquals("2.", numbers.next(new ListItem("1", 0, "decimal", "%1.")));
        assertEquals("b)", numbers.next(new ListItem("2", 0, "lowerLetter", "%1)")));
    }

    @Test
    void romanNumbersAndBullets() {
        ListNumbers numbers = new ListNumbers();
        for (int i = 1; i < 4; i++) {
            numbers.next(new ListItem("1", 0, "upperRoman", "%1."));
        }
        assertEquals("IV.", numbers.next(new ListItem("1", 0, "upperRoman", "%1.")));
        assertEquals("•", numbers.next(new ListItem("3", 0, "bullet", "")), "Symbol's bullet, as such");
        assertEquals("▪", numbers.next(new ListItem("3", 0, "bullet", "")), "Wingdings' square");
        assertEquals("o", numbers.next(new ListItem("3", 1, "bullet", "o")), "any other as it is");
    }

    @Test
    void aLevelWithoutTextIsNumberedAsWordDoesByDefault() {
        assertEquals("1.", new ListNumbers().next(new ListItem("1", 0, null, null)));
    }
}
