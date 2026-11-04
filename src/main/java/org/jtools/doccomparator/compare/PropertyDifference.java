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

import org.jtools.doccomparator.i18n.I18n;

/**
 * Something compared besides the text — a style, a heading level, a cell's shading — its value on each side, and the
 * kind of difference it is, which the options count or leave out.
 */
public record PropertyDifference(CompareOptions.Aspect aspect, String property, String left, String right) {

    /** What stands for a value a side does not have: "(none)". */
    public static String none() {
        return I18n.text("value.none");
    }

    /** The difference, null when the values are the same; a null value is none(). */
    static PropertyDifference of(CompareOptions.Aspect aspect, String property, String left, String right) {
        String l = left == null ? none() : left;
        String r = right == null ? none() : right;
        return l.equals(r) ? null : new PropertyDifference(aspect, property, l, r);
    }

    @Override
    public String toString() {
        return property + ": " + left + " → " + right;
    }
}
