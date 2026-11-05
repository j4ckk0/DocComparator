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

import static org.jtools.doccomparator.compare.Paragraphs.units;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PairerTest {

    private final Vocabulary vocabulary = new Vocabulary();

    private static final String PUMP = "Stop the pump and lock out the main switch before you open the housing.";
    private static final String TRAY = "Place a drip tray under the filter housing to catch the oil.";
    private static final String GLOVES = "Wear safety glasses and oil-resistant gloves at all times.";
    private static final String RECORD = "Record the date and the outlet pressure in the maintenance log.";

    @Test
    void theSameParagraphsArePairedInOrder() {
        List<Pairer.Match> matches = Pairer.pair(units(vocabulary, PUMP, TRAY, GLOVES),
                units(vocabulary, PUMP, TRAY, GLOVES));
        assertEquals(List.of(new Pairer.Match(0, 0, 1, false), new Pairer.Match(1, 1, 1, false),
                new Pairer.Match(2, 2, 1, false)), matches);
    }

    @Test
    void anEditedParagraphIsPairedWhereItIs() {
        List<Pairer.Match> matches = Pairer.pair(units(vocabulary, PUMP, TRAY, GLOVES),
                units(vocabulary, PUMP, "Place a large drip tray under the pump housing to catch the oil.", GLOVES));
        assertEquals(3, matches.size());
        Pairer.Match edited = matches.get(1);
        assertEquals(1, edited.left());
        assertEquals(1, edited.right());
        assertTrue(edited.similarity() > 0.8 && edited.similarity() < 1, "similarity " + edited.similarity());
    }

    @Test
    void anAddedParagraphHasNoMatch() {
        List<Pairer.Match> matches = Pairer.pair(units(vocabulary, PUMP, GLOVES),
                units(vocabulary, PUMP, TRAY, GLOVES));
        assertEquals(List.of(new Pairer.Match(0, 0, 1, false), new Pairer.Match(1, 2, 1, false)), matches);
    }

    @Test
    void unrelatedParagraphsAreNotPaired() {
        List<Pairer.Match> matches = Pairer.pair(units(vocabulary, PUMP, TRAY),
                units(vocabulary, PUMP, "The quarterly budget review moved to Thursday afternoon."));
        assertEquals(List.of(new Pairer.Match(0, 0, 1, false)), matches);
    }

    @Test
    void aRewrittenParagraphIsPairedInPlaceWithLessInCommon() {
        String before = "Unscrew the filter cover by hand, without any tool.";
        String after = "Unscrew the filter cover with the KP-T-05 strap wrench.";
        assertEquals(0, Pairer.similarity(units(vocabulary, before).getFirst(), units(vocabulary, after).getFirst()),
                "not enough in common to be paired anywhere");
        List<Pairer.Match> matches = Pairer.pair(units(vocabulary, PUMP, before, GLOVES),
                units(vocabulary, PUMP, after, GLOVES));
        assertEquals(new Pairer.Match(1, 1, 0.4, false), matches.get(1), "but enough where the other was");
    }

    @Test
    void aMovedParagraphIsPairedOutOfOrder() {
        List<Pairer.Match> matches = Pairer.pair(units(vocabulary, GLOVES, PUMP, TRAY, RECORD),
                units(vocabulary, PUMP, TRAY, RECORD, GLOVES));
        assertEquals(List.of(new Pairer.Match(1, 0, 1, false), new Pairer.Match(2, 1, 1, false),
                new Pairer.Match(3, 2, 1, false), new Pairer.Match(0, 3, 1, true)), matches);
    }

    @Test
    void aMovedParagraphMayBeEditedToo() {
        List<Pairer.Match> matches = Pairer.pair(units(vocabulary, GLOVES, PUMP, TRAY, RECORD),
                units(vocabulary, PUMP, TRAY, RECORD, "Always wear safety glasses and oil-resistant gloves."));
        Pairer.Match moved = matches.getLast();
        assertTrue(moved.moved());
        assertEquals(0, moved.left());
        assertEquals(3, moved.right());
    }

    @Test
    void ofTwoBlocksSwappedTheLargerStaysInOrder() {
        List<Pairer.Match> matches = Pairer.pair(units(vocabulary, "Safety", GLOVES, "Procedure", PUMP, TRAY, RECORD),
                units(vocabulary, "Procedure", PUMP, TRAY, RECORD, "Safety", GLOVES));
        assertEquals(List.of(new Pairer.Match(2, 0, 1, false), new Pairer.Match(3, 1, 1, false),
                new Pairer.Match(4, 2, 1, false), new Pairer.Match(5, 3, 1, false),
                new Pairer.Match(0, 4, 1, true), new Pairer.Match(1, 5, 1, true)), matches);
    }

    /** Two figures swapped: each found where it went, moved, by its image. */
    @Test
    void aMovedFigureIsFoundByItsImage() {
        List<Unit> left = List.of(unit("pump"), unit(PUMP), unit("valve"), unit(TRAY));
        List<Unit> right = List.of(unit("valve"), unit(PUMP), unit("pump"), unit(TRAY));
        List<Pairer.Match> matches = Pairer.pair(left, right);
        assertTrue(matches.contains(new Pairer.Match(1, 1, 1, false)));
        assertTrue(matches.stream().filter(Pairer.Match::moved).count() >= 1, "moved: " + matches);
        assertTrue(matches.stream().noneMatch(m -> m.left() == 0 && m.right() == 0), "not the other figure");
        assertEquals(4, matches.size(), "each paired: " + matches);
    }

    private Unit unit(String figure) {
        if (figure.length() > 10) {
            return Paragraphs.units(vocabulary, figure).getFirst();
        }
        return Paragraphs.unit(Paragraphs.paragraph(null, 0, null, Paragraphs.picture(figure)), false, vocabulary);
    }

    @Test
    void aShortTextFoundSeveralTimesIsNotLinkedAcrossTheDocument() {
        List<Pairer.Match> matches = Pairer.pair(units(vocabulary, "N/A", "N/A", PUMP),
                units(vocabulary, PUMP, "N/A"));
        assertEquals(List.of(new Pairer.Match(2, 0, 1, false)), matches);
    }

    @Test
    void ofTheSameTextsInATableTheSameCellIsPreferred() {
        Item.Place row2 = Item.Place.inCell(1, 2, 2);
        Item.Place row3 = Item.Place.inCell(1, 3, 2);
        Item.Place row4 = Item.Place.inCell(1, 4, 2);
        List<Unit> left = List.of(cell("1", row2), cell("1", row3));
        List<Unit> right = List.of(cell("1", row2), cell("1", row3), cell("1", row4));
        List<Pairer.Match> matches = Pairer.pair(left, right);
        assertEquals(List.of(new Pairer.Match(0, 0, 1, false), new Pairer.Match(1, 1, 1, false)), matches);
        matches = Pairer.pair(List.of(cell("1", row2), cell("1", row4)), right);
        assertEquals(List.of(new Pairer.Match(0, 0, 1, false), new Pairer.Match(1, 2, 1, false)), matches);
    }

    private Unit cell(String text, Item.Place place) {
        return Unit.of(new Item(org.jtools.doccomparator.document.Story.Name.BODY, List.of(place),
                Paragraphs.paragraph(text), null), false, vocabulary);
    }
}
