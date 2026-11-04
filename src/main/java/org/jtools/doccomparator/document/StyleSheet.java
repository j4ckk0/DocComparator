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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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

    /**
     * Word's built-in heading styles keep their English name whatever the language: "heading 1" is "Titre 1" on screen.
     */
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
     * A style's name, which is the same in two documents when its id may not be ("Heading1", "Titre1"); its id when the
     * document gives it no name; null for none.
     */
    public String nameOf(String id) {
        Style style = style(id);
        return style == null || style.name() == null ? id : style.name();
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

    /**
     * A paragraph's properties as they apply: the document's defaults, then the styles its style is based on, the
     * farthest first, then its style (the default paragraph style if it names none), then its own — the nearest wins.
     */
    public Formatting effectiveParagraph(String styleId, Formatting own) {
        TreeMap<String, String> values = new TreeMap<>(defaultParagraph.values());
        for (Style style : chain(paragraphStyle(styleId))) {
            values.putAll(style.paragraph().values());
        }
        values.putAll(own.values());
        return new Formatting(values);
    }

    /**
     * A paragraph's properties as they apply, its list's included: the defaults, its style's, then those its list level
     * gives its items (their indent), then its own.
     */
    public Formatting effectiveParagraph(Paragraph paragraph) {
        Formatting own = paragraph.formatting();
        if (paragraph.listItem() != null && !paragraph.listItem().paragraph().isEmpty()) {
            TreeMap<String, String> values = new TreeMap<>(paragraph.listItem().paragraph().values());
            values.putAll(own.values());
            own = new Formatting(values);
        }
        return effectiveParagraph(paragraph.styleId(), own);
    }

    /**
     * A run's properties as they apply: the document's defaults, then those of its paragraph's style and the styles it
     * is based on, then its character style's and theirs, then its own — the nearest wins.
     */
    public Formatting effectiveRun(String paragraphStyleId, String runStyleId, Formatting own) {
        TreeMap<String, String> values = new TreeMap<>(defaultRun.values());
        for (Style style : chain(paragraphStyle(paragraphStyleId))) {
            values.putAll(style.run().values());
        }
        for (Style style : chain(runStyleId)) {
            values.putAll(style.run().values());
        }
        values.putAll(own.values());
        return new Formatting(values);
    }

    /** A style and those it is based on, the farthest first. */
    private List<Style> chain(String id) {
        List<Style> chain = new ArrayList<>();
        Style style = style(id);
        for (int depth = 0; style != null && depth < LONGEST_CHAIN; depth++) {
            chain.add(style);
            style = style(style.basedOn());
        }
        return chain.reversed();
    }

    /** Word's outline level, 0 to 8 for the headings 1 to 9, 9 for body text: the heading level, 0 for none. */
    public static int headingLevelOfOutline(String outlineLevel) {
        try {
            int level = Integer.parseInt(outlineLevel.trim());
            return level >= 0 && level < 9 ? level + 1 : 0;
        } catch (NumberFormatException _) {
            return 0;
        }
    }
}
