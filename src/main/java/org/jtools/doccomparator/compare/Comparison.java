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
import org.jtools.doccomparator.document.WordDocument;

/**
 * Two documents compared: each kind of story, pairing by pairing, and what differs of the documents themselves — template,
 * style definitions, sections — as the options compare them.
 */
public record Comparison(WordDocument left, WordDocument right, CompareOptions options, List<StoryComparison> stories,
                         List<PropertyDifference> documentDifferences) {

    public Comparison {
        stories = List.copyOf(stories);
        documentDifferences = List.copyOf(documentDifferences);
    }

    /** Whether nothing compared differs. */
    public boolean same() {
        return documentDifferences.isEmpty() && stories.stream().allMatch(story -> story.differences() == 0);
    }
}
