package org.jtools.doccomparator.report;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jtools.doccomparator.i18n.I18n;

/**
 * Word's property names as a reader says them, in the language in use: "shd" is "shading", "tcBorders.bottom" a
 * "bottom border", "ind.left" "indent left". A name not known stays Word's own: every property is compared, those
 * without a name of ours too.
 */
public final class PropertyNames {

    private static final Set<String> BORDERS = Set.of("tcBorders", "pBdr", "tblBorders", "bdr");

    /** The properties with a name of ours: "property." + their name in Word is the key of the name a reader says. */
    public static final Set<String> NAMED = Set.of(
            "shd", "ind", "spacing", "jc", "trHeight", "tcW", "tblW", "tblInd", "tblLayout", "tblCellMar", "tcMar",
            "tblLook", "vAlign", "tblHeader", "cantSplit", "noWrap", "textDirection", "b", "bCs", "i", "iCs", "u",
            "strike", "dstrike", "sz", "szCs", "rFonts", "color", "highlight", "caps", "smallCaps", "vertAlign",
            "lang", "vanish", "keepNext", "keepLines", "pageBreakBefore", "widowControl", "tabs",
            "contextualSpacing", "textAlignment", "hidden", "formula", "numFmt", "wrapText");
    /** The parts of a property (its attributes, a border's side) with a name of ours: "property.part." + theirs. */
    public static final Set<String> PARTS = Set.of("left", "right", "start", "end", "top", "bottom", "firstLine", "hanging",
            "before", "after", "line", "lineRule", "insideH", "insideV");

    private PropertyNames() {
    }

    /** A property's name, in the language in use: "ind.left" is "indent left" in English. */
    public static String of(String property) {
        String[] path = property.split("\\.");
        List<String> parts = new ArrayList<>();
        for (int i = 1; i < path.length; i++) {
            parts.add(PARTS.contains(path[i]) ? I18n.text("property.part." + path[i]) : path[i]);
        }
        String rest = String.join(" ", parts);
        if (BORDERS.contains(path[0]) && !rest.isEmpty()) {
            return I18n.text("property.border", rest);
        }
        String name = NAMED.contains(path[0]) ? I18n.text("property." + path[0]) : path[0];
        return rest.isEmpty() ? name : I18n.text("property.withPart", name, rest);
    }
}
