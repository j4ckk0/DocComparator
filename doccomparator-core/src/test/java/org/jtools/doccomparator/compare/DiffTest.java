package org.jtools.doccomparator.compare;

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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DiffTest {

    @Test
    void theSameSequenceIsOneEqualStretch() {
        assertEquals(List.of(new Diff.Edit(true, 0, 3, 0, 3)), Diff.of(new int[] {1, 2, 3}, new int[] {1, 2, 3}));
        assertEquals(List.of(), Diff.of(new int[0], new int[0]));
    }

    @Test
    void whatOneSideAloneHasIsAChange() {
        assertEquals(List.of(new Diff.Edit(false, 0, 0, 0, 2)), Diff.of(new int[0], new int[] {1, 2}));
        assertEquals(List.of(new Diff.Edit(true, 0, 1, 0, 1), new Diff.Edit(false, 1, 2, 1, 1),
                new Diff.Edit(true, 2, 3, 1, 2)), Diff.of(new int[] {1, 2, 3}, new int[] {1, 3}));
    }

    @Test
    void theScriptKeepsTheMostInCommon() {
        // Myers' own example: ABCABBA → CBABAC keeps four
        int[] a = {1, 2, 3, 1, 2, 2, 1};
        int[] b = {3, 2, 1, 2, 1, 3};
        assertValid(a, b, Diff.of(a, b), 4);
        assertEquals(4, Diff.commonLength(a, b, 100));
    }

    @Test
    void scriptsAreRightOnRandomSequences() {
        Random random = new Random(42);
        for (int round = 0; round < 500; round++) {
            int[] a = random.ints(random.nextInt(30), 0, 4).toArray();
            int[] b = random.ints(random.nextInt(30), 0, 4).toArray();
            int longest = longestCommonSubsequence(a, b);
            assertValid(a, b, Diff.of(a, b), longest);
            assertEquals(longest, Diff.commonLength(a, b, a.length + b.length));
        }
    }

    @Test
    void commonLengthGivesUpPastTheDifferencesAllowed() {
        int[] a = {1, 2, 3, 4};
        int[] b = {1, 5, 6, 4};   // 2 kept: 4 removed and added
        assertEquals(2, Diff.commonLength(a, b, 4));
        assertEquals(-1, Diff.commonLength(a, b, 3));
        assertEquals(-1, Diff.commonLength(new int[0], new int[] {1, 2}, 1));
    }

    /** The script covers both sequences in order, its equal stretches are equal, and keep this many. */
    private static void assertValid(int[] a, int[] b, List<Diff.Edit> edits, int kept) {
        int x = 0;
        int y = 0;
        int equal = 0;
        List<Integer> rebuilt = new ArrayList<>();
        for (Diff.Edit edit : edits) {
            assertEquals(x, edit.fromA());
            assertEquals(y, edit.fromB());
            if (edit.equal()) {
                assertEquals(edit.toA() - edit.fromA(), edit.toB() - edit.fromB());
                for (int k = 0; k < edit.toA() - edit.fromA(); k++) {
                    assertEquals(a[edit.fromA() + k], b[edit.fromB() + k]);
                }
                equal += edit.toA() - edit.fromA();
            }
            for (int k = edit.fromB(); k < edit.toB(); k++) {
                rebuilt.add(b[k]);
            }
            x = edit.toA();
            y = edit.toB();
        }
        assertEquals(a.length, x);
        assertEquals(b.length, y);
        assertEquals(kept, equal);
        assertArrayEquals(b, rebuilt.stream().mapToInt(Integer::intValue).toArray());
        for (int i = 1; i < edits.size(); i++) {
            assertTrue(edits.get(i).equal() != edits.get(i - 1).equal(), "stretches alternate");
        }
    }

    private static int longestCommonSubsequence(int[] a, int[] b) {
        int[][] lengths = new int[a.length + 1][b.length + 1];
        for (int i = 1; i <= a.length; i++) {
            for (int j = 1; j <= b.length; j++) {
                lengths[i][j] = a[i - 1] == b[j - 1] ? lengths[i - 1][j - 1] + 1
                        : Math.max(lengths[i - 1][j], lengths[i][j - 1]);
            }
        }
        return lengths[a.length][b.length];
    }
}
