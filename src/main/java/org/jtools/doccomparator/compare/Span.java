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

/** A stretch of a paragraph's text, [start, end), and how it compares with the paragraph it is paired with. */
public record Span(int start, int end, Kind kind) {

    public enum Kind {
        /** The same text on the other side. */
        SAME,
        /** Other text in its place on the other side. */
        CHANGED,
        /** Nothing in its place on the other side: removed, or added. */
        ONLY_HERE,
        /** Empty: where the other side has text this one has not (ONLY_HERE there). */
        ABSENT
    }

    /** Whether it is a difference that counts. */
    public boolean differs() {
        return kind == Kind.CHANGED || kind == Kind.ONLY_HERE || kind == Kind.ABSENT;
    }
}
