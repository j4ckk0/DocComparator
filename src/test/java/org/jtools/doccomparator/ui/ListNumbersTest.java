package org.jtools.doccomparator.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jtools.doccomparator.document.ListItem;
import org.junit.jupiter.api.Test;

/** List items numbered as Word numbers them, in the order of the paragraphs. */
class ListNumbersTest {

    @Test
    void levelsCountUnderTheOneAboveAndBeginAgainUnderTheNext() {
        ListNumbers numbers = new ListNumbers();
        assertEquals("1.", numbers.next(new ListItem("1", 0, "decimal", "%1.")));
        assertEquals("1.1.", numbers.next(new ListItem("1", 1, "decimal", "%1.%2.")));
        assertEquals("1.2.", numbers.next(new ListItem("1", 1, "decimal", "%1.%2.")));
        assertEquals("2.", numbers.next(new ListItem("1", 0, "decimal", "%1.")));
        assertEquals("2.1.", numbers.next(new ListItem("1", 1, "decimal", "%1.%2.")));
    }

    @Test
    void eachListCountsOnItsOwn() {
        ListNumbers numbers = new ListNumbers();
        assertEquals("1.", numbers.next(new ListItem("1", 0, "decimal", "%1.")));
        assertEquals("a)", numbers.next(new ListItem("2", 0, "lowerLetter", "%1)")));
        assertEquals("2.", numbers.next(new ListItem("1", 0, "decimal", "%1.")));
        assertEquals("b)", numbers.next(new ListItem("2", 0, "lowerLetter", "%1)")));
    }

    @Test
    void romanNumbersAndBullets() {
        ListNumbers numbers = new ListNumbers();
        for (int i = 1; i < 4; i++) {
            numbers.next(new ListItem("1", 0, "upperRoman", "%1."));
        }
        assertEquals("IV.", numbers.next(new ListItem("1", 0, "upperRoman", "%1.")));
        assertEquals("•", numbers.next(new ListItem("3", 0, "bullet", "")), "Symbol's bullet, as such");
        assertEquals("▪", numbers.next(new ListItem("3", 0, "bullet", "")), "Wingdings' square");
        assertEquals("o", numbers.next(new ListItem("3", 1, "bullet", "o")), "any other as it is");
    }

    @Test
    void aLevelWithoutTextIsNumberedAsWordDoesByDefault() {
        assertEquals("1.", new ListNumbers().next(new ListItem("1", 0, null, null)));
    }
}
