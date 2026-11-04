package org.jtools.doccomparator.compare;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import org.jtools.doccomparator.compare.CompareOptions.Aspect;
import org.jtools.doccomparator.document.Formatting;
import org.jtools.doccomparator.document.Style;
import org.jtools.doccomparator.document.StyleSheet;

/** The differences of properties found, those of a kind the options leave out left out as they are found. */
final class Differences {

    /** How far a chain of "based on" is followed, as StyleSheet does. */
    private static final int LONGEST_CHAIN = 32;

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

    /** The properties two formattings set from there differ in, each under its name preceded by prefix. */
    void formatting(Aspects.Source source, String prefix, Formatting a, Formatting b) {
        formatting(source, prefix, a, b, aspect -> true);
    }

    /**
     * The properties two formattings set from there differ in, of the kinds kept, each under its name preceded by
     * prefix. A property and its attributes are one difference: "shading: clear, color auto, fill D9E2F3".
     */
    void formatting(Aspects.Source source, String prefix, Formatting a, Formatting b, Predicate<Aspect> kept) {
        for (Map.Entry<String, List<String>> property : properties(a, b).entrySet()) {
            Aspect aspect = Aspects.of(source, property.getKey());
            if (aspect != null && kept.test(aspect)) {
                add(aspect, prefix + PropertyNames.of(property.getKey()), value(a, property), value(b, property));
            }
        }
    }

    /** The properties two formattings set differ in, all of this kind, each under its name preceded by prefix. */
    void formattingAs(Aspect aspect, String prefix, Formatting a, Formatting b) {
        for (Map.Entry<String, List<String>> property : properties(a, b).entrySet()) {
            add(aspect, prefix + PropertyNames.of(property.getKey()), value(a, property), value(b, property));
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

    /**
     * A paragraph's formatting as it applies: the document's default, then the styles its style is based on, the
     * farthest first, then its style (the default paragraph style if it names none), then its own — the nearest wins. Two documents on different templates may lay
     * out alike paragraphs that set nothing themselves.
     */
    static Formatting effective(StyleSheet styles, String styleId, Formatting own) {
        List<Style> chain = new ArrayList<>();
        Style style = styles.style(styles.paragraphStyle(styleId));   // no style named: the default one, "Normal"
        for (int depth = 0; style != null && depth < LONGEST_CHAIN; depth++) {
            chain.add(style);
            style = styles.style(style.basedOn());
        }
        TreeMap<String, String> values = new TreeMap<>(styles.defaultParagraph().values());
        for (Style s : chain.reversed()) {
            values.putAll(s.paragraph().values());
        }
        values.putAll(own.values());
        return new Formatting(values);
    }
}
