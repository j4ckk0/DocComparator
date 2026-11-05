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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jtools.doccomparator.document.Block;
import org.jtools.doccomparator.pdf.ContentReader.Glyph;
import org.jtools.doccomparator.pdf.PdfLines.Line;

/**
 * Rebuilds a document's words, lines, paragraphs and lists from the glyphs on its pages, which a PDF holds without
 * any, by where they are drawn (PdfLines, PdfParagraphs); its running header and footer are set apart from its body
 * (RunningLines).
 */
final class TextLayout {

    /** A page: its glyphs in the order drawn, and its box (lower left x, y, upper right x, y). */
    record Page(List<Glyph> glyphs, double[] box) {
    }

    /** What the layout gives: the body's paragraphs, and those of the running header and footer. */
    record Text(List<Block> body, List<Block> header, List<Block> footer) {
    }

    private TextLayout() {
    }

    static Text of(List<Page> pages) {
        List<List<Line>> lines = new ArrayList<>();
        for (int p = 0; p < pages.size(); p++) {
            lines.add(PdfLines.of(pages.get(p), p));
        }
        List<Line> header = new ArrayList<>();
        List<Line> footer = new ArrayList<>();
        RunningLines.takeOut(lines, header, footer);
        Map<Long, Double> pitches = PdfParagraphs.pitches(lines);
        Set<String> words = PdfParagraphs.words(lines);
        List<Block> body = PdfParagraphs.of(lines, pitches, words, true);
        return new Text(body, PdfParagraphs.of(List.of(header), pitches, words, false),
                PdfParagraphs.of(List.of(footer), pitches, words, false));
    }
}
