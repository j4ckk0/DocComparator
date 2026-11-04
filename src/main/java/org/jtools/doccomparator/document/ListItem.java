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

/**
 * Where a paragraph sits in a list: the list (Word's numId), its level from 0, and that level's numbering as the
 * document defines it — its format ("decimal", "bullet", "lowerLetter"…) and its text ("%1.", "•"), null when the
 * document does not say — and the paragraph properties the level gives its items: their indent, mostly, which wins
 * over the paragraph's style, and loses to the paragraph's own.
 */
public record ListItem(String listId, int level, String numberFormat, String levelText, Formatting paragraph) {

    public ListItem(String listId, int level, String numberFormat, String levelText) {
        this(listId, level, numberFormat, levelText, Formatting.NONE);
    }
}
