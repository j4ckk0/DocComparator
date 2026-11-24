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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The documents last opened on each side, the latest first, each once: what a side's head and the File menu offer to
 * open again. Only the paths: whether a file is still there is seen when it is chosen, not before — a folder on a
 * network gone away would hold the window up.
 */
final class RecentFiles {

    /** How many each side keeps. */
    static final int KEPT = 10;

    private final Map<Side, List<Path>> files = new EnumMap<>(Map.of(Side.LEFT, List.of(), Side.RIGHT, List.of()));

    /** The side's documents, the latest first. */
    List<Path> of(Side side) {
        return files.get(side);
    }

    /** Puts these in the side's list, as they are kept (the program's settings), the first KEPT of them, each once. */
    void set(Side side, List<Path> kept) {
        List<Path> list = new ArrayList<>();
        for (Path path : kept) {
            Path whole = whole(path);
            if (!list.contains(whole) && list.size() < KEPT) {
                list.add(whole);
            }
        }
        files.put(side, List.copyOf(list));
    }

    /**
     * A document opened on that side: first in its list, out of its older place, the oldest dropped beyond KEPT.
     * Whether the list changed: not when it was first already, as at each comparison of the same documents.
     */
    boolean opened(Side side, Path path) {
        Path whole = whole(path);
        List<Path> list = new ArrayList<>(files.get(side));
        if (!list.isEmpty() && list.getFirst().equals(whole)) {
            return false;
        }
        list.remove(whole);
        list.addFirst(whole);
        files.put(side, List.copyOf(list.subList(0, Math.min(KEPT, list.size()))));
        return true;
    }

    /** A document gone from where it was: out of the side's list. Whether it was in it. */
    boolean gone(Side side, Path path) {
        List<Path> list = new ArrayList<>(files.get(side));
        boolean was = list.remove(whole(path));
        files.put(side, List.copyOf(list));
        return was;
    }

    /** The path as it is listed: whole, from the root, so that one file opened two ways is listed once. */
    private static Path whole(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
