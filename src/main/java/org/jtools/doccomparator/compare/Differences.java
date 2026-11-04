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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import org.jtools.doccomparator.compare.CompareOptions.Aspect;
import org.jtools.doccomparator.document.Formatting;
import java.util.function.UnaryOperator;

/** The differences of properties found; those of a kind the options leave out are dropped as they are found. */
final class Differences {

    private final CompareOptions options;
    private final List<PropertyDifference> found = new ArrayList<>();

    Differences(CompareOptions options) {
        this.options = options;
    }

    List<PropertyDifference> list() {
        return found;
    }

    void add(Aspect aspect, String property, String left, String right) {
        if (aspect != null && options.has(aspect)) {
            PropertyDifference difference = PropertyDifference.of(aspect, property, left, right);
            if (difference != null) {
                found.add(difference);
            }
        }
    }

    /** The properties two formattings set from there differ in, each under its name. */
    void formatting(Aspects.Source source, Formatting a, Formatting b) {
        formatting(source, UnaryOperator.identity(), a, b, aspect -> true);
    }

    /** The properties two formattings set from there differ in, each under its name as named says it ("cell {0}"). */
    void formatting(Aspects.Source source, UnaryOperator<String> named, Formatting a, Formatting b) {
        formatting(source, named, a, b, aspect -> true);
    }

    /**
     * The properties two formattings set from there differ in, of the kinds kept, each under its name as named says
     * it. A property and its attributes are one difference: "shading: clear, color auto, fill D9E2F3".
     */
    void formatting(Aspects.Source source, UnaryOperator<String> named, Formatting a, Formatting b,
                    Predicate<Aspect> kept) {
        for (Map.Entry<String, List<String>> property : properties(a, b).entrySet()) {
            Aspect aspect = Aspects.of(source, property.getKey());
            if (aspect != null && kept.test(aspect)) {
                add(aspect, named.apply(PropertyNames.of(property.getKey())), value(a, property), value(b, property));
            }
        }
    }

    /** The properties two formattings set differ in, all of this kind, each under its name as named says it. */
    void formattingAs(Aspect aspect, UnaryOperator<String> named, Formatting a, Formatting b) {
        for (Map.Entry<String, List<String>> property : properties(a, b).entrySet()) {
            add(aspect, named.apply(PropertyNames.of(property.getKey())), value(a, property), value(b, property));
        }
    }

    /**
     * The properties either formatting sets, each with the names of its values: its own ("shd"), its attributes'
     * ("shd.fill"). A name belongs to the nearest property set before it: "tcBorders.bottom.sz" to "tcBorders.bottom"
     * if that is set, a border with its width; "ind.left" to itself when "ind" has no value of its own.
     */
    static Map<String, List<String>> properties(Formatting a, Formatting b) {
        TreeSet<String> names = new TreeSet<>(a.values().keySet());
        names.addAll(b.values().keySet());
        Map<String, List<String>> properties = new TreeMap<>();
        for (String name : names) {
            String property = name;
            for (int dot = name.lastIndexOf('.'); dot > 0; dot = name.lastIndexOf('.', dot - 1)) {
                if (names.contains(name.substring(0, dot))) {
                    property = name.substring(0, dot);
                    break;
                }
            }
            properties.computeIfAbsent(property, p -> new ArrayList<>()).add(name);
        }
        return properties;
    }

    /** A property's value as a formatting sets it: its own, then its attributes'; null if it sets none. */
    private static String value(Formatting formatting, Map.Entry<String, List<String>> property) {
        List<String> parts = new ArrayList<>();
        for (String name : property.getValue()) {
            String value = formatting.get(name);
            if (value != null) {
                parts.add(name.equals(property.getKey()) ? value
                        : name.substring(property.getKey().length() + 1) + " " + value);
            }
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }
}
