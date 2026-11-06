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
import java.util.List;
import org.jtools.doccomparator.compare.CompareOptions.Aspect;
import java.util.Map;
import java.util.TreeMap;
import org.jtools.doccomparator.document.Formatting;
import org.jtools.doccomparator.document.ListItem;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.Picture;
import org.jtools.doccomparator.document.Run;
import org.jtools.doccomparator.document.Story;

/** Paragraphs and units for a test, without a document. */
final class Paragraphs {

    /** Everything compared, but the blanks. */
    static final CompareOptions FULL = CompareOptions.ALL.with(Aspect.BLANKS, false);

    private Paragraphs() {
    }

    /** Property differences in words the same in every language, each its kind first: "CHARACTERS b: true → value.none". */
    static List<String> said(List<PropertyDifference> differences) {
        return differences.stream().map(d -> d.aspect() + " " + d).toList();
    }

    static Paragraph paragraph(String text) {
        return paragraph(null, 0, null, new Run(text, null, Formatting.NONE));
    }

    static Paragraph paragraph(String styleId, int headingLevel, ListItem listItem, Run... runs) {
        return new Paragraph(styleId, Formatting.NONE, headingLevel, listItem, false, List.of(runs));
    }

    static Run run(String text, String... properties) {
        Map<String, String> values = new TreeMap<>();
        for (String property : properties) {
            values.put(property, "true");
        }
        return new Run(text, null, new Formatting(values));
    }

    /** A picture's run: its character, its image told by this digest. */
    static Run picture(String digest) {
        return new Run(String.valueOf(Picture.IN_TEXT), null, Formatting.NONE,
                new Picture(Picture.Kind.IMAGE, digest, 100, 60, "png", digest, new byte[] {1}));
    }

    static ComparedBlock item(Paragraph paragraph) {
        return new ComparedBlock(Story.Name.BODY, List.of(), paragraph, null);
    }

    static Unit unit(Paragraph paragraph, boolean blanks, Vocabulary vocabulary) {
        return Unit.of(item(paragraph), blanks, vocabulary);
    }

    static List<Unit> units(Vocabulary vocabulary, String... texts) {
        List<Unit> units = new ArrayList<>();
        for (String text : texts) {
            units.add(unit(paragraph(text), false, vocabulary));
        }
        return units;
    }
}
