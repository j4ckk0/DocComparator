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

import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The properties a paragraph, a run, a table, a cell or a style sets, one entry per property, under Word's own names:
 * an element gives its name and value ("b" → "true", "sz" → "24", "jc" → "center"), each further attribute an entry of
 * its own ("u.color", "rFonts.ascii"), a nested element a dotted path ("pBdr.top.sz"). Generic on purpose: every
 * property can be compared, those without a name of ours (PropertyNames) included.
 */
public record Formatting(SortedMap<String, String> values) {

    public static final Formatting NONE = new Formatting(Map.of());

    public Formatting(Map<String, String> values) {
        this(Collections.unmodifiableSortedMap(new TreeMap<>(values)));
    }

    public Formatting {
        values = Collections.unmodifiableSortedMap(new TreeMap<>(values));
    }

    /** The value of a property, null when it is not set. */
    public String get(String name) {
        return values.get(name);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    /** Without these properties, nor those nested under them. */
    public Formatting without(String... names) {
        TreeMap<String, String> kept = new TreeMap<>(values);
        for (String name : names) {
            kept.keySet().removeIf(key -> key.equals(name) || key.startsWith(name + "."));
        }
        return new Formatting(kept);
    }
}
