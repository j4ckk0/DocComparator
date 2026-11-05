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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jtools.doccomparator.pdf.ContentReader.Artifact;
import org.jtools.doccomparator.pdf.ContentReader.Glyph;
import org.jtools.doccomparator.pdf.TextLayout.Page;

/** A page's glyphs as lines: those drawn one after the other on a baseline, a gap between them a space. */
final class PdfLines {

    /** A line of a page: its glyphs, its direction, where it lies, its size, and what kind of line it is. */
    static final class Line {
        final List<Glyph> glyphs = new ArrayList<>();
        double dx;
        double dy;
        double across;
        double start;
        double end;
        double size;
        double top;   // its distance from the page's top, for the running header and footer
        double bottom;
        int page;
        /** Whether it has a gap wider than a word's: cells side by side, or tabbed columns, never a sentence. */
        boolean tabular;

        String text() {
            StringBuilder text = new StringBuilder();
            glyphs.forEach(glyph -> text.append(glyph.text()));
            return text.toString();
        }

        boolean allBold() {
            boolean any = false;
            for (Glyph glyph : glyphs) {
                if (!glyph.text().isBlank()) {
                    if (!bold(glyph)) {
                        return false;
                    }
                    any = true;
                }
            }
            return any;
        }

        Artifact artifact() {
            Artifact found = null;
            for (Glyph glyph : glyphs) {
                if (found == null) {
                    found = glyph.artifact();
                } else if (found != glyph.artifact()) {
                    return Artifact.NONE;
                }
            }
            return found == null ? Artifact.NONE : found;
        }

        boolean horizontal() {
            return dx > 0.99;
        }
    }

    private PdfLines() {
    }

    /** Whether a glyph is drawn bold: by its font, or by hand (stroked as well as filled, or drawn twice). */
    static boolean bold(Glyph glyph) {
        return glyph.font().bold || glyph.drawnBold();
    }

    /** The lines of a page, numbered so (from 0), in the order their glyphs are drawn. */
    static List<Line> of(Page page, int number) {
        List<Line> lines = new ArrayList<>();
        Line line = null;
        double pageTop = page.box()[3];
        double pageBottom = page.box()[1];
        for (Glyph glyph : page.glyphs()) {
            double along = glyph.x() * glyph.dx() + glyph.y() * glyph.dy();
            double alongEnd = glyph.endX() * glyph.dx() + glyph.endY() * glyph.dy();
            double across = -glyph.x() * glyph.dy() + glyph.y() * glyph.dx();
            if (line != null && continues(line, glyph, along, across)) {
                Glyph last = line.glyphs.getLast();
                double gap = along - line.end;
                if (glyph.text().equals(last.text()) && Math.abs(along - lastStart(line)) < 0.15 * glyph.size()
                        && !glyph.text().isBlank()) {
                    // drawn twice, a little apart: a bold its writer made by hand
                    line.glyphs.set(line.glyphs.size() - 1, emboldened(last));
                    continue;
                }
                boolean spaced = last.text().isBlank() || glyph.text().isBlank();
                if (!spaced && (gap > 0.18 * Math.min(glyph.size(), last.size()) || along < lastStart(line)
                        - 0.5 * glyph.size())) {
                    line.glyphs.add(space(last));
                    line.tabular |= gap > 1.5 * Math.max(glyph.size(), last.size());
                }
                line.glyphs.add(glyph);
                line.end = Math.max(line.end, alongEnd);
                line.size = Math.max(line.size, glyph.size());
                continue;
            }
            line = new Line();
            line.dx = glyph.dx();
            line.dy = glyph.dy();
            line.across = across;
            line.start = along;
            line.end = alongEnd;
            line.size = glyph.size();
            line.page = number;
            line.top = pageTop - glyph.y();
            line.bottom = glyph.y() - pageBottom;
            line.glyphs.add(glyph);
            lines.add(line);
        }
        for (Line each : lines) {
            each.size = dominantSize(each);
            trim(each);
        }
        lines.removeIf(each -> each.glyphs.isEmpty());
        return lines;
    }

    private static double lastStart(Line line) {
        Glyph last = line.glyphs.getLast();
        return last.x() * last.dx() + last.y() * last.dy();
    }

    private static boolean continues(Line line, Glyph glyph, double along, double across) {
        if (Math.abs(glyph.dx() - line.dx) > 0.05 || Math.abs(glyph.dy() - line.dy) > 0.05) {
            return false;
        }
        Glyph last = line.glyphs.getLast();
        double lastAcross = -last.x() * last.dy() + last.y() * last.dx();
        double tolerance = 0.5 * Math.max(glyph.size(), last.size());
        return Math.abs(across - lastAcross) <= tolerance && along >= lastStart(line) - 3 * glyph.size()
                || Math.abs(across - line.across) <= tolerance && along >= line.end - 0.5 * glyph.size();
    }

    private static Glyph space(Glyph after) {
        return new Glyph(" ", after.endX(), after.endY(), after.endX(), after.endY(), after.dx(), after.dy(),
                after.size(), after.font(), after.color(), after.drawnBold(), after.slanted(), after.artifact(),
                false);
    }

    private static Glyph emboldened(Glyph glyph) {
        return new Glyph(glyph.text(), glyph.x(), glyph.y(), glyph.endX(), glyph.endY(), glyph.dx(), glyph.dy(),
                glyph.size(), glyph.font(), glyph.color(), true, glyph.slanted(), glyph.artifact(),
                glyph.unreadableFont());
    }

    /** The size most of a line's characters have: its own, a superscript's aside. */
    private static double dominantSize(Line line) {
        Map<Long, Integer> counts = new HashMap<>();
        for (Glyph glyph : line.glyphs) {
            if (!glyph.text().isBlank()) {
                counts.merge(Math.round(glyph.size() * 2), 1, Integer::sum);
            }
        }
        return counts.entrySet().stream().max(Map.Entry.<Long, Integer>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> entry.getKey() / 2.0).orElse(line.size);
    }

    /** Without the spaces it starts and ends with. */
    private static void trim(Line line) {
        while (!line.glyphs.isEmpty() && line.glyphs.getFirst().text().isBlank()) {
            line.glyphs.removeFirst();
        }
        while (!line.glyphs.isEmpty() && line.glyphs.getLast().text().isBlank()) {
            line.glyphs.removeLast();
        }
        if (!line.glyphs.isEmpty()) {
            Glyph first = line.glyphs.getFirst();
            line.start = first.x() * first.dx() + first.y() * first.dy();
        }
    }
}
