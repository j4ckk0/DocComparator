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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cell references, as Excel writes them (A1, $B$7, AA10), and the formulas holding them: a shared formula is written
 * once, on its first cell; the others are that formula shifted by their distance from it, its relative references
 * moved, its absolute ones ($) kept.
 */
final class Formulas {

    /**
     * A reference, not part of a name, a function or a number: column letters then a row, each perhaps absolute. A
     * sheet's name before it ('Sheet 1'!A1) is left as it is.
     */
    private static final Pattern REFERENCE =
            Pattern.compile("(?<![A-Za-z0-9_.\\\\])(\\$?)([A-Z]{1,3})(\\$?)([0-9]{1,7})(?![A-Za-z0-9_(])");

    private Formulas() {
    }

    /** The column's number from its letters: A is 1, Z 26, AA 27. */
    static int column(String letters) {
        int column = 0;
        for (int i = 0; i < letters.length(); i++) {
            column = column * 26 + (letters.charAt(i) - 'A' + 1);
        }
        return column;
    }

    /** The letters of a column's number: 1 is A, 27 AA. */
    static String letters(int column) {
        StringBuilder letters = new StringBuilder();
        for (int n = column; n > 0; n = (n - 1) / 26) {
            letters.insert(0, (char) ('A' + (n - 1) % 26));
        }
        return letters.toString();
    }

    /** A reference's row and column ("B7" → {7, 2}); null for what is not one. */
    static int[] cell(String reference) {
        Matcher m = Pattern.compile("\\$?([A-Z]{1,3})\\$?([0-9]{1,7})").matcher(reference);
        return m.matches() ? new int[] {Integer.parseInt(m.group(2)), column(m.group(1))} : null;
    }

    /** The formula moved down rows and right columns, its relative references with it; strings left alone. */
    static String shift(String formula, int rows, int columns) {
        StringBuilder out = new StringBuilder();
        boolean quoted = false;
        int start = 0;
        for (int i = 0; i <= formula.length(); i++) {
            if (i == formula.length() || formula.charAt(i) == '"') {
                String piece = formula.substring(start, i);
                out.append(quoted ? piece : shiftReferences(piece, rows, columns));
                if (i < formula.length()) {
                    out.append('"');
                }
                quoted = !quoted;
                start = i + 1;
            }
        }
        return out.toString();
    }

    private static String shiftReferences(String text, int rows, int columns) {
        Matcher m = REFERENCE.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            int column = column(m.group(2));
            int row = Integer.parseInt(m.group(4));
            String moved = m.group(1) + (m.group(1).isEmpty() ? letters(Math.max(1, column + columns)) : m.group(2))
                    + m.group(3) + (m.group(3).isEmpty() ? Math.max(1, row + rows) : row);
            m.appendReplacement(out, Matcher.quoteReplacement(moved));
        }
        m.appendTail(out);
        return out.toString();
    }
}
