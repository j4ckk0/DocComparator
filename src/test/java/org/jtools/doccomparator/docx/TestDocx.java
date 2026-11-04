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
package org.jtools.doccomparator.docx;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A .docx for a test, built from the XML of its parts as Word writes them: the body, and whatever parts and
 * relationships the test adds. The package's relationship part and the main document's are made from those added.
 */
final class TestDocx {

    static final String RELATIONSHIPS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/";

    /** The namespaces of Word's parts, declared on each root. */
    static final String NAMESPACES = String.join(" ",
            "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"",
            "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"",
            "xmlns:mc=\"http://schemas.openxmlformats.org/markup-compatibility/2006\"",
            "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\"",
            "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"",
            "xmlns:wps=\"http://schemas.microsoft.com/office/word/2010/wordprocessingShape\"",
            "xmlns:v=\"urn:schemas-microsoft-com:vml\"",
            "xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\"",
            "xmlns:w14=\"http://schemas.microsoft.com/office/word/2010/wordml\"",
            "mc:Ignorable=\"w14\"");

    private record Link(String id, String type, String target, boolean external) {
    }

    private final Map<String, String> parts = new LinkedHashMap<>();
    private final Map<String, byte[]> binaries = new LinkedHashMap<>();
    private final Map<String, List<Link>> links = new LinkedHashMap<>();   // by the part that links

    private TestDocx() {
    }

    /** A document whose body holds this XML (paragraphs, tables, a w:sectPr…). */
    static TestDocx body(String bodyXml) {
        TestDocx docx = new TestDocx();
        docx.parts.put("word/document.xml", part("w:document", "<w:body>" + bodyXml + "</w:body>"));
        docx.link("", "rIdMain", "officeDocument", "word/document.xml", false);
        return docx;
    }

    /** A part's XML: the declaration, the root with Word's namespaces, the content. */
    static String part(String root, String content) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<" + root + " " + NAMESPACES + ">"
                + content + "</" + root + ">";
    }

    /** A part of word/, linked from the main document by this id and this type (the end of its URI: "styles"…). */
    TestDocx wordPart(String id, String type, String name, String root, String content) {
        parts.put("word/" + name, part(root, content));
        return link("word/document.xml", id, type, name, false);
    }

    /** A part as is, linked from nowhere unless link() says so. */
    TestDocx raw(String name, String xml) {
        parts.put(name, xml);
        return this;
    }

    /** A binary part as is (a picture), linked from nowhere unless link() says so. */
    TestDocx binary(String name, byte[] bytes) {
        binaries.put(name, bytes);
        return this;
    }

    /** A relationship of a part ("" for the package's own); an external target is written as it is given. */
    TestDocx link(String fromPart, String id, String type, String target, boolean external) {
        links.computeIfAbsent(fromPart, part -> new ArrayList<>()).add(new Link(id, type, target, external));
        return this;
    }

    /** Without the relationships of this part, for a test that writes them itself, or wants none. */
    TestDocx withoutLinks(String fromPart) {
        links.remove(fromPart);
        return this;
    }

    Path write(Path directory) throws IOException {
        Path file = Files.createTempFile(directory, "test", ".docx");
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
            put(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/></Types>");
            for (Map.Entry<String, List<Link>> partLinks : links.entrySet()) {
                put(zip, relationshipsPart(partLinks.getKey()), relationships(partLinks.getValue()));
            }
            for (Map.Entry<String, String> part : parts.entrySet()) {
                put(zip, part.getKey(), part.getValue());
            }
            for (Map.Entry<String, byte[]> binary : binaries.entrySet()) {
                zip.putNextEntry(new ZipEntry(binary.getKey()));
                zip.write(binary.getValue());
                zip.closeEntry();
            }
        }
        return file;
    }

    private static String relationships(List<Link> links) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (Link link : links) {
            String type = link.type().contains("://") ? link.type() : RELATIONSHIPS + link.type();
            xml.append("<Relationship Id=\"").append(link.id()).append("\" Type=\"").append(type)
                    .append("\" Target=\"").append(link.target().replace("&", "&amp;")).append('"')
                    .append(link.external() ? " TargetMode=\"External\"" : "").append("/>");
        }
        return xml.append("</Relationships>").toString();
    }

    private static String relationshipsPart(String part) {
        int slash = part.lastIndexOf('/');
        return part.substring(0, slash + 1) + "_rels/" + part.substring(slash + 1) + ".rels";
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
