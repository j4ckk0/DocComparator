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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A place in two sequences laid side by side — paragraphs, rows or columns of a table: the element of each side there
 * (its index, -1 for none). Elements paired in order share a slot; one alone has a slot of its own, after those of its
 * side before it, the left side's first; one moved too, its counterpart naming the slot of its match.
 */
public record Slot(int left, int right, int counterpart) {

    public boolean moved() {
        return counterpart >= 0;
    }

    /** The slots of two sequences of these lengths, from their pairs (Pairer). */
    static List<Slot> align(int leftLength, int rightLength, List<Pairer.Match> matches) {
        Map<Integer, Pairer.Match> movedFromLeft = new HashMap<>();
        Map<Integer, Pairer.Match> movedFromRight = new HashMap<>();
        List<Pairer.Match> inOrder = new ArrayList<>();
        for (Pairer.Match match : matches) {
            if (match.moved()) {
                movedFromLeft.put(match.left(), match);
                movedFromRight.put(match.right(), match);
            } else {
                inOrder.add(match);
            }
        }
        inOrder.add(new Pairer.Match(leftLength, rightLength, 0, false));   // past the end: the rest of both sides

        List<Slot> slots = new ArrayList<>();
        int[] leftSlot = new int[leftLength];
        int[] rightSlot = new int[rightLength];
        int i = 0;
        int j = 0;
        for (Pairer.Match match : inOrder) {
            for (; i < match.left(); i++) {
                leftSlot[i] = slots.size();
                slots.add(new Slot(i, -1, -1));
            }
            for (; j < match.right(); j++) {
                rightSlot[j] = slots.size();
                slots.add(new Slot(-1, j, -1));
            }
            if (i < leftLength && j < rightLength) {
                slots.add(new Slot(i++, j++, -1));
            }
        }
        for (Pairer.Match moved : movedFromLeft.values()) {   // each slot of a moved pair names the other's
            int l = leftSlot[moved.left()];
            int r = rightSlot[moved.right()];
            slots.set(l, new Slot(moved.left(), -1, r));
            slots.set(r, new Slot(-1, moved.right(), l));
        }
        return slots;
    }

    /** For each element of the left side, its match on the right (-1 for none), moved or not. */
    static int[] leftPartners(List<Slot> slots, int leftLength) {
        int[] partners = new int[leftLength];
        Arrays.fill(partners, -1);
        for (Slot slot : slots) {
            if (slot.left >= 0) {
                partners[slot.left] = slot.right >= 0 ? slot.right
                        : slot.moved() ? slots.get(slot.counterpart).right : -1;
            }
        }
        return partners;
    }

    /** For each element of the right side, its match on the left (-1 for none), moved or not. */
    static int[] rightPartners(List<Slot> slots, int rightLength) {
        int[] partners = new int[rightLength];
        Arrays.fill(partners, -1);
        for (Slot slot : slots) {
            if (slot.right >= 0) {
                partners[slot.right] = slot.left >= 0 ? slot.left
                        : slot.moved() ? slots.get(slot.counterpart).left : -1;
            }
        }
        return partners;
    }
}
