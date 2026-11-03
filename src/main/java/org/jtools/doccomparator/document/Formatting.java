package org.jtools.doccomparator.document;

import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The properties a paragraph, a run, a table, a cell or a style sets, one entry per property, under Word's own names:
 * an element gives its name and value ("b" → "true", "sz" → "24", "jc" → "center"), each further attribute an entry of
 * its own ("u.color", "rFonts.ascii"), a nested element a dotted path ("pBdr.top.sz"). Generic on purpose: every
 * property can be compared, those the program has no name for included.
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
