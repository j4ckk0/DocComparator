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

import java.util.Set;
import org.jtools.doccomparator.compare.CompareOptions.Aspect;

/**
 * The kind of difference each formatting property is, by the properties it is one of: the same name means another
 * thing in a paragraph and in a table (w:jc aligns the one's text, places the other), w:shd shades text, a paragraph or
 * a cell. A property no kind claims is none: never compared.
 */
final class Aspects {

    /** Where a property is set. */
    enum Source {
        /** w:pPr: a paragraph's, a style's, the document's default. */
        PARAGRAPH,
        /** w:rPr: a run's, a style's, the document's default. */
        RUN,
        /** w:tblPr: a table's. */
        TABLE,
        /** w:trPr: a table row's. */
        ROW,
        /** w:tcPr: a table cell's. */
        CELL
    }

    /**
     * Never compared: w:cnfStyle, the flags of a table style's conditional formatting Word writes on rows, cells and
     * paragraphs (first row, banded…), which change on every row after one inserted; the grid's own spans and merges,
     * compared as the layout of the table.
     */
    private static final Set<String> NEVER = Set.of("cnfStyle", "gridSpan", "vMerge", "hMerge", "gridBefore",
            "gridAfter", "wBefore", "wAfter", "divId");

    private Aspects() {
    }

    /** The kind of difference the property of this name is, there; null for one never compared. */
    static Aspect of(Source source, String name) {
        String root = root(name);
        if (NEVER.contains(root)) {
            return null;
        }
        return switch (source) {
            case PARAGRAPH -> switch (root) {
                case "ind", "mirrorIndents", "adjustRightInd" -> Aspect.INDENTATION;
                case "spacing", "contextualSpacing", "snapToGrid" -> Aspect.SPACING;
                case "jc", "textAlignment" -> Aspect.ALIGNMENT;
                case "outlineLvl" -> Aspect.HEADINGS;
                case "numPr" -> Aspect.LISTS;
                case "pStyle" -> Aspect.STYLES;
                case "sectPr" -> Aspect.SECTIONS;
                default -> Aspect.PARAGRAPHS;
            };
            case RUN -> root.equals("rStyle") ? Aspect.STYLES : Aspect.CHARACTERS;
            case TABLE -> switch (root) {
                case "tblStyle" -> Aspect.STYLES;
                case "tblW", "tblInd", "tblLayout", "jc", "tblpPr", "tblOverlap", "bidiVisual" -> Aspect.TABLE_LAYOUT;
                default -> Aspect.CELLS;   // tblBorders, shd, tblCellMar, tblCellSpacing, tblLook…
            };
            case ROW -> switch (root) {
                case "trHeight", "jc" -> Aspect.TABLE_LAYOUT;
                default -> Aspect.CELLS;   // tblHeader, cantSplit, hidden…
            };
            case CELL -> switch (root) {
                case "tcW" -> Aspect.TABLE_LAYOUT;
                default -> Aspect.CELLS;   // shd, tcBorders, vAlign, tcMar, textDirection, noWrap…
            };
        };
    }

    /** The element a property name comes from: "ind" of "ind.left", "tcBorders" of "tcBorders.top.sz". */
    private static String root(String name) {
        int dot = name.indexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }
}
