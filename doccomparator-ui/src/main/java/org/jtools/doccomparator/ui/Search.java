package org.jtools.doccomparator.ui;

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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where the lines shown hold the words searched: in the documents' own text only — paragraphs and table cells, on
 * both sides —, not in what the view says of them (the parts' titles, the notes under a paragraph). The places are
 * in the order they are read, line by line, the left side before the right.
 */
final class Search {

    private Search() {
    }

    /**
     * A place found: on that line, in that text, from its first character to the one after its last; paragraph is the
     * side the text is the paragraph of, null for a table's cell (its row's top stands for it).
     */
    record Found(int line, TextBlock block, int start, int end, LineSide paragraph) {

        /** How far below its line's top the line of text holding it is, once laid out; 0 for a cell. */
        int top() {
            if (paragraph == null) {
                return 0;
            }
            int text = LineSide.PADDING + (paragraph.caption == null ? 0 : paragraph.caption.height() + LineSide.GAP);
            return text + (int) block.lineTop(start);
        }
    }

    /** Every place the words are found in these lines; none for blank words. */
    static List<Found> in(List<Line> lines, String words, boolean matchCase) {
        List<Found> found = new ArrayList<>();
        if (words.isBlank()) {
            return found;
        }
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            for (LineSide side : new LineSide[] {line.left, line.right}) {
                if (side == null) {
                    continue;
                }
                if (side.columns == null) {
                    find(found, i, side.text, words, matchCase, side);
                } else {
                    for (LineSide cell : side.columns) {
                        if (cell != null && cell.text != null) {
                            find(found, i, cell.text, words, matchCase, null);
                        }
                    }
                }
            }
        }
        return found;
    }

    /**
     * The places of the words in a text, each after the last: compared character by character, so that the offsets are
     * the text's own (a text made lower case can change its length).
     */
    private static void find(List<Found> found, int line, TextBlock block, String words, boolean matchCase,
                             LineSide paragraph) {
        if (block == null) {
            return;
        }
        String text = block.text();
        for (int at = 0; at + words.length() <= text.length(); at++) {
            if (text.regionMatches(!matchCase, at, words, 0, words.length())) {
                found.add(new Found(line, block, at, at + words.length(), paragraph));
                at += words.length() - 1;
            }
        }
    }

    /**
     * Shows these places in their texts, the one at current as the one gone to (-1 for none); every other text of the
     * lines shows none.
     */
    static void show(List<Line> lines, List<Found> found, int current) {
        Map<TextBlock, List<int[]>> places = new IdentityHashMap<>();
        Map<TextBlock, Integer> currents = new IdentityHashMap<>();
        for (int i = 0; i < found.size(); i++) {
            Found place = found.get(i);
            List<int[]> ofBlock = places.computeIfAbsent(place.block(), _ -> new ArrayList<>());
            if (i == current) {
                currents.put(place.block(), ofBlock.size());
            }
            ofBlock.add(new int[] {place.start(), place.end()});
        }
        for (Line line : lines) {
            for (LineSide side : new LineSide[] {line.left, line.right}) {
                if (side == null) {
                    continue;
                }
                List<TextBlock> blocks = new ArrayList<>();
                blocks.add(side.text);
                if (side.columns != null) {
                    side.columns.forEach(cell -> blocks.add(cell == null ? null : cell.text));
                }
                for (TextBlock block : blocks) {
                    if (block != null) {
                        block.showFound(places.getOrDefault(block, List.of()), currents.getOrDefault(block, -1));
                    }
                }
            }
        }
    }
}
