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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.jtools.doccomparator.document.ListItem;

/**
 * The marks of a side's list items as Word shows them ("2.1.", "iv)", "•"): each number counted in the order of the
 * side's paragraphs, by list and level; a level begun again under the one above it.
 */
final class ListNumbers {

    /** The most levels a list has, as Word allows them. */
    private static final int LEVELS = 9;
    /** The longest mark shown: a level's text longer than that is cut. */
    private static final int LONGEST_MARK = 6;

    /** By list, the number each level is at. */
    private final Map<String, int[]> counters = new HashMap<>();

    /** The mark of a list item, the next of its list: its level's text ("%1.", "%1.%2") numbered; a bullet as such. */
    String next(ListItem list) {
        String format = list.numberFormat() == null ? "decimal" : list.numberFormat();
        if (format.equals("bullet")) {
            return bullet(list.levelText());
        }
        int level = Math.clamp(list.level(), 0, LEVELS - 1);
        int[] numbers = counters.computeIfAbsent(list.listId(), id -> new int[LEVELS]);
        numbers[level]++;
        for (int deeper = level + 1; deeper < numbers.length; deeper++) {
            numbers[deeper] = 0;
        }
        String text = list.levelText() == null ? "%" + (level + 1) + "." : list.levelText();
        for (int l = 0; l <= level; l++) {
            text = text.replace("%" + (l + 1), number(Math.max(1, numbers[l]), l == level ? format : "decimal"));
        }
        return text.length() > LONGEST_MARK ? text.substring(0, LONGEST_MARK) : text;
    }

    /**
     * A bullet as it can be shown: Word's own bullets are often characters of the Symbol or Wingdings fonts, private
     * to them (U+F0B7…), which no other font has: those as the bullet they stand for; any other as it is ("o", "–").
     */
    static String bullet(String text) {
        if (text == null || text.isEmpty()) {
            return "•";
        }
        char c = text.charAt(0);
        if (c >= '' && c <= '') {
            return switch (c) {
                case '', '' -> "▪";
                case '' -> "➢";
                case '' -> "✓";
                default -> "•";
            };
        }
        return text;
    }

    private static String number(int n, String format) {
        return switch (format) {
            case "lowerLetter" -> String.valueOf((char) ('a' + (n - 1) % 26));
            case "upperLetter" -> String.valueOf((char) ('A' + (n - 1) % 26));
            case "lowerRoman" -> roman(n).toLowerCase(Locale.ROOT);
            case "upperRoman" -> roman(n);
            default -> String.valueOf(n);
        };
    }

    private static String roman(int n) {
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        StringBuilder roman = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            for (; n >= values[i]; n -= values[i]) {
                roman.append(symbols[i]);
            }
        }
        return roman.toString();
    }
}
