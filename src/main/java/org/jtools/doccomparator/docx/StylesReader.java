package org.jtools.doccomparator.docx;

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

import java.util.LinkedHashMap;
import java.util.Map;
import org.jtools.doccomparator.document.Formatting;
import org.jtools.doccomparator.document.Style;
import org.jtools.doccomparator.document.StyleSheet;
import org.jtools.doccomparator.ooxml.OoxmlException;
import org.jtools.doccomparator.ooxml.XmlCursor;

/** styles.xml into a StyleSheet: the defaults, and each style's own properties. */
final class StylesReader {

    private StylesReader() {
    }

    static StyleSheet read(XmlCursor xml) throws OoxmlException {
        xml.root("styles");
        Map<String, Style> styles = new LinkedHashMap<>();
        Formatting defaultParagraph = Formatting.NONE;
        Formatting defaultRun = Formatting.NONE;
        String defaultParagraphStyle = null;
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
                String isDefault = xml.wordAttribute("default");
                Style style = readStyle(xml);
                if (style.id() != null) {
                    styles.put(style.id(), style);
                    if (("1".equals(isDefault) || "true".equals(isDefault)) && "paragraph".equals(style.type())
                            && defaultParagraphStyle == null) {
                        defaultParagraphStyle = style.id();
                    }
                }
            } else {
                xml.skip();   // w:latentStyles: how Word offers the built-in styles not used
            }
        }
        return new StyleSheet(styles, defaultParagraph, defaultRun, defaultParagraphStyle);
    }

    private static Style readStyle(XmlCursor xml) throws OoxmlException {
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
    private static Formatting readInner(XmlCursor xml, String properties) throws OoxmlException {
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
