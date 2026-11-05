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

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** Numbers as Excel shows them, the same on every machine. */
class NumberFormatsTest {

    private static String shown(double value, String code) {
        return NumberFormats.format(value, code, false);
    }

    @Test
    void generalShowsTheValueToFifteenDigits() {
        assertEquals("0.3", shown(0.1 + 0.2, "General"), "binary's noise beyond Excel's 15 digits");
        assertEquals("1234567.891", shown(1234567.891, "General"));
        assertEquals("-5", shown(-5, "General"));
        assertEquals("1E+20", shown(1e20, "General"));
        assertEquals("1.5E-12", shown(1.5e-12, "General"));
        assertEquals("0", shown(0, "General"));
        assertEquals("12.5 kg", shown(12.5, "General\" kg\""), "General among literals");
        assertEquals("42", shown(42, "@"), "text's format, given a number");
    }

    @Test
    void decimalsThousandsPercents() {
        assertEquals("3.14", shown(3.14159, "0.00"));
        assertEquals("3", shown(3.14159, "0"));
        assertEquals("1,234,568", shown(1234567.891, "#,##0"));
        assertEquals("1,234,567.89", shown(1234567.891, "#,##0.00"));
        assertEquals("0.50", shown(0.5, "#,##0.00"));
        assertEquals("12.5%", shown(0.125, "0.0%"));
        assertEquals("13%", shown(0.125, "0%"), "half up, as Excel");
        assertEquals("1.23E+03", shown(1234, "0.00E+00"));
        assertEquals("1.5", shown(1.5, "0.##"));
        assertEquals("2", shown(2, "0.##"), "no decimal point left alone: Excel shows 2.; close enough");
        assertEquals("1,235", shown(1234567, "#,##0,"), "a trailing comma: thousands");
    }

    @Test
    void sectionsCurrencyAndLiterals() {
        assertEquals("€ 1,234.50", shown(1234.5, "[$€-40C] #,##0.00"));
        assertEquals("(1,234.50)", shown(-1234.5, "#,##0.00;(#,##0.00)"), "the negative section, without a minus");
        assertEquals("-1,234.50", shown(-1234.5, "#,##0.00"), "one section: the minus added");
        assertEquals("zero", shown(0, "0;-0;\"zero\""));
        assertEquals("5 pcs", shown(5, "0\" pcs\""));
        assertEquals("5 pcs", shown(5, "0\\ \\p\\c\\s"));
        assertEquals("1,234.50", shown(1234.5, "[Red]#,##0.00"), "a colour is not shown");
    }

    @Test
    void datesAndTimesFromTheSerialNumber() {
        assertEquals(LocalDateTime.of(1900, 2, 28, 0, 0), NumberFormats.dateTime(59, false));
        assertEquals(LocalDateTime.of(1900, 3, 1, 0, 0), NumberFormats.dateTime(61, false),
                "past Excel's 29 February 1900");
        assertEquals(LocalDateTime.of(2026, 11, 5, 0, 0), NumberFormats.dateTime(46_331, false));
        assertEquals(LocalDateTime.of(2030, 11, 6, 0, 0), NumberFormats.dateTime(46_331, true), "from 1904");
        assertEquals("2026-11-05", shown(46_331, "yyyy-mm-dd"));
        assertEquals("2026-11-05", NumberFormats.format(46_331, NumberFormats.builtIn(14), false),
                "the machine's short date: the same everywhere");
        assertEquals("05/11/2026 18:30", shown(46_331.770833333, "dd/mm/yyyy hh:mm"), "m after h: minutes");
        assertEquals("Nov 5, 2026", shown(46_331, "mmm d, yyyy"));
        assertEquals("Thursday 5 November", shown(46_331, "dddd d mmmm"));
        assertEquals("6:30 PM", shown(46_331.770833333, "h:mm AM/PM"));
        assertEquals("30:15:00", shown(1.260416667, "[h]:mm:ss"), "elapsed: past a day");
        assertEquals("02:03.5", shown(123.5 / 86_400, "mm:ss.0"));
    }
}
