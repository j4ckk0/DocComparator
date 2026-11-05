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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jtools.doccomparator.compare.CompareOptions;
import org.jtools.doccomparator.compare.DocumentComparer;
import org.jtools.doccomparator.compare.Pairing;
import org.jtools.doccomparator.document.WordDocument;
import org.junit.jupiter.api.Test;

/** The sample workbooks (samples/make_workbooks.py), as LibreOffice writes them, and their differences. */
class WorkbookSamplesTest {

    private static WordDocument sample(String name) throws IOException {
        return new XlsxReader().read(Path.of("..", "samples", name));
    }

    /** Every cell pairing of every table, as "status: left | right, its properties' differences". */
    private static List<String> cells(WordDocument left, WordDocument right, CompareOptions options) {
        List<String> said = new ArrayList<>();
        for (Pairing pairing : DocumentComparer.compare(left, right, options).stories().getFirst().pairings()) {
            if (pairing.table() == null) {
                continue;
            }
            for (List<Pairing> row : pairing.table().cells()) {
                for (Pairing cell : row) {
                    if (cell != null && cell.differs()) {
                        said.add(cell.status() + ": " + (cell.left() == null ? "" : cell.left().text()) + " | "
                                + (cell.right() == null ? "" : cell.right().text()) + " "
                                + cell.diff().properties());
                    }
                }
            }
        }
        return said;
    }

    @Test
    void aWorkbookComparedWithItselfIsTheSame() throws IOException {
        WordDocument budget = sample("budget-revA.xlsx");
        assertEquals(List.of(), cells(budget, budget, CompareOptions.ALL));
    }

    @Test
    void theNextVersionsChangesAreFound() throws IOException {
        List<String> said = cells(sample("budget-revA.xlsx"), sample("budget-revB.xlsx"), CompareOptions.ALL);
        String all = String.join("\n", said);
        assertTrue(said.contains("CHANGED: 85.50 | 92.00 [compared.ofCell(tcW.w): 1335 → 1710]"), all);
        assertTrue(said.contains("RIGHT_ONLY:  | Pressure gauge []"), "the row inserted\n" + all);
        assertTrue(all.contains("compared.ofCell(formula): SUM(B3:B5) → SUBTOTAL(9,B3:B6)"), all);
        assertTrue(all.contains("OTHERWISE: Item | Item [compared.ofCell(shd.fill): D9E2F3 → FCE4D6]"), all);
        assertTrue(said.contains("CHANGED: Drain the circuit | Replace the filter []"), "the steps swapped\n" + all);
        List<String> textOnly = cells(sample("budget-revA.xlsx"), sample("budget-revB.xlsx"), CompareOptions.TEXT_ONLY);
        assertTrue(textOnly.stream().noneMatch(cell -> cell.contains("compared.")),
                "no look, no formula, no width with Text only\n" + String.join("\n", textOnly));
    }
}
