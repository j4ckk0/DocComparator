package org.jtools.doccomparator.xlsx;


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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** References and shared formulas; colours lightened and darkened. */
class FormulasAndColorsTest {

    @Test
    void columnsAndReferences() {
        assertEquals(1, Formulas.column("A"));
        assertEquals(27, Formulas.column("AA"));
        assertEquals("AZ", Formulas.letters(52));
        assertArrayEquals(new int[] {7, 2}, Formulas.cell("$B$7"));
        assertNull(Formulas.cell("Total"));
    }

    @Test
    void aSharedFormulaShiftsItsRelativeReferencesOnly() {
        assertEquals("A3*2+$A$1", Formulas.shift("A2*2+$A$1", 1, 0));
        assertEquals("SUM(C2:C9)", Formulas.shift("SUM(B2:B9)", 0, 1));
        assertEquals("$B3+B$2", Formulas.shift("$B2+A$2", 1, 1), "each half of a mixed reference");
        assertEquals("\"A1\"&B5", Formulas.shift("\"A1\"&B4", 1, 0), "a string is not a reference");
        assertEquals("LOG10(B5)", Formulas.shift("LOG10(B4)", 1, 0), "a function's name is not either");
        assertEquals("'Sheet 2'!B5", Formulas.shift("'Sheet 2'!B4", 1, 0));
    }

    @Test
    void tintsOnLuminanceAsExcel() {
        assertEquals("BFBFBF", Colors.tinted("FFFFFF", -0.25), "white, 25% darker");
        assertEquals("7F7F7F", Colors.tinted("000000", 0.5), "black, 50% lighter");
        assertEquals("2F5496", Colors.tinted("4472C4", -0.25), "blue accent, 25% darker");
    }
}
