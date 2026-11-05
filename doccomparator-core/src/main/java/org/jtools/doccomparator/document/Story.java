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
 * One flow of text, as Word calls it: the body, a header, a footer, a footnote, an endnote; named by what tells it from
 * the others (Name), which the view says in words: "Body", "Header (first page), section 2", "Footnote 3".
 */
public record Story(Name name, List<Block> blocks) {

    public enum Kind {
        BODY, HEADER, FOOTER, FOOTNOTE, ENDNOTE
    }

    /**
     * What tells a story from the others: its kind; for a header or a footer, the pages it is for, as Word writes them
     * ("default", "first", "even"), and its section, from 1, or 0 when the document has a single one; for a note, its
     * number, from 1.
     */
    public record Name(Kind kind, String pages, int section, int number) {

        public static final Name BODY = new Name(Kind.BODY, null, 0, 0);
        /** The pages of a header or a footer for the first page of its section only, as Word writes it. */
        public static final String FIRST_PAGE = "first";
        /** The pages of a header or a footer for the even pages only, as Word writes it. */
        public static final String EVEN_PAGES = "even";

        public static Name headerOrFooter(Kind kind, String pages, int section) {
            return new Name(kind, pages, section, 0);
        }

        public static Name note(Kind kind, int number) {
            return new Name(kind, null, 0, number);
        }
    }

    public Story {
        blocks = List.copyOf(blocks);
    }

    public Kind kind() {
        return name.kind();
    }
}
