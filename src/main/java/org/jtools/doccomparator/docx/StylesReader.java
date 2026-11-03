package org.jtools.doccomparator.docx;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jtools.doccomparator.document.Formatting;
import org.jtools.doccomparator.document.Style;
import org.jtools.doccomparator.document.StyleSheet;

/** styles.xml into a StyleSheet: the defaults, and each style's own properties. */
final class StylesReader {

    private StylesReader() {
    }

    static StyleSheet read(XmlCursor xml) throws DocxException {
        xml.root("styles");
        Map<String, Style> styles = new LinkedHashMap<>();
        Formatting defaultParagraph = Formatting.NONE;
        Formatting defaultRun = Formatting.NONE;
        while (xml.nextChild()) {
            if (xml.isWord("docDefaults")) {
                while (xml.nextChild()) {
                    if (xml.isWord("rPrDefault")) {
                        defaultRun = readInner(xml, "rPr");
                    } else if (xml.isWord("pPrDefault")) {
                        defaultParagraph = readInner(xml, "pPr");
                    } else {
                        xml.skip();
                    }
                }
            } else if (xml.isWord("style")) {
                Style style = readStyle(xml);
                if (style.id() != null) {
                    styles.put(style.id(), style);
                }
            } else {
                xml.skip();   // w:latentStyles: how Word offers the built-in styles not used
            }
        }
        return new StyleSheet(styles, defaultParagraph, defaultRun);
    }

    private static Style readStyle(XmlCursor xml) throws DocxException {
        String id = xml.wordAttribute("styleId");
        String type = xml.wordAttribute("type");
        String name = null;
        String basedOn = null;
        Formatting paragraph = Formatting.NONE;
        Formatting run = Formatting.NONE;
        while (xml.nextChild()) {
            if (xml.isWord("name")) {
                name = xml.wordAttribute("val");
                xml.skip();
            } else if (xml.isWord("basedOn")) {
                basedOn = xml.wordAttribute("val");
                xml.skip();
            } else if (xml.isWord("pPr")) {
                paragraph = FormattingReader.read(xml, () -> { });
            } else if (xml.isWord("rPr")) {
                run = FormattingReader.read(xml, () -> { });
            } else {
                xml.skip();
            }
        }
        return new Style(id, name, type, basedOn, paragraph, run);
    }

    /** The properties element of this name within the one the cursor is on. */
    private static Formatting readInner(XmlCursor xml, String properties) throws DocxException {
        Formatting formatting = Formatting.NONE;
        while (xml.nextChild()) {
            if (xml.isWord(properties)) {
                formatting = FormattingReader.read(xml, () -> { });
            } else {
                xml.skip();
            }
        }
        return formatting;
    }
}
