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

import java.util.List;

/** The pairings of one part of the documents compared: the body, the headers and footers, the notes. */
public record StoryComparison(Part part, List<Pairing> pairings) {

    /** A part of the documents, compared on its own: its stories paired with the other document's. */
    public enum Part {
        BODY, HEADERS_AND_FOOTERS, NOTES
    }

    public StoryComparison {
        pairings = List.copyOf(pairings);
    }

    /** The pairings that show a difference; a moved paragraph counts once on each side. */
    public long differences() {
        return pairings.stream().filter(Pairing::differs).count();
    }
}
