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
 * A Word document as the comparison sees it: its stories — the body, the headers and footers, the footnotes and
 * endnotes — its styles and its template. Tracked changes are read as if accepted: what was inserted is in the text,
 * what was deleted is not; hasTrackedChanges says the document had some.
 */
public record WordDocument(String fileName, Story body, List<Story> headersAndFooters, List<Story> notes,
                           StyleSheet styles, Template template, boolean hasTrackedChanges) {

    public WordDocument {
        headersAndFooters = List.copyOf(headersAndFooters);
        notes = List.copyOf(notes);
    }
}
