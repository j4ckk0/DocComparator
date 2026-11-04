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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Step one of a comparison: which paragraph of one side goes with which of the other. Two paragraphs go together when
 * their text is the same, or close enough: "similarity", the share of their words they keep in order, from 0 to 1.
 *
 * <p>The pairs that keep the order of both documents come first: of all such sequences, the one that pairs the most
 * text, which favours a long paragraph over a short one, and the same text over a close one. Paragraphs left over may
 * still have their match elsewhere, out of order: moved, and shown as such. A moved pair needs more evidence than one
 * in order, so that short common texts ("Yes.", a "1" in a table) are not linked across the document by chance. Last,
 * between two pairs in order, paragraphs left over on both sides are paired with less evidence than at first: in place,
 * a paragraph rewritten in good part is still the one that was there.
 */
final class Pairer {

    /** The least similarity of two paragraphs paired in order. */
    static final double SIMILAR = 0.5;
    /**
     * The least similarity of two paragraphs paired in place: left over between the same two pairs, each in a gap of
     * the other side, where a paragraph rewritten in good part is still the one that was there.
     */
    static final double SIMILAR_IN_PLACE = 0.4;
    /** The least similarity of two paragraphs paired out of order, unless their text is the same… */
    static final double SIMILAR_WHEN_MOVED = 0.6;
    /** …and the words each must have. */
    static final int WORDS_WHEN_MOVED = 3;
    /** The candidates kept for each paragraph: the most text paired, then the nearest place. */
    static final int CANDIDATES = 32;
    /**
     * What a pair weighs more when both paragraphs are in the same place of the structure (the same cell of a table…):
     * between pairings alike otherwise, the one that keeps it ("1" in a row of a table rather than in the next).
     */
    static final double SAME_PLACE = 0.01;

    /** A pair: the index of each paragraph in its side, how similar they are, whether they are out of order. */
    record Match(int left, int right, double similarity, boolean moved) {
    }

    private record Candidate(int left, int right, double similarity, double weight) {
    }

    private Pairer() {
    }

    /** The pairs of these two sides: those in order by increasing indexes, then those moved. */
    static List<Match> pair(List<Unit> left, List<Unit> right) {
        int nl = left.size();
        int nr = right.size();
        int prefix = 0;
        while (prefix < nl && prefix < nr && sameInSamePlace(left.get(prefix), right.get(prefix))) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < nl - prefix && suffix < nr - prefix
                && sameInSamePlace(left.get(nl - 1 - suffix), right.get(nr - 1 - suffix))) {
            suffix++;
        }

        List<Match> inOrder = new ArrayList<>();
        for (int i = 0; i < prefix; i++) {
            inOrder.add(new Match(i, i, 1, false));
        }
        List<Candidate> candidates = candidates(left, prefix, nl - suffix, right, prefix, nr - suffix);
        boolean[] leftPaired = new boolean[nl];
        boolean[] rightPaired = new boolean[nr];
        for (Candidate candidate : longestChain(candidates, nr)) {
            inOrder.add(new Match(candidate.left(), candidate.right(), candidate.similarity(), false));
            leftPaired[candidate.left()] = true;
            rightPaired[candidate.right()] = true;
        }
        for (int i = 0; i < suffix; i++) {
            inOrder.add(new Match(nl - suffix + i, nr - suffix + i, 1, false));
        }

        Map<String, Integer> leftKeys = countKeys(left);
        Map<String, Integer> rightKeys = countKeys(right);
        List<Match> moved = new ArrayList<>();
        candidates.sort(Comparator.comparingDouble(Candidate::weight).reversed()
                .thenComparingInt(Candidate::left).thenComparingInt(Candidate::right));
        for (Candidate candidate : candidates) {
            if (leftPaired[candidate.left()] || rightPaired[candidate.right()]) {
                continue;
            }
            Unit l = left.get(candidate.left());
            Unit r = right.get(candidate.right());
            // the same text, found once on each side, with something to tell it by: words, or a picture's image
            boolean sameText = l.key().equals(r.key()) && (l.words().length > 0 || !l.pictures().isEmpty())
                    && leftKeys.get(l.key()) == 1 && rightKeys.get(r.key()) == 1;
            boolean close = candidate.similarity() >= SIMILAR_WHEN_MOVED
                    && Math.min(l.words().length, r.words().length) >= WORDS_WHEN_MOVED;
            if (sameText || close) {
                moved.add(new Match(candidate.left(), candidate.right(), candidate.similarity(), true));
                leftPaired[candidate.left()] = true;
                rightPaired[candidate.right()] = true;
            }
        }
        moved.sort(Comparator.comparingInt(Match::left));

        inOrder.sort(Comparator.comparingInt(Match::left));
        List<Match> inPlace = new ArrayList<>();
        int previousLeft = -1;
        int previousRight = -1;
        for (Match next : withEnd(inOrder, nl, nr)) {
            List<Candidate> gap = new ArrayList<>();
            for (int i = previousLeft + 1; i < next.left(); i++) {
                for (int j = previousRight + 1; j < next.right(); j++) {
                    if (!leftPaired[i] && !rightPaired[j]) {
                        double similarity = similarity(left.get(i), right.get(j), SIMILAR_IN_PLACE);
                        if (similarity > 0) {
                            gap.add(candidate(left, i, right, j, similarity));
                        }
                    }
                }
            }
            for (Candidate candidate : longestChain(gap, nr)) {
                inPlace.add(new Match(candidate.left(), candidate.right(), candidate.similarity(), false));
            }
            previousLeft = next.left();
            previousRight = next.right();
        }
        inOrder.addAll(inPlace);
        inOrder.sort(Comparator.comparingInt(Match::left));
        List<Match> matches = new ArrayList<>(inOrder);
        matches.addAll(moved);
        return matches;
    }

    private static List<Match> withEnd(List<Match> inOrder, int nl, int nr) {
        List<Match> all = new ArrayList<>(inOrder);
        all.add(new Match(nl, nr, 0, false));
        return all;
    }

    private static Candidate candidate(List<Unit> left, int i, List<Unit> right, int j, double similarity) {
        Unit a = left.get(i);
        Unit b = right.get(j);
        return new Candidate(i, j, similarity,
                similarity * (a.size() + b.size()) / 2 + (samePlace(a, b) ? SAME_PLACE : 0));
    }

    /** In the same place: the same story, the same text box, table, row or column, each by its number. */
    private static boolean samePlace(Unit a, Unit b) {
        return a.item().places().equals(b.item().places()) && a.item().story().equals(b.item().story());
    }

    /** The same text in the same place: paired at once at the start and the end, where the documents are alike. */
    private static boolean sameInSamePlace(Unit a, Unit b) {
        return a.key().equals(b.key()) && samePlace(a, b);
    }

    /** How similar two paragraphs are, from 0 to 1: 1 for the same text, 0 below SIMILAR. */
    static double similarity(Unit a, Unit b) {
        return similarity(a, b, SIMILAR);
    }

    /** How similar two paragraphs are, from 0 to 1: 1 for the same text, 0 below least. */
    static double similarity(Unit a, Unit b, double least) {
        if (a.isTable() != b.isTable()) {
            return 0;   // a table goes with a table only
        }
        if (a.key().equals(b.key())) {
            return 1;
        }
        int n = a.words().length;
        int m = b.words().length;
        if (n == 0 && m == 0) {
            // no words: the same text, but for its pictures (a figure redrawn), is the least that goes together
            return a.text().equals(b.text()) && !a.text().isBlank() ? least : 0;
        }
        double enough = least * (n + m) - 1e-9;   // the words to keep in common, twice
        if (n == 0 || m == 0 || 2.0 * Math.min(n, m) < enough) {
            return 0;   // too different in length to keep enough in common
        }
        if (2.0 * common(a.bag(), b.bag()) < enough) {
            return 0;   // not enough words in common, in any order
        }
        int common = Diff.commonLength(a.words(), b.words(), (int) Math.floor((1 - least) * (n + m) + 1e-9));
        return common < 0 || 2.0 * common < enough ? 0 : 2.0 * common / (n + m);
    }

    /** What two sorted bags of words have in common. */
    private static int common(int[] a, int[] b) {
        int common = 0;
        for (int i = 0, j = 0; i < a.length && j < b.length; ) {
            if (a[i] == b[j]) {
                common++;
                i++;
                j++;
            } else if (a[i] < b[j]) {
                i++;
            } else {
                j++;
            }
        }
        return common;
    }

    /** The pairs worth considering between left[l0, l1) and right[r0, r1). */
    private static List<Candidate> candidates(List<Unit> left, int l0, int l1, List<Unit> right, int r0, int r1) {
        Map<String, List<Integer>> rightByKey = new HashMap<>();
        for (int j = r0; j < r1; j++) {
            rightByKey.computeIfAbsent(right.get(j).key(), _ -> new ArrayList<>()).add(j);
        }
        Map<String, Integer> leftKeys = countKeys(left.subList(l0, l1));
        // A text found once on each side has its match: no need to look further for it, on either side.
        boolean[] rightSettled = new boolean[r1 - r0];
        for (int j = r0; j < r1; j++) {
            String key = right.get(j).key();
            rightSettled[j - r0] = leftKeys.getOrDefault(key, 0) == 1 && rightByKey.get(key).size() == 1;
        }
        double leftLength = Math.max(1, l1 - l0);
        double rightLength = Math.max(1, r1 - r0);
        List<Candidate> candidates = new ArrayList<>();
        for (int i = l0; i < l1; i++) {
            Unit a = left.get(i);
            List<Candidate> own = new ArrayList<>();
            List<Integer> sameText = rightByKey.getOrDefault(a.key(), List.of());
            for (int j : sameText) {
                own.add(candidate(left, i, right, j, 1));
            }
            boolean settled = sameText.size() == 1 && leftKeys.get(a.key()) == 1;
            if (a.words().length > 0 && !settled) {
                for (int j = r0; j < r1; j++) {
                    Unit b = right.get(j);
                    if (!rightSettled[j - r0] && !b.key().equals(a.key())) {
                        double similarity = similarity(a, b);
                        if (similarity > 0) {
                            own.add(candidate(left, i, right, j, similarity));
                        }
                    }
                }
            }
            if (own.size() > CANDIDATES) {
                int row = i;
                own.sort(Comparator.comparingDouble(Candidate::weight).reversed().thenComparingDouble(
                        c -> Math.abs((row - l0) / leftLength - (c.right() - r0) / rightLength)));
                own = own.subList(0, CANDIDATES);
            }
            candidates.addAll(own);
        }
        return candidates;
    }

    /**
     * Of the candidates, those in order on both sides that weigh the most together: the heaviest chain, increasing in
     * both indexes, found with a Fenwick tree of the best chain ending before each right index.
     */
    private static List<Candidate> longestChain(List<Candidate> candidates, int rightSize) {
        List<Candidate> sorted = new ArrayList<>(candidates);
        // by left, and for one left from the last right: none of them may then follow another
        sorted.sort(Comparator.comparingInt(Candidate::left)
                .thenComparing(Candidate::right, Comparator.reverseOrder()));
        double[] treeWeight = new double[rightSize + 1];
        int[] treeEnd = new int[rightSize + 1];
        Arrays.fill(treeEnd, -1);
        double[] weight = new double[sorted.size()];
        int[] previous = new int[sorted.size()];
        int best = -1;
        for (int c = 0; c < sorted.size(); c++) {
            Candidate candidate = sorted.get(c);
            double before = 0;
            int beforeEnd = -1;
            for (int p = candidate.right(); p > 0; p -= p & -p) {   // the chains ending at a right index below
                if (treeEnd[p] >= 0 && treeWeight[p] > before) {
                    before = treeWeight[p];
                    beforeEnd = treeEnd[p];
                }
            }
            weight[c] = before + candidate.weight();
            previous[c] = beforeEnd;
            for (int p = candidate.right() + 1; p <= rightSize; p += p & -p) {
                if (treeEnd[p] < 0 || weight[c] > treeWeight[p]) {
                    treeWeight[p] = weight[c];
                    treeEnd[p] = c;
                }
            }
            if (best < 0 || weight[c] > weight[best]) {
                best = c;
            }
        }
        List<Candidate> chain = new ArrayList<>();
        for (int c = best; c >= 0; c = previous[c]) {
            chain.add(sorted.get(c));
        }
        return chain.reversed();
    }

    private static Map<String, Integer> countKeys(List<Unit> units) {
        Map<String, Integer> counts = new HashMap<>();
        for (Unit unit : units) {
            counts.merge(unit.key(), 1, Integer::sum);
        }
        return counts;
    }
}
