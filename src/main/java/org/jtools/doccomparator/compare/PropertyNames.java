package org.jtools.doccomparator.compare;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jtools.doccomparator.i18n.I18n;

/**
 * Word's property names as a reader says them, in the language in use: "shd" is "shading", "tcBorders.bottom" a
 * "bottom border", "ind.left" "indent left". A name not known stays Word's own: every property is compared, those
 * without a name of ours too.
 */
final class PropertyNames {

    private static final Set<String> BORDERS = Set.of("tcBorders", "pBdr", "tblBorders", "bdr");

    /** The properties with a name of ours: "property." + their name in Word is the key of the name a reader says. */
    static final Set<String> NAMED = Set.of(
            "shd", "ind", "spacing", "jc", "trHeight", "tcW", "tblW", "tblInd", "tblLayout", "tblCellMar", "tcMar",
            "tblLook", "vAlign", "tblHeader", "cantSplit", "noWrap", "textDirection", "b", "bCs", "i", "iCs", "u",
            "strike", "dstrike", "sz", "szCs", "rFonts", "color", "highlight", "caps", "smallCaps", "vertAlign",
            "lang", "vanish", "keepNext", "keepLines", "pageBreakBefore", "widowControl", "tabs",
            "contextualSpacing", "textAlignment");
    /** The parts of a property (its attributes, a border's side) with a name of ours: "property.part." + theirs. */
    static final Set<String> PARTS = Set.of("left", "right", "start", "end", "top", "bottom", "firstLine", "hanging",
            "before", "after", "line", "lineRule", "insideH", "insideV");

    private PropertyNames() {
    }

    /** A property's name, in the language in use: "ind.left" is "indent left" in English. */
    static String of(String property) {
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
