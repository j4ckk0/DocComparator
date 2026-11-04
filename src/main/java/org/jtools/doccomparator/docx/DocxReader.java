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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jtools.doccomparator.document.Block;
import org.jtools.doccomparator.document.Story;
import org.jtools.doccomparator.document.StyleSheet;
import org.jtools.doccomparator.document.Template;
import org.jtools.doccomparator.document.WordDocument;
import org.jtools.doccomparator.i18n.I18n;

/**
 * Reads a Word document (.docx, and .docm, .dotx, .dotm, which are built alike) into a WordDocument. Reads the file
 * and nothing else: the links it holds — a template on a share, a linked picture — are kept as written, never
 * followed (README, "No network").
 */
public final class DocxReader {

    private DocxReader() {
    }

    public static WordDocument read(Path file) throws IOException {
        try (DocxPackage docx = DocxPackage.open(file)) {
            String main = docx.mainDocument();
            List<Relationship> relationships = docx.relationships(main);
            StyleSheet styles = readStyles(docx, relationships);
            StoryReader stories = new StoryReader(styles, readNumbering(docx, relationships));

            List<Block> body = List.of();
            stories.pictures(new Pictures(docx, relationships));
            try (XmlCursor xml = docx.open(main)) {
                xml.root("document");
                while (xml.nextChild()) {
                    if (xml.isWord("body")) {
                        body = stories.readBlocks(xml);
                    } else {
                        xml.skip();   // w:background
                    }
                }
            }
            List<Story> headersAndFooters = readHeadersAndFooters(docx, relationships, stories);
            List<Story> notes = new ArrayList<>();
            notes.addAll(readNotes(docx, relationships, "footnotes", Story.Kind.FOOTNOTE, "story.footnote", stories));
            notes.addAll(readNotes(docx, relationships, "endnotes", Story.Kind.ENDNOTE, "story.endnote", stories));

            return new WordDocument(String.valueOf(file.getFileName()), new Story(Story.Kind.BODY, I18n.text("story.body"), body),
                    headersAndFooters, notes, styles, readTemplate(docx, relationships), stories.trackedChanges());
        }
    }

    private static StyleSheet readStyles(DocxPackage docx, List<Relationship> relationships) throws DocxException {
        Optional<String> part = DocxPackage.target(relationships, "styles");
        if (part.isEmpty() || !docx.has(part.get())) {
            return StyleSheet.NONE;
        }
        try (XmlCursor xml = docx.open(part.get())) {
            return StylesReader.read(xml);
        }
    }

    private static Numbering readNumbering(DocxPackage docx, List<Relationship> relationships) throws DocxException {
        Optional<String> part = DocxPackage.target(relationships, "numbering");
        if (part.isEmpty() || !docx.has(part.get())) {
            return Numbering.NONE;
        }
        try (XmlCursor xml = docx.open(part.get())) {
            return Numbering.read(xml);
        }
    }

    /**
     * The headers and footers the sections refer to, in the order the body does, each part once: a section that
     * refers to none keeps those of the section before, as in Word.
     */
    private static List<Story> readHeadersAndFooters(DocxPackage docx, List<Relationship> relationships,
                                                     StoryReader stories) throws DocxException {
        boolean severalSections = stories.sections() > 1;
        Set<String> read = new HashSet<>();
        List<Story> headersAndFooters = new ArrayList<>();
        for (StoryReader.SectionReference reference : stories.sectionReferences()) {
            Optional<String> part = relationships.stream()
                    .filter(r -> r.id().equals(reference.relationshipId()) && r.isPart())
                    .map(Relationship::target).findFirst();
            if (part.isEmpty() || !docx.has(part.get()) || !read.add(part.get())) {
                continue;
            }
            boolean header = reference.kind() == Story.Kind.HEADER;
            String label = I18n.text(header ? "story.header" : "story.footer");
            label = switch (reference.type()) {
                case "first" -> I18n.text("story.firstPage", label);
                case "even" -> I18n.text("story.evenPages", label);
                default -> label;
            };
            if (severalSections) {
                label = I18n.text("story.section", label, reference.section());
            }
            stories.pictures(new Pictures(docx, docx.relationships(part.get())));   // a header's logo: its own
            try (XmlCursor xml = docx.open(part.get())) {
                xml.root(header ? "hdr" : "ftr");
                headersAndFooters.add(new Story(reference.kind(), label, stories.readBlocks(xml)));
            }
        }
        return headersAndFooters;
    }

    private static List<Story> readNotes(DocxPackage docx, List<Relationship> relationships, String kind,
                                         Story.Kind storyKind, String label, StoryReader stories) throws DocxException {
        Optional<String> part = DocxPackage.target(relationships, kind);
        if (part.isEmpty() || !docx.has(part.get())) {
            return List.of();
        }
        stories.pictures(new Pictures(docx, docx.relationships(part.get())));
        try (XmlCursor xml = docx.open(part.get())) {
            xml.root(kind);
            return stories.readNotes(xml, storyKind, label);
        }
    }

    /**
     * The template the settings attach (w:attachedTemplate, whose target is the link as written) and the one the
     * document's properties name (docProps/app.xml).
     */
    private static Template readTemplate(DocxPackage docx, List<Relationship> relationships) throws DocxException {
        String attached = null;
        Optional<String> settings = DocxPackage.target(relationships, "settings");
        if (settings.isPresent() && docx.has(settings.get())) {
            String id = null;
            try (XmlCursor xml = docx.open(settings.get())) {
                xml.root("settings");
                while (xml.nextChild()) {
                    if (xml.isWord("attachedTemplate")) {
                        id = xml.relationshipAttribute("id");
                    }
                    xml.skip();
                }
            }
            if (id != null) {
                String templateId = id;
                attached = docx.relationships(settings.get()).stream()
                        .filter(r -> r.id().equals(templateId)).map(Relationship::target).findFirst().orElse(null);
            }
        }

        String named = null;
        Optional<String> properties = DocxPackage.target(docx.relationships(""), "extended-properties", "extendedProperties");
        if (properties.isPresent() && docx.has(properties.get())) {
            try (XmlCursor xml = docx.open(properties.get())) {
                xml.root("Properties");
                while (xml.nextChild()) {
                    if (xml.localName().equals("Template")) {
                        String text = xml.text().trim();
                        named = text.isEmpty() ? null : text;   // <Template/>: none, as LibreOffice writes it
                    } else {
                        xml.skip();
                    }
                }
            }
        }
        return attached == null && named == null ? Template.NONE : new Template(attached, named);
    }
}
