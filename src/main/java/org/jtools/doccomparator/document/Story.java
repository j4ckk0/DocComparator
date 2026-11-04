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
package org.jtools.doccomparator.document;

import java.util.List;

/**
 * One flow of text, as Word calls it: the body, a header, a footer, a footnote, an endnote. Its label names it for a
 * reader: "Body", "Header (first page), section 2", "Footnote 3".
 */
public record Story(Kind kind, String label, List<Block> blocks) {

    public enum Kind {
        BODY, HEADER, FOOTER, FOOTNOTE, ENDNOTE
    }

    public Story {
        blocks = List.copyOf(blocks);
    }
}
