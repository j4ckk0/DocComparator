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


/**
 * Something compared besides the text — a style, a heading level, a cell's shading — its value on each side, and the
 * kind of difference it is, which the options count or leave out.
 */
public record PropertyDifference(CompareOptions.Aspect aspect, Phrase property, Phrase left, Phrase right) {

    /** The difference, null when the values are the same; a null value is Phrase.NONE. */
    static PropertyDifference of(CompareOptions.Aspect aspect, Phrase property, Phrase left, Phrase right) {
        Phrase l = left == null ? Phrase.NONE : left;
        Phrase r = right == null ? Phrase.NONE : right;
        return l.equals(r) ? null : new PropertyDifference(aspect, property, l, r);
    }

    /** The same in every language, keys and Word's names: "compared.heading: value.heading(1) → value.none". */
    @Override
    public String toString() {
        return property + ": " + left + " → " + right;
    }
}
