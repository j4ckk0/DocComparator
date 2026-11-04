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
package org.jtools.doccomparator.compare;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The differences between two sequences of codes (words, characters…), by Myers' algorithm: the fewest elements to
 * remove and add, so the most kept in order. The same algorithm tells how much two sequences have in common, stopping
 * as soon as they differ too much for it to matter.
 */
final class Diff {

    /**
     * A stretch of the edit script: a[fromA, toA) and b[fromB, toB) are the same (equal) or what replaces one with the
     * other (one side may be empty: something removed or added).
     */
    record Edit(boolean equal, int fromA, int toA, int fromB, int toB) {
    }

    /**
     * The most differences the full script is worked out for: past them, the middle of the sequences counts as one
     * change. The work grows with their square; two paragraphs that far apart have nothing worth showing in common.
     */
    static final int MOST_DIFFERENCES = 4000;

    private Diff() {
    }

    /** The edit script from a to b, in order, its stretches alternating between equal and changed. */
    static List<Edit> of(int[] a, int[] b) {
        int n = a.length;
        int m = b.length;
        int prefix = commonPrefix(a, b);
        int suffix = commonSuffix(a, b, prefix);
        List<Edit> edits = new ArrayList<>();
        add(edits, true, 0, prefix, 0, prefix);
        middle(a, prefix, n - suffix, b, prefix, m - suffix, edits);
        add(edits, true, n - suffix, n, m - suffix, m);
        return edits;
    }

    /**
     * How many elements a and b have in common, in order (their longest common subsequence); -1 when more than
     * mostDifferences elements would have to be removed and added to turn one into the other.
     */
    static int commonLength(int[] a, int[] b, int mostDifferences) {
        int prefix = commonPrefix(a, b);
        int suffix = commonSuffix(a, b, prefix);
        int n = a.length - prefix - suffix;
        int m = b.length - prefix - suffix;
        if (n == 0 || m == 0) {
            return n + m <= mostDifferences ? prefix + suffix : -1;
        }
        int max = Math.min(n + m, mostDifferences);
        int offset = max + 1;
        int[] v = new int[2 * max + 3];
        for (int d = 0; d <= max; d++) {
            for (int k = -d; k <= d; k += 2) {
                int x = k == -d || (k != d && v[offset + k - 1] < v[offset + k + 1]) ? v[offset + k + 1]
                        : v[offset + k - 1] + 1;
                int y = x - k;
                while (x < n && y < m && a[prefix + x] == b[prefix + y]) {
                    x++;
                    y++;
                }
                v[offset + k] = x;
                if (x >= n && y >= m) {
                    return prefix + suffix + (n + m - d) / 2;
                }
            }
        }
        return -1;
    }

    private static int commonPrefix(int[] a, int[] b) {
        int prefix = 0;
        while (prefix < a.length && prefix < b.length && a[prefix] == b[prefix]) {
            prefix++;
        }
        return prefix;
    }

    private static int commonSuffix(int[] a, int[] b, int prefix) {
        int suffix = 0;
        while (suffix < a.length - prefix && suffix < b.length - prefix
                && a[a.length - 1 - suffix] == b[b.length - 1 - suffix]) {
            suffix++;
        }
        return suffix;
    }

    /** The script of a[aStart, aEnd) to b[bStart, bEnd), which share neither their first nor their last element. */
    private static void middle(int[] a, int aStart, int aEnd, int[] b, int bStart, int bEnd, List<Edit> edits) {
        int n = aEnd - aStart;
        int m = bEnd - bStart;
        if (n == 0 || m == 0) {
            add(edits, false, aStart, aEnd, bStart, bEnd);
            return;
        }
        int max = Math.min(n + m, MOST_DIFFERENCES);
        int offset = max + 1;
        int[] v = new int[2 * max + 3];
        List<int[]> trace = new ArrayList<>();   // v after each d, for k from -d to d
        for (int d = 0; d <= max; d++) {
            for (int k = -d; k <= d; k += 2) {
                int x = k == -d || (k != d && v[offset + k - 1] < v[offset + k + 1]) ? v[offset + k + 1]
                        : v[offset + k - 1] + 1;
                int y = x - k;
                while (x < n && y < m && a[aStart + x] == b[bStart + y]) {
                    x++;
                    y++;
                }
                v[offset + k] = x;
                if (x >= n && y >= m) {
                    trace.add(Arrays.copyOfRange(v, offset - d, offset + d + 1));
                    backtrack(trace, n, m, aStart, bStart, edits);
                    return;
                }
            }
            trace.add(Arrays.copyOfRange(v, offset - d, offset + d + 1));
        }
        add(edits, false, aStart, aEnd, bStart, bEnd);   // too far apart to be worth the work
    }

    /** From the end back to the start, the path Myers' search found: then its stretches, in order. */
    private static void backtrack(List<int[]> trace, int n, int m, int aStart, int bStart, List<Edit> edits) {
        List<int[]> steps = new ArrayList<>();   // {x, y, kind}: 0 equal, 1 removed from a, 2 added from b; reversed
        int x = n;
        int y = m;
        for (int d = trace.size() - 1; d > 0; d--) {
            int[] previous = trace.get(d - 1);   // k from -(d-1) to d-1
            int k = x - y;
            boolean down = k == -d || (k != d && previous[k - 1 + d - 1] < previous[k + 1 + d - 1]);
            int previousK = down ? k + 1 : k - 1;
            int previousX = previous[previousK + d - 1];
            int previousY = previousX - previousK;
            int startX = down ? previousX : previousX + 1;
            int startY = down ? previousY + 1 : previousY;
            while (x > startX && y > startY) {
                steps.add(new int[] {x - 1, y - 1, 0});
                x--;
                y--;
            }
            steps.add(down ? new int[] {previousX, previousY, 2} : new int[] {previousX, previousY, 1});
            x = previousX;
            y = previousY;
        }
        while (x > 0 && y > 0) {
            steps.add(new int[] {x - 1, y - 1, 0});
            x--;
            y--;
        }
        for (int i = steps.size() - 1; i >= 0; i--) {
            int[] step = steps.get(i);
            int ax = aStart + step[0];
            int by = bStart + step[1];
            switch (step[2]) {
                case 0 -> add(edits, true, ax, ax + 1, by, by + 1);
                case 1 -> add(edits, false, ax, ax + 1, by, by);
                default -> add(edits, false, ax, ax, by, by + 1);
            }
        }
    }

    /** Adds a stretch, joined to the last one when both are equal or both changed. */
    private static void add(List<Edit> edits, boolean equal, int fromA, int toA, int fromB, int toB) {
        if (fromA == toA && fromB == toB) {
            return;
        }
        if (!edits.isEmpty()) {
            Edit last = edits.getLast();
            if (last.equal() == equal && last.toA() == fromA && last.toB() == fromB) {
                edits.set(edits.size() - 1, new Edit(equal, last.fromA(), toA, last.fromB(), toB));
                return;
            }
        }
        edits.add(new Edit(equal, fromA, toA, fromB, toB));
    }
}
