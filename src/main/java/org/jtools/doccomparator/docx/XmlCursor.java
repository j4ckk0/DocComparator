package org.jtools.doccomparator.docx;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * A walk through one XML part of a .docx, element by element, that fetches nothing: no DTD, no external entity. A
 * document could otherwise make the parser read another file, or reach the network (README, "No network"); Word
 * writes neither, so a part with a DOCTYPE is refused. The program parses XML here and nowhere else (NoNetworkTest).
 *
 * A reader handed the cursor on an element's start consumes it up to its end; nextChild() then moves to the next one.
 */
final class XmlCursor implements AutoCloseable {

    // WordprocessingML, as Word writes it ("transitional") and as ISO's strict variant does
    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String W_STRICT = "http://purl.oclc.org/ooxml/wordprocessingml/main";
    private static final String R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String R_STRICT = "http://purl.oclc.org/ooxml/officeDocument/relationships";
    private static final String MATH = "http://schemas.openxmlformats.org/officeDocument/2006/math";
    private static final String MATH_STRICT = "http://purl.oclc.org/ooxml/officeDocument/math";
    private static final String MARKUP_COMPATIBILITY = "http://schemas.openxmlformats.org/markup-compatibility/2006";

    /** What reads an element the cursor is on, up to its end. */
    interface ElementReader {
        void read(XmlCursor xml) throws DocxException;
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
    static XmlCursor open(InputStream in, String part) throws DocxException {
        XMLInputFactory factory = XMLInputFactory.newDefaultFactory();   // the JDK's own, whatever the classpath holds
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");   // no protocol at all, file: included
        factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
            throw new XMLStreamException("refused to fetch " + systemId);
        });
        try {
            return new XmlCursor(in, factory.createXMLStreamReader(in), part);
        } catch (XMLStreamException e) {
            throw new DocxException(part, e.getMessage());
        }
    }

    String part() {
        return part;
    }

    /** Goes to the root element, which must be this one (its local name): a part of another kind is refused. */
    void root(String localName) throws DocxException {
        while (true) {
            int event = next();
            if (event == XMLStreamConstants.DTD) {
                throw new DocxException(part, "it has a DOCTYPE, which Word never writes: refused, as it could make the "
                        + "reading fetch another file or reach the network");
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                break;
            }
            if (event == XMLStreamConstants.END_DOCUMENT) {
                throw new DocxException(part, "it holds no XML element");
            }
        }
        if (!reader.getLocalName().equals(localName)) {
            throw new DocxException(part, "<" + reader.getLocalName() + "> where a Word document has <" + localName + ">");
        }
    }

    /**
     * From an element's start, or the end of its previous child: to its next child's start (true), or to its own end
     * when there is none left (false).
     */
    boolean nextChild() throws DocxException {
        while (true) {
            switch (next()) {
                case XMLStreamConstants.START_ELEMENT:
                    return true;
                case XMLStreamConstants.END_ELEMENT:
                    return false;
                case XMLStreamConstants.END_DOCUMENT:
                    throw new DocxException(part, "the XML ends inside an element");
                default:
                    // text between elements, comments, processing instructions: nothing Word means
            }
        }
    }

    /** Hands each child to the reader, which consumes it; ends on this element's end. */
    void forChildren(ElementReader childReader) throws DocxException {
        while (nextChild()) {
            childReader.read(this);
        }
    }

    /** A content control (w:sdt): its content goes to the reader, its properties are skipped. */
    void sdtContent(ElementReader contentReader) throws DocxException {
        while (nextChild()) {
            if (isWord("sdtContent")) {
                contentReader.read(this);
            } else {
                skip();
            }
        }
    }

    /**
     * Markup compatibility's alternatives (mc:AlternateContent): the first choice goes to the reader, the others and the
     * fallback are skipped — they hold the same content again, for older versions of Word.
     */
    void firstChoice(ElementReader choiceReader) throws DocxException {
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
    void skip() throws DocxException {
        int depth = 1;
        while (depth > 0) {
            int event = next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            } else if (event == XMLStreamConstants.END_DOCUMENT) {
                throw new DocxException(part, "the XML ends inside an element");
            }
        }
    }

    /** The text of an element that holds text only (w:t), consumed. */
    String text() throws DocxException {
        try {
            return reader.getElementText();
        } catch (XMLStreamException e) {
            throw new DocxException(part, e.getMessage());
        }
    }

    String localName() {
        return reader.getLocalName();
    }

    /** In WordprocessingML's namespace. */
    boolean isWord() {
        String namespace = reader.getNamespaceURI();
        return W.equals(namespace) || W_STRICT.equals(namespace);
    }

    boolean isWord(String localName) {
        return isWord() && reader.getLocalName().equals(localName);
    }

    boolean isMath(String localName) {
        String namespace = reader.getNamespaceURI();
        return (MATH.equals(namespace) || MATH_STRICT.equals(namespace)) && reader.getLocalName().equals(localName);
    }

    boolean isMarkupCompatibility(String localName) {
        return MARKUP_COMPATIBILITY.equals(reader.getNamespaceURI()) && reader.getLocalName().equals(localName);
    }

    /** A WordprocessingML attribute of the element (w:val); null when absent. */
    String wordAttribute(String localName) {
        String value = reader.getAttributeValue(W, localName);
        return value != null ? value : reader.getAttributeValue(W_STRICT, localName);
    }

    /** A relationship attribute of the element (r:id), which names another part; null when absent. */
    String relationshipAttribute(String localName) {
        String value = reader.getAttributeValue(R, localName);
        return value != null ? value : reader.getAttributeValue(R_STRICT, localName);
    }

    /** An attribute without a namespace (those of a part's relationships); null when absent. */
    String plainAttribute(String localName) {
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            String namespace = reader.getAttributeNamespace(i);
            if ((namespace == null || namespace.isEmpty()) && reader.getAttributeLocalName(i).equals(localName)) {
                return reader.getAttributeValue(i);
            }
        }
        return null;
    }

    /** The element's WordprocessingML attributes, by local name, in their order. */
    Map<String, String> wordAttributes() {
        Map<String, String> attributes = new LinkedHashMap<>();
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            String namespace = reader.getAttributeNamespace(i);
            if (W.equals(namespace) || W_STRICT.equals(namespace)) {
                attributes.put(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
            }
        }
        return attributes;
    }

    private int next() throws DocxException {
        try {
            return reader.next();
        } catch (XMLStreamException e) {
            throw new DocxException(part, e.getMessage());
        }
    }

    @Override
    public void close() throws DocxException {
        try {
            reader.close();
            in.close();
        } catch (XMLStreamException | IOException e) {
            throw new DocxException(part, e.getMessage());
        }
    }
}
