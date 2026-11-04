package org.jtools.doccomparator.document;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The styles of a document by id, and its defaults: what a paragraph and a run are when nothing else says, and the
 * style of a paragraph that names none (Word's "Normal", the paragraph style marked w:default; null when none is).
 */
public record StyleSheet(Map<String, Style> styles, Formatting defaultParagraph, Formatting defaultRun,
                         String defaultParagraphStyle) {

    public static final StyleSheet NONE = new StyleSheet(Map.of(), Formatting.NONE, Formatting.NONE);

    public StyleSheet(Map<String, Style> styles, Formatting defaultParagraph, Formatting defaultRun) {
        this(styles, defaultParagraph, defaultRun, null);
    }

    /** Word's built-in heading styles keep their English name whatever the language: "heading 1" is "Titre 1" on screen. */
    private static final Pattern HEADING_NAME = Pattern.compile("(?i)heading ([1-9])");

    /** How far a chain of "based on" is followed: Word allows no loop, a broken document might have one. */
    private static final int LONGEST_CHAIN = 32;

    public StyleSheet {
        styles = Collections.unmodifiableMap(new LinkedHashMap<>(styles));
    }

    /** The style of a paragraph: the one it names, else the default paragraph style; null for none. */
    public String paragraphStyle(String id) {
        return id != null ? id : defaultParagraphStyle;
    }

    /** The style of this id; null for none, or one the document does not define. */
    public Style style(String id) {
        return id == null ? null : styles.get(id);
    }

    /**
     * The heading level a paragraph of this style has, from 1; 0 when it is not a heading. The style's outline level
     * says, else its name ("heading 2"), else the style it is based on.
     */
    public int headingLevel(String styleId) {
        Style style = style(styleId);
        for (int depth = 0; style != null && depth < LONGEST_CHAIN; depth++) {
            String outline = style.paragraph().get("outlineLvl");
            if (outline != null) {
                return headingLevelOfOutline(outline);
            }
            Matcher name = HEADING_NAME.matcher(style.name() == null ? "" : style.name());
            if (name.matches()) {
                return Integer.parseInt(name.group(1));
            }
            style = style(style.basedOn());
        }
        return 0;
    }

    /** A paragraph property as this style or those it is based on set it: the nearest wins; null when none does. */
    public String inheritedParagraphProperty(String styleId, String name) {
        Style style = style(styleId);
        for (int depth = 0; style != null && depth < LONGEST_CHAIN; depth++) {
            String value = style.paragraph().get(name);
            if (value != null) {
                return value;
            }
            style = style(style.basedOn());
        }
        return null;
    }

    /** Word's outline level, 0 to 8 for the headings 1 to 9, 9 for body text: the heading level, 0 for none. */
    public static int headingLevelOfOutline(String outlineLevel) {
        try {
            int level = Integer.parseInt(outlineLevel.trim());
            return level >= 0 && level < 9 ? level + 1 : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
