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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jtools.doccomparator.report.PropertyNames;
import org.junit.jupiter.api.Test;

/** The names a reader other than Word's sets are Word's own, each one the report says in words: no typo in them. */
class WordPropertiesTest {

    /** Not names but values: those of VERTICAL_MERGE. */
    private static final Set<String> VALUES = Set.of("MERGE_STARTS", "MERGE_CONTINUES");
    /** The cells' place on the table's grid, never compared themselves: no name of ours. */
    private static final Set<String> NEVER_COMPARED = Set.of("gridSpan", "vMerge");

    @Test
    void everyNameIsSaid() throws IllegalAccessException {
        List<String> unknown = new ArrayList<>();
        for (String name : names()) {
            String root = name.split("\\.")[0];
            if (!PropertyNames.NAMED.contains(root) && !NEVER_COMPARED.contains(root)) {
                unknown.add(name);
            }
        }
        assertTrue(unknown.isEmpty(), "no name of ours in PropertyNames.NAMED: " + unknown);
    }

    @Test
    void aCellsBorderIsSaidAsABorder() {
        assertEquals("tcBorders.top.sz", WordProperties.cellBorder("top", "sz"));
        assertFalse(PropertyNames.of(WordProperties.cellBorder("top", "sz")).startsWith("tcBorders"));
    }

    private static List<String> names() throws IllegalAccessException {
        List<String> names = new ArrayList<>();
        for (Field field : WordProperties.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class
                    && !VALUES.contains(field.getName())) {
                names.add((String) field.get(null));
            }
        }
        assertFalse(names.isEmpty());
        return names;
    }
}
