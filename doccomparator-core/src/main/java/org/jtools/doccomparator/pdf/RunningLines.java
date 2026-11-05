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
import org.jtools.doccomparator.pdf.PdfLines.Line;

/** A document's running header and footer: the lines its pages repeat at their top and bottom, set apart. */
final class RunningLines {

    /** The share of a page, at its top and at its bottom, where its running header and footer are looked for. */
    private static final double MARGIN = 0.15;

    private RunningLines() {
    }

    /**
     * Takes out of the pages their running header and footer: the lines a tagged PDF marks as pagination, and those
     * repeated, digits aside, near the same place at the top or the bottom of at least half the pages (two at the
     * least). Each is kept once, from the first page it is on.
     */
    static void takeOut(List<List<Line>> pages, List<Line> header, List<Line> footer) {
        Map<String, Integer> seen = new HashMap<>();
        if (pages.size() >= 2) {
            for (List<Line> page : pages) {
                page.stream().filter(RunningLines::inMargin).map(RunningLines::key).distinct()
                        .forEach(key -> seen.merge(key, 1, Integer::sum));
            }
        }
        int needed = Math.max(2, (pages.size() + 1) / 2);
        for (List<Line> page : pages) {
            List<Line> top = new ArrayList<>();
            List<Line> bottom = new ArrayList<>();
            page.removeIf(line -> {
                Artifact artifact = line.artifact();
                boolean repeated = inMargin(line) && seen.getOrDefault(key(line), 0) >= needed;
                if (artifact == Artifact.NONE && !repeated) {
                    return false;
                }
                boolean atTop = artifact == Artifact.HEADER || artifact != Artifact.FOOTER && line.top < line.bottom;
                (atTop ? top : bottom).add(line);
                return true;
            });
            if (header.isEmpty()) {
                header.addAll(top);
            }
            if (footer.isEmpty()) {
                footer.addAll(bottom);
            }
        }
    }

    private static boolean inMargin(Line line) {
        double height = line.top + line.bottom;
        return line.horizontal() && height > 0 && (line.top < MARGIN * height || line.bottom < MARGIN * height);
    }

    /** A line as its repetitions are known: its words, digits aside (page numbers), and where it is, to 4 points. */
    private static String key(Line line) {
        boolean top = line.top < line.bottom;
        long place = Math.round((top ? line.top : line.bottom) / 4);
        return place + ":" + line.text().replaceAll("[0-9]+", "#").replaceAll("\\s+", " ").strip();
    }
}
