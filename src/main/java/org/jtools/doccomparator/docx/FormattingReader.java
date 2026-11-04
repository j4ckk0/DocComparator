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

import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import org.jtools.doccomparator.document.Formatting;

/** Properties (w:pPr, w:rPr, w:tblPr, w:tcPr…) into a Formatting, under Word's own names (see Formatting). */
final class FormattingReader {

    /**
     * Properties that are on or off: "true" and "false" whichever way the document spells them (w:b, w:b w:val="0"…).
     */
    private static final Set<String> TOGGLES = Set.of(
            "b", "bCs", "i", "iCs", "caps", "smallCaps", "strike", "dstrike", "outline", "shadow", "emboss", "imprint",
            "noProof", "snapToGrid", "vanish", "webHidden", "rtl", "cs", "specVanish", "oMath",
            "keepNext", "keepLines", "pageBreakBefore", "widowControl", "suppressLineNumbers", "suppressAutoHyphens",
            "contextualSpacing", "bidi", "mirrorIndents", "adjustRightInd", "titlePg", "noWrap",
            "tblHeader", "cantSplit", "hidden");

    private FormattingReader() {
    }

    /**
     * The properties of the element the cursor is on (a w:rPr, a w:tblPr…), consumed. A tracked change of formatting
     * (w:rPrChange…, which holds the properties as they were) is not read: changeFound is told of it.
     */
    static Formatting read(XmlCursor xml, Runnable changeFound) throws DocxException {
        SortedMap<String, String> values = new TreeMap<>();
        while (xml.nextChild()) {
            if (xml.isWord() && xml.localName().endsWith("PrChange")) {
                changeFound.run();
                xml.skip();
            } else if (xml.isWord()) {
                readProperty(xml, values);
            } else {
                xml.skip();   // another vocabulary's properties (w14:…): Word's own suffice to compare
            }
        }
        return new Formatting(values);
    }

    /** One property element, consumed, into the values. */
    static void readProperty(XmlCursor xml, SortedMap<String, String> values) throws DocxException {
        readProperty(xml, xml.localName(), values);
    }

    private static void readProperty(XmlCursor xml, String name, SortedMap<String, String> values)
            throws DocxException {
        Map<String, String> attributes = xml.wordAttributes();
        for (Map.Entry<String, String> attribute : attributes.entrySet()) {
            if (attribute.getKey().equals("val")) {
                values.put(name, TOGGLES.contains(name) ? toggle(attribute.getValue()) : attribute.getValue());
            } else {
                values.put(name + "." + attribute.getKey(), attribute.getValue());
            }
        }
        boolean hasChildren = false;
        while (xml.nextChild()) {
            hasChildren = true;
            if (xml.isWord()) {
                readProperty(xml, name + "." + xml.localName(), values);
            } else {
                xml.skip();
            }
        }
        if (attributes.isEmpty() && !hasChildren) {
            values.put(name, "true");   // <w:b/>: on
        }
    }

    private static String toggle(String value) {
        return switch (value) {
            case "0", "false", "off" -> "false";
            case "1", "true", "on" -> "true";
            default -> value;
        };
    }
}
