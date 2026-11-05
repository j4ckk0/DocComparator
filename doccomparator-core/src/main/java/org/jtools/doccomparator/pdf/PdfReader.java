package org.jtools.doccomparator.pdf;

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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jtools.doccomparator.document.Block;
import org.jtools.doccomparator.document.Story;
import org.jtools.doccomparator.document.StyleSheet;
import org.jtools.doccomparator.document.Template;
import org.jtools.doccomparator.document.Document;
import org.jtools.doccomparator.i18n.I18n;
import org.jtools.doccomparator.pdf.PdfObjects.Stream;
import org.jtools.doccomparator.reader.AbstractDocumentReader;

/**
 * Reads a PDF into a Document, so that it compares as a document does: its text and its look, its paragraphs
 * rebuilt from the lines (TextLayout), its running header and footer apart. Pictures, annotations, form fields and
 * the text of scanned pages (images) are not read; tables are read as their lines.
 */
public final class PdfReader extends AbstractDocumentReader {


    /**
     * The most characters a file is read with, about 500 pages of text: two PDFs of 570 pages each (2.3 million
     * characters) are read and compared in 5 seconds, within 1 GB.
     */
    static final long LARGEST_TEXT = 2_000_000;

    /** The most nodes of a page tree walked: more is a crafted file, not a document. */
    private static final int LARGEST_TREE = 100_000;

    /** The share of the characters a font that does not say them may hold before the file is refused. */
    private static final double MOST_UNREADABLE = 0.2;

    /** The reader of .pdf files, found as a service (DocumentReaders). */
    public PdfReader() {
        super(30, "pdf");
    }

    @Override
    public String formatName() {
        return I18n.text("format.pdf");
    }

    @Override
    public String readingNote() {
        return I18n.text("note.pdf");
    }


    @Override
    protected Document readFile(Path path) throws IOException {
        return read(PdfFile.open(path));
    }

    static Document read(PdfFile file) throws PdfException {
        List<TextLayout.Page> pages = new ArrayList<>();
        Map<Map<String, Object>, PdfFont> fonts = new IdentityHashMap<>();
        long[] glyphsLeft = {LARGEST_TEXT};
        long glyphs = 0;
        long unreadable = 0;
        PdfFont unreadableFont = null;
        for (Page page : pages(file)) {
            byte[] content = content(file, page.dictionary());
            List<ContentReader.Glyph> shown = content == null ? List.of()
                    : ContentReader.read(file, content, page.resources(), fonts, glyphsLeft);
            for (ContentReader.Glyph glyph : shown) {
                glyphs++;
                if (glyph.unreadableFont()) {
                    unreadable++;
                    unreadableFont = unreadableFont == null ? glyph.font() : unreadableFont;
                }
            }
            pages.add(new TextLayout.Page(shown, page.box()));
        }
        if (unreadableFont != null && unreadable > MOST_UNREADABLE * glyphs) {
            throw new PdfException(I18n.text("error.pdfFont", unreadableFont.name));
        }
        if (glyphs == 0) {
            throw new PdfException(I18n.text("error.pdfNoText"));
        }
        TextLayout.Text text = TextLayout.of(pages);
        List<Story> running = new ArrayList<>();
        if (!text.header().isEmpty()) {
            running.add(new Story(Story.Name.headerOrFooter(Story.Kind.HEADER, "default", 0), text.header()));
        }
        if (!text.footer().isEmpty()) {
            running.add(new Story(Story.Name.headerOrFooter(Story.Kind.FOOTER, "default", 0), text.footer()));
        }
        List<Block> body = text.body();
        return new Document(file.fileName(), new Story(Story.Name.BODY, body), running, List.of(),
                StyleSheet.NONE, Template.NONE, false);
    }

    /** A page: its dictionary, its resources and box, inherited from the tree above it where it has none. */
    private record Page(Map<String, Object> dictionary, Map<String, Object> resources, double[] box) {
    }

    private static List<Page> pages(PdfFile file) throws PdfException {
        Map<String, Object> catalog = file.catalog();
        List<Page> pages = new ArrayList<>();
        Set<Map<String, Object>> walked = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<String, Object> root = PdfObjects.dictionary(file.resolve(catalog.get("Pages")));
        if (root == null) {
            throw new PdfException(I18n.text("error.pdfDamaged"));
        }
        walk(file, root, null, new double[] {0, 0, 612, 792}, pages, walked);
        return pages;
    }

    private static void walk(PdfFile file, Map<String, Object> node, Map<String, Object> resources, double[] box,
                             List<Page> pages, Set<Map<String, Object>> walked) {
        if (!walked.add(node) || walked.size() > LARGEST_TREE) {
            return;   // a node met again: the tree loops
        }
        Map<String, Object> own = PdfObjects.dictionary(file.resolve(node.get("Resources")));
        Map<String, Object> inherited = own != null ? own : resources;
        double[] ownBox = box(file, node.get("CropBox"));
        if (ownBox == null) {
            ownBox = box(file, node.get("MediaBox"));
        }
        double[] pageBox = ownBox != null ? ownBox : box;
        List<?> kids = PdfObjects.array(file.resolve(node.get("Kids")));
        String type = PdfObjects.name(file.resolve(node.get("Type")));
        if (kids != null && !"Page".equals(type)) {
            for (Object kid : kids) {
                Map<String, Object> child = PdfObjects.dictionary(file.resolve(kid));
                if (child != null) {
                    walk(file, child, inherited, pageBox, pages, walked);
                }
            }
        } else {
            pages.add(new Page(node, inherited == null ? Map.of() : inherited, pageBox));
        }
    }

    private static double[] box(PdfFile file, Object value) {
        List<?> array = PdfObjects.array(file.resolve(value));
        if (array == null || array.size() < 4) {
            return null;
        }
        double[] box = new double[4];
        for (int i = 0; i < 4; i++) {
            box[i] = PdfObjects.number(file.resolve(array.get(i)), 0);
        }
        return new double[] {Math.min(box[0], box[2]), Math.min(box[1], box[3]), Math.max(box[0], box[2]),
            Math.max(box[1], box[3])};
    }

    /** A page's content streams, end to end, a line between each: one's last token kept apart from the next's. */
    private static byte[] content(PdfFile file, Map<String, Object> page) throws PdfException {
        Object contents = file.resolve(page.get("Contents"));
        List<?> parts = contents instanceof List<?> list ? list : contents == null ? List.of() : List.of(contents);
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        for (Object part : parts) {
            if (file.resolve(part) instanceof Stream stream) {
                byte[] bytes = file.decoded(stream);
                if (bytes != null) {
                    all.write(bytes, 0, bytes.length);
                    all.write('\n');
                }
            }
        }
        return all.size() == 0 ? null : all.toByteArray();
    }
}
