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
package org.jtools.doccomparator.compare;

import java.util.List;

/** The pairings of one kind of story compared — "Body", "Headers and footers", "Footnotes and endnotes". */
public record StoryComparison(String title, List<Pairing> pairings) {

    public StoryComparison {
        pairings = List.copyOf(pairings);
    }

    /** The pairings that show a difference; a moved paragraph counts once on each side. */
    public long differences() {
        return pairings.stream().filter(Pairing::differs).count();
    }
}
