package org.jtools.doccomparator.ooxml;

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

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.jtools.doccomparator.i18n.I18n;

/**
 * A walk through one XML part of an Office Open XML file, element by element, that fetches nothing: no DTD, no external
 * entity. A file could otherwise make the parser read another file, or reach the network (README, "No network");
 * Office writes neither, so a part with a DOCTYPE is refused. The program parses XML here and nowhere else
 * (NoNetworkTest).
 *
 * A reader handed the cursor on an element's start consumes it up to its end; nextChild() then moves to the next one.
 */
public final class XmlCursor implements AutoCloseable {

    // WordprocessingML, as Word writes it ("transitional") and as ISO's strict variant does
    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String W_STRICT = "http://purl.oclc.org/ooxml/wordprocessingml/main";
    // SpreadsheetML, likewise
    private static final String X = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String X_STRICT = "http://purl.oclc.org/ooxml/spreadsheetml/main";
    private static final String R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String R_STRICT = "http://purl.oclc.org/ooxml/officeDocument/relationships";
    private static final String MATH = "http://schemas.openxmlformats.org/officeDocument/2006/math";
    private static final String MATH_STRICT = "http://purl.oclc.org/ooxml/officeDocument/math";
    private static final String MARKUP_COMPATIBILITY = "http://schemas.openxmlformats.org/markup-compatibility/2006";

    /** What reads an element the cursor is on, up to its end. */
    public interface ElementReader {
        void read(XmlCursor xml) throws OoxmlException;
    }

    private final InputStream in;
    private final XMLStreamReader reader;
    private final String part;

    private XmlCursor(InputStream in, XMLStreamReader reader, String part) {
        this.in = in;
        this.reader = reader;
        this.part = part;
    }

    /** The cursor before the part's root; root() goes to it. Closing the cursor closes the stream. */
    public static XmlCursor open(InputStream in, String part) throws OoxmlException {
        XMLInputFactory factory = XMLInputFactory.newDefaultFactory();   // the JDK's own, whatever the classpath holds
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");   // no protocol at all, file: included
        factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
            throw new XMLStreamException(I18n.text("error.fetchRefused", systemId));
        });
        try {
            return new XmlCursor(in, factory.createXMLStreamReader(in), part);
        } catch (XMLStreamException e) {
            throw new OoxmlException(part, e.getMessage());
        }
    }

    public String part() {
        return part;
    }

    /** Goes to the root element, which must be this one (its local name): a part of another kind is refused. */
    public void root(String localName) throws OoxmlException {
        while (true) {
            int event = next();
            if (event == XMLStreamConstants.DTD) {
                throw new OoxmlException(part, I18n.text("error.doctype"));
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                break;
            }
            if (event == XMLStreamConstants.END_DOCUMENT) {
                throw new OoxmlException(part, I18n.text("error.noElement"));
            }
        }
        if (!reader.getLocalName().equals(localName)) {
            throw new OoxmlException(part, I18n.text("error.wrongRoot", reader.getLocalName(), localName));
        }
    }

    /**
     * From an element's start, or the end of its previous child: to its next child's start (true), or to its own end
     * when there is none left (false).
     */
    public boolean nextChild() throws OoxmlException {
        while (true) {
            switch (next()) {
                case XMLStreamConstants.START_ELEMENT:
                    return true;
                case XMLStreamConstants.END_ELEMENT:
                    return false;
                case XMLStreamConstants.END_DOCUMENT:
                    throw new OoxmlException(part, I18n.text("error.truncated"));
                default:
                    // text between elements, comments, processing instructions: nothing Word means
            }
        }
    }

    /** Hands each child to the reader, which consumes it; ends on this element's end. */
    public void forChildren(ElementReader childReader) throws OoxmlException {
        while (nextChild()) {
            childReader.read(this);
        }
    }

    /** A content control (w:sdt): its content goes to the reader, its properties are skipped. */
    public void sdtContent(ElementReader contentReader) throws OoxmlException {
        while (nextChild()) {
            if (isWord("sdtContent")) {
                contentReader.read(this);
            } else {
                skip();
            }
        }
    }

    /**
     * Markup compatibility's alternatives (mc:AlternateContent): the first choice goes to the reader, the others and
     * the fallback are skipped — they hold the same content again, for older versions of Word.
     */
    public void firstChoice(ElementReader choiceReader) throws OoxmlException {
        boolean chosen = false;
        while (nextChild()) {
            if (!chosen && isMarkupCompatibility("Choice")) {
                chosen = true;
                choiceReader.read(this);
            } else {
                skip();
            }
        }
    }

    /** Consumes the element, whatever it holds. */
    public void skip() throws OoxmlException {
        int depth = 1;
        while (depth > 0) {
            int event = next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            } else if (event == XMLStreamConstants.END_DOCUMENT) {
                throw new OoxmlException(part, I18n.text("error.truncated"));
            }
        }
    }

    /** The text of an element that holds text only (w:t), consumed. */
    public String text() throws OoxmlException {
        try {
            return reader.getElementText();
        } catch (XMLStreamException e) {
            throw new OoxmlException(part, e.getMessage());
        }
    }

    public String localName() {
        return reader.getLocalName();
    }

    /** In WordprocessingML's namespace. */
    public boolean isWord() {
        String namespace = reader.getNamespaceURI();
        return W.equals(namespace) || W_STRICT.equals(namespace);
    }

    public boolean isWord(String localName) {
        return isWord() && reader.getLocalName().equals(localName);
    }

    /** In SpreadsheetML's namespace. */
    public boolean isSpreadsheet() {
        String namespace = reader.getNamespaceURI();
        return X.equals(namespace) || X_STRICT.equals(namespace);
    }

    public boolean isSpreadsheet(String localName) {
        return isSpreadsheet() && reader.getLocalName().equals(localName);
    }

    public boolean isMath(String localName) {
        String namespace = reader.getNamespaceURI();
        return (MATH.equals(namespace) || MATH_STRICT.equals(namespace)) && reader.getLocalName().equals(localName);
    }

    public boolean isMarkupCompatibility(String localName) {
        return MARKUP_COMPATIBILITY.equals(reader.getNamespaceURI()) && reader.getLocalName().equals(localName);
    }

    /** A WordprocessingML attribute of the element (w:val); null when absent. */
    public String wordAttribute(String localName) {
        String value = reader.getAttributeValue(W, localName);
        return value != null ? value : reader.getAttributeValue(W_STRICT, localName);
    }

    /** A relationship attribute of the element (r:id), which names another part; null when absent. */
    public String relationshipAttribute(String localName) {
        String value = reader.getAttributeValue(R, localName);
        return value != null ? value : reader.getAttributeValue(R_STRICT, localName);
    }

    /** An attribute without a namespace (a relationship's, a drawing's: cx, descr…); null when absent. */
    public String plainAttribute(String localName) {
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            String namespace = reader.getAttributeNamespace(i);
            if ((namespace == null || namespace.isEmpty()) && reader.getAttributeLocalName(i).equals(localName)) {
                return reader.getAttributeValue(i);
            }
        }
        return null;
    }

    /** The element's WordprocessingML attributes, by local name, in their order. */
    public Map<String, String> wordAttributes() {
        Map<String, String> attributes = new LinkedHashMap<>();
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            String namespace = reader.getAttributeNamespace(i);
            if (W.equals(namespace) || W_STRICT.equals(namespace)) {
                attributes.put(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
            }
        }
        return attributes;
    }

    private int next() throws OoxmlException {
        try {
            return reader.next();
        } catch (XMLStreamException e) {
            throw new OoxmlException(part, e.getMessage());
        }
    }

    @Override
    public void close() throws OoxmlException {
        try {
            reader.close();
            in.close();
        } catch (XMLStreamException | IOException e) {
            throw new OoxmlException(part, e.getMessage());
        }
    }
}
