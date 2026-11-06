package org.jtools.doccomparator.document;

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

import java.util.List;

/**
 * A document as the comparison sees it, whatever its format (DocumentReader): every format is read into it in Word's
 * terms — stories, paragraphs, runs, Word's property names — the comparison's one vocabulary. Its stories — the body,
 * the headers and footers, the footnotes and endnotes —, its styles and its template. Tracked changes are read as if
 * accepted: what was inserted is in the text, what was deleted is not; hasTrackedChanges says the document had some.
 */
public record Document(String fileName, Story body, List<Story> headersAndFooters, List<Story> notes,
                           StyleSheet styles, Template template, boolean hasTrackedChanges) {

    public Document {
        headersAndFooters = List.copyOf(headersAndFooters);
        notes = List.copyOf(notes);
    }

    /**
     * A document that is its body alone: no header, footer or note, no style, no template, no tracked change — what a
     * format without them reads (text, a workbook, a reader of one's own).
     */
    public static Document of(String fileName, List<? extends Block> body) {
        return new Document(fileName, new Story(Story.Name.BODY, List.copyOf(body)), List.of(), List.of(),
                StyleSheet.NONE, Template.NONE, false);
    }
}
