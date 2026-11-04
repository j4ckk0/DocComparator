package org.jtools.doccomparator.compare;

import java.util.EnumSet;
import java.util.Set;

/**
 * What the comparison takes into account: the text and each kind of layout, each compared or left out on its own. Left
 * out, a kind of difference is neither counted nor shown. The text left out, paragraphs are still paired by it — their
 * layout can only be compared so — but nothing of it is shown as differing: no word marked, no paragraph added,
 * removed or moved.
 */
public record CompareOptions(Set<Aspect> aspects) {

    /** A kind of difference; its label and description are texts of the language in use (i18n). */
    public enum Aspect {
        TEXT, BLANKS, HEADINGS, LISTS, SECTIONS, STYLES, CHARACTERS, INDENTATION, SPACING, ALIGNMENT, PARAGRAPHS,
        TABLE_LAYOUT, CELLS, TEMPLATE, HEADERS_AND_FOOTERS;

        /** The key of its label: "aspect.TABLE_LAYOUT.label". */
        public String labelKey() {
            return "aspect." + name() + ".label";
        }

        /** The key of what it covers, in a sentence. */
        public String descriptionKey() {
            return "aspect." + name() + ".description";
        }
    }

    /** The text, the headers and footers included: the content, the layout left out until asked for. */
    public static final CompareOptions DEFAULT = new CompareOptions(EnumSet.of(Aspect.TEXT, Aspect.HEADERS_AND_FOOTERS));
    /** The text alone, the body's and the notes'. */
    public static final CompareOptions TEXT_ONLY = new CompareOptions(EnumSet.of(Aspect.TEXT));
    /** Everything but the text and the blanks: the layout, the headers and footers included. */
    public static final CompareOptions LAYOUT_ONLY = new CompareOptions(
            EnumSet.complementOf(EnumSet.of(Aspect.TEXT, Aspect.BLANKS)));
    /** Everything. */
    public static final CompareOptions ALL = new CompareOptions(EnumSet.allOf(Aspect.class));

    public CompareOptions {
        aspects = Set.copyOf(aspects);
    }

    public boolean has(Aspect aspect) {
        return aspects.contains(aspect);
    }

    public CompareOptions with(Aspect aspect, boolean on) {
        EnumSet<Aspect> changed = aspects.isEmpty() ? EnumSet.noneOf(Aspect.class) : EnumSet.copyOf(aspects);
        if (on) {
            changed.add(aspect);
        } else {
            changed.remove(aspect);
        }
        return new CompareOptions(changed);
    }

    public boolean blanks() {
        return has(Aspect.BLANKS);
    }

    public boolean text() {
        return has(Aspect.TEXT);
    }
}
