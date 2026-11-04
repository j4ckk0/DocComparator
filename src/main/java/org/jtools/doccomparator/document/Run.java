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

/**
 * A piece of a paragraph's text that has one formatting: the character style it is in (null for none) and the
 * properties set on it directly. Word splits text into runs at will; the reader joins those that look the same. A
 * picture is a run of its own, its text the one character that stands for it (Picture.IN_TEXT), never joined.
 */
public record Run(String text, String styleId, Formatting formatting, Picture picture) {

    public Run(String text, String styleId, Formatting formatting) {
        this(text, styleId, formatting, null);
    }
}
