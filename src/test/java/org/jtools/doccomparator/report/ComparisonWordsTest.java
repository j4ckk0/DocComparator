package org.jtools.doccomparator.report;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.jtools.doccomparator.compare.Item;
import org.jtools.doccomparator.compare.StoryComparison;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.Formatting;
import org.jtools.doccomparator.document.Story;
import org.junit.jupiter.api.Test;

/** What the comparison holds, said in words: the stories' names, where a paragraph is, the parts' titles. */
class ComparisonWordsTest {

    private static final Paragraph EMPTY = new Paragraph(null, Formatting.NONE, 0, null, false, List.of());

    @Test
    void storiesAreNamedAsWordShowsThem() {
        assertEquals("Body", ComparisonWords.story(Story.Name.BODY));
        assertEquals("Header, section 1", ComparisonWords.story(Story.Name.headerOrFooter(Story.Kind.HEADER, "default", 1)));
        assertEquals("Header (first page), section 2",
                ComparisonWords.story(Story.Name.headerOrFooter(Story.Kind.HEADER, Story.Name.FIRST_PAGE, 2)));
        assertEquals("Footer (even pages)",
                ComparisonWords.story(Story.Name.headerOrFooter(Story.Kind.FOOTER, Story.Name.EVEN_PAGES, 0)));
        assertEquals("Footnote 3", ComparisonWords.story(Story.Name.note(Story.Kind.FOOTNOTE, 3)));
    }

    /** In the body's flow, nothing to say; elsewhere, the story then the places, the body's own name left unsaid. */
    @Test
    void aLocationSaysTheStoryThenThePlaces() {
        assertNull(ComparisonWords.location(new Item(Story.Name.BODY, List.of(), EMPTY, null), false));
        Item inTable = new Item(Story.Name.BODY, List.of(new Item.Place(false, 2, 0, 0)), EMPTY, null);
        assertEquals("Table 2", ComparisonWords.location(inTable, false));
        Item inFooter = new Item(Story.Name.headerOrFooter(Story.Kind.FOOTER, "default", 2), List.of(), EMPTY, null);
        assertEquals("Footer, section 2", ComparisonWords.location(inFooter, false));
    }

    @Test
    void partsHaveTheirTitles() {
        assertEquals("Headers and footers", ComparisonWords.part(StoryComparison.Part.HEADERS_AND_FOOTERS));
    }
}
