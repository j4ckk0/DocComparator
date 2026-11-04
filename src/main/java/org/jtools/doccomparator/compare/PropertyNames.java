package org.jtools.doccomparator.compare;

import java.util.Map;
import java.util.Set;

/**
 * Word's property names as a reader says them: "shd" is "shading", "tcBorders.bottom" a "bottom border", "ind.left" a
 * "indent left". A name not known stays Word's own: every property is compared, those without a name of ours too.
 */
final class PropertyNames {

    private static final Set<String> BORDERS = Set.of("tcBorders", "pBdr", "tblBorders", "bdr");

    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("shd", "shading"), Map.entry("ind", "indent"), Map.entry("spacing", "spacing"),
            Map.entry("jc", "alignment"), Map.entry("trHeight", "height"), Map.entry("tcW", "width"),
            Map.entry("tblW", "width"), Map.entry("tblInd", "indent"), Map.entry("tblLayout", "layout"),
            Map.entry("tblCellMar", "cell margins"), Map.entry("tcMar", "margins"),
            Map.entry("tblLook", "style options"), Map.entry("vAlign", "vertical alignment"),
            Map.entry("tblHeader", "repeated as header row"), Map.entry("cantSplit", "kept on one page"),
            Map.entry("noWrap", "no wrap"), Map.entry("textDirection", "text direction"),
            Map.entry("b", "bold"), Map.entry("bCs", "bold (complex scripts)"), Map.entry("i", "italic"),
            Map.entry("iCs", "italic (complex scripts)"), Map.entry("u", "underline"),
            Map.entry("strike", "strikethrough"), Map.entry("dstrike", "double strikethrough"),
            Map.entry("sz", "size"), Map.entry("szCs", "size (complex scripts)"), Map.entry("rFonts", "font"),
            Map.entry("color", "colour"), Map.entry("highlight", "highlight"), Map.entry("caps", "all caps"),
            Map.entry("smallCaps", "small caps"), Map.entry("vertAlign", "superscript or subscript"),
            Map.entry("lang", "language"), Map.entry("vanish", "hidden"),
            Map.entry("keepNext", "keep with next"), Map.entry("keepLines", "keep lines together"),
            Map.entry("pageBreakBefore", "page break before"), Map.entry("widowControl", "widow control"),
            Map.entry("tabs", "tabs"), Map.entry("contextualSpacing", "no space between same style"),
            Map.entry("textAlignment", "vertical text alignment"));

    private PropertyNames() {
    }

    static String of(String property) {
        int dot = property.indexOf('.');
        String root = dot < 0 ? property : property.substring(0, dot);
        String rest = dot < 0 ? "" : property.substring(dot + 1).replace('.', ' ');
        if (BORDERS.contains(root) && !rest.isEmpty()) {
            return rest + " border";
        }
        String name = NAMES.getOrDefault(root, root);
        return rest.isEmpty() ? name : name + " " + rest;
    }
}
