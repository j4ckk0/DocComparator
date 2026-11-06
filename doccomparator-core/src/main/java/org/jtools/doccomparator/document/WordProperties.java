package org.jtools.doccomparator.document;

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

/**
 * The names a reader of a format other than Word gives the properties it sets in a Formatting: Word's own, since
 * Word's names are the comparison's one vocabulary, each here with the values and the unit Word gives it. Set under
 * these names, a property is compared under the switch Word's is on, and said by its name in the language in use
 * (report.PropertyNames). A property of a format that Word has not is set under a name of its own (the last ones here).
 *
 * <p>The code that reads Word's whole vocabulary — the Word reader, which copies the names as it reads them; the
 * comparison's switches (compare.Aspects); the names said (report.PropertyNames); the view's look — keeps Word's names
 * as Word writes them: most have no constant here.
 */
public final class WordProperties {

    // A run's (w:rPr)

    /** Bold: "true". */
    public static final String BOLD = "b";
    /** Italic: "true". */
    public static final String ITALIC = "i";
    /** Struck through: "true". */
    public static final String STRIKE = "strike";
    /** Underlined: "single", "double"… */
    public static final String UNDERLINE = "u";
    /** The size of the text, in half-points: "24" for 12 points. */
    public static final String SIZE = "sz";
    /** The font's family: "Arial". */
    public static final String FONT = "rFonts.ascii";
    /** The text's colour: six hexadecimal digits, "FF0000"; "auto" for the default. */
    public static final String COLOR = "color";
    /** Raised or lowered: "superscript", "subscript". */
    public static final String VERTICAL_ALIGNMENT = "vertAlign";

    // A paragraph's (w:pPr)

    /** How the text is aligned: "left", "center", "right", "both" (justified), "distribute". */
    public static final String ALIGNMENT = "jc";

    // A table row's (w:trPr)

    /** The row's height, in twentieths of a point. */
    public static final String ROW_HEIGHT = "trHeight.val";
    /** How the height applies: "exact", "atLeast". */
    public static final String ROW_HEIGHT_RULE = "trHeight.hRule";
    /** A hidden row: "true". */
    public static final String HIDDEN = "hidden";

    // A table cell's (w:tcPr)

    /** The cell's width, in twentieths of a point when CELL_WIDTH_TYPE is "dxa". */
    public static final String CELL_WIDTH = "tcW.w";
    /** The unit of CELL_WIDTH: "dxa", twentieths of a point. */
    public static final String CELL_WIDTH_TYPE = "tcW.type";
    /** The columns of the table's grid the cell spans, from its own: "2"; 1 when not set. Never compared itself. */
    public static final String GRID_SPAN = "gridSpan";
    /** A cell merged with the one below ("restart") or with the one above ("continue"). Never compared itself. */
    public static final String VERTICAL_MERGE = "vMerge";
    /** The value of VERTICAL_MERGE on the first cell of cells merged down. */
    public static final String MERGE_STARTS = "restart";
    /** The value of VERTICAL_MERGE on the cells merged into the one above. */
    public static final String MERGE_CONTINUES = "continue";
    /** The shading's pattern: "clear" for a plain fill, "solid", "pct25"… */
    public static final String SHADING_PATTERN = "shd.val";
    /** The colour of the shading's pattern: six hexadecimal digits, or "auto". */
    public static final String SHADING_COLOR = "shd.color";
    /** The colour behind the shading's pattern, the fill: six hexadecimal digits, or "auto". */
    public static final String SHADING_FILL = "shd.fill";
    /** How the text is placed from top to bottom: "top", "center", "bottom", "both". */
    public static final String CELL_VERTICAL_ALIGNMENT = "vAlign";

    // A spreadsheet cell's, which Word has not

    /** The formula that computes the cell's value, as the workbook writes it: "SUM(B2:B9)". */
    public static final String FORMULA = "formula";
    /** The number format the value is shown through: "0.00", "dd/mm/yyyy". */
    public static final String NUMBER_FORMAT = "numFmt";
    /** The text wraps within the cell: "true". */
    public static final String WRAP_TEXT = "wrapText";

    private WordProperties() {
    }

    /**
     * The border of a cell on this side ("top", "left", "bottom", "right"), and its part: "val" its style ("single",
     * "double"…), "sz" its width in eighths of a point, "color" its colour. tcBorders.top.sz, for instance.
     */
    public static String cellBorder(String side, String part) {
        return "tcBorders." + side + "." + part;
    }
}
