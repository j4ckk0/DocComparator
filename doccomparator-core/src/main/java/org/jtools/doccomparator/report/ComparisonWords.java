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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.jtools.doccomparator.compare.ComparedBlock;
import org.jtools.doccomparator.compare.PropertyDifference;
import org.jtools.doccomparator.compare.Phrase;
import org.jtools.doccomparator.compare.StoryComparison;
import org.jtools.doccomparator.document.Story;
import org.jtools.doccomparator.i18n.I18n;

/**
 * What the comparison holds, said in the language in use: a story's name, where a paragraph is, a part's title, how
 * many differences, a property difference, any phrase of it. The comparison keeps them as data (Phrase); this says
 * them, for a view of the comparison or any report of it.
 */
public final class ComparisonWords {

    /** Between the places of a location: "Footer, table 2". */
    private static final String BETWEEN_PLACES = ", ";

    private ComparisonWords() {
    }

    /** A story's name: "Body", "Header (first page), section 2", "Footnote 3". */
    public static String story(Story.Name name) {
        return switch (name.kind()) {
            case BODY -> I18n.text("story.body");
            case FOOTNOTE -> I18n.text("story.footnote", name.number());
            case ENDNOTE -> I18n.text("story.endnote", name.number());
            case HEADER, FOOTER -> headerOrFooter(name);
        };
    }

    private static String headerOrFooter(Story.Name name) {
        String said = I18n.text(name.kind() == Story.Kind.HEADER ? "story.header" : "story.footer");
        if (Story.Name.FIRST_PAGE.equals(name.pages())) {
            said = I18n.text("story.firstPage", said);
        } else if (Story.Name.EVEN_PAGES.equals(name.pages())) {
            said = I18n.text("story.evenPages", said);
        }
        return name.section() > 0 ? I18n.text("story.section", said, name.section()) : said;
    }

    /**
     * Where a paragraph or a table is, when the body's flow does not say (or always): "Table 2", "Footer, section 2",
     * "Body" itself left unsaid.
     */
    public static String location(ComparedBlock item, boolean always) {
        boolean inBody = item.story().kind() == Story.Kind.BODY;
        if (inBody && item.places().isEmpty()) {
            return always ? story(item.story()) : null;
        }
        List<String> parts = new ArrayList<>();
        if (!inBody) {
            parts.add(story(item.story()));
        }
        for (ComparedBlock.Place place : item.places()) {
            parts.add(place.textBox() ? I18n.text("location.textBox")
                    : place.isTable() ? I18n.text("location.table", place.table())
                    : I18n.text("location.cell", place.row(), place.column()));
        }
        String said = String.join(BETWEEN_PLACES, parts);
        return inBody ? Character.toUpperCase(said.charAt(0)) + said.substring(1) : said;
    }

    /** A phrase of the comparison, in the language in use: a text with its arguments said, a value as it is. */
    public static String say(Phrase phrase) {
        return switch (phrase) {
            case Phrase.Text text -> I18n.text(text.key(),
                    text.arguments().stream().map(a -> a instanceof Phrase inner ? say(inner) : a).toArray());
            case Phrase.Verbatim verbatim -> verbatim.value();
            case Phrase.Property property -> PropertyNames.of(property.wordName());
            case Phrase.Joined joined ->
                    joined.parts().stream().map(ComparisonWords::say).collect(Collectors.joining());
        };
    }

    /** How many differences: "no difference", "1 difference", "3 differences". */
    public static String count(long differences) {
        return differences == 0 ? I18n.text("count.none") : differences == 1 ? I18n.text("count.one")
                : I18n.text("count.many", differences);
    }

    /** A property difference, in words: "shading: clear → fill D9E2F3". */
    public static String change(PropertyDifference difference) {
        return I18n.text("line.change", say(difference.property()), say(difference.left()), say(difference.right()));
    }

    /** A property and one side's value of it: "heading: heading 2". */
    public static String property(Phrase property, Phrase value) {
        return I18n.text("line.property", say(property), say(value));
    }

    /** The title of a part of the documents: "Body", "Headers and footers", "Footnotes and endnotes". */
    public static String part(StoryComparison.Part part) {
        return switch (part) {
            case BODY -> I18n.text("part.body");
            case HEADERS_AND_FOOTERS -> I18n.text("part.headersAndFooters");
            case NOTES -> I18n.text("part.notes");
        };
    }
}
