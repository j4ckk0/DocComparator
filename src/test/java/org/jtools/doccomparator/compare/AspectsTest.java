package org.jtools.doccomparator.compare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.jtools.doccomparator.compare.Aspects.Source;
import org.jtools.doccomparator.compare.CompareOptions.Aspect;
import org.junit.jupiter.api.Test;

class AspectsTest {

    @Test
    void aParagraphsPropertiesByKind() {
        assertEquals(Aspect.INDENTATION, Aspects.of(Source.PARAGRAPH, "ind.left"));
        assertEquals(Aspect.INDENTATION, Aspects.of(Source.PARAGRAPH, "ind.hanging"));
        assertEquals(Aspect.SPACING, Aspects.of(Source.PARAGRAPH, "spacing.after"));
        assertEquals(Aspect.SPACING, Aspects.of(Source.PARAGRAPH, "contextualSpacing"));
        assertEquals(Aspect.ALIGNMENT, Aspects.of(Source.PARAGRAPH, "jc"));
        assertEquals(Aspect.HEADINGS, Aspects.of(Source.PARAGRAPH, "outlineLvl"));
        assertEquals(Aspect.PARAGRAPHS, Aspects.of(Source.PARAGRAPH, "shd.fill"));
        assertEquals(Aspect.PARAGRAPHS, Aspects.of(Source.PARAGRAPH, "keepNext"));
        assertEquals(Aspect.PARAGRAPHS, Aspects.of(Source.PARAGRAPH, "pBdr.top.sz"));
    }

    @Test
    void theSameNameIsAnotherKindElsewhere() {
        assertEquals(Aspect.ALIGNMENT, Aspects.of(Source.PARAGRAPH, "jc"));
        assertEquals(Aspect.TABLE_LAYOUT, Aspects.of(Source.TABLE, "jc"));
        assertEquals(Aspect.CHARACTERS, Aspects.of(Source.RUN, "shd.fill"));
        assertEquals(Aspect.PARAGRAPHS, Aspects.of(Source.PARAGRAPH, "shd.fill"));
        assertEquals(Aspect.CELLS, Aspects.of(Source.CELL, "shd.fill"));
        assertEquals(Aspect.CELLS, Aspects.of(Source.TABLE, "shd.fill"));
    }

    @Test
    void tablesRowsAndCells() {
        assertEquals(Aspect.CELLS, Aspects.of(Source.CELL, "tcBorders.bottom.sz"));
        assertEquals(Aspect.CELLS, Aspects.of(Source.CELL, "vAlign"));
        assertEquals(Aspect.TABLE_LAYOUT, Aspects.of(Source.CELL, "tcW.w"));
        assertEquals(Aspect.TABLE_LAYOUT, Aspects.of(Source.ROW, "trHeight"));
        assertEquals(Aspect.CELLS, Aspects.of(Source.ROW, "tblHeader"));
        assertEquals(Aspect.TABLE_LAYOUT, Aspects.of(Source.TABLE, "tblW.w"));
        assertEquals(Aspect.CELLS, Aspects.of(Source.TABLE, "tblBorders.insideH.val"));
        assertEquals(Aspect.STYLES, Aspects.of(Source.RUN, "rStyle"));
        assertEquals(Aspect.CHARACTERS, Aspects.of(Source.RUN, "b"));
    }

    @Test
    void whatWordRewritesAndTheGridAreNeverCompared() {
        assertNull(Aspects.of(Source.CELL, "cnfStyle"));
        assertNull(Aspects.of(Source.ROW, "cnfStyle"));
        assertNull(Aspects.of(Source.PARAGRAPH, "cnfStyle.firstRow"));
        assertNull(Aspects.of(Source.CELL, "gridSpan"));
        assertNull(Aspects.of(Source.CELL, "vMerge"));
    }
}
