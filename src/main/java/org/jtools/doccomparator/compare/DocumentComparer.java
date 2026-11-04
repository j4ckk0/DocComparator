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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jtools.doccomparator.compare.CompareOptions.Aspect;
import org.jtools.doccomparator.document.Block;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.Story;
import org.jtools.doccomparator.document.Style;
import org.jtools.doccomparator.document.StyleSheet;
import org.jtools.doccomparator.document.WordDocument;

/**
 * Compares two Word documents: their paragraphs and tables paired (Pairer), the pairs compared (ParagraphComparer,
 * TableComparer), then laid out in pairings for a side-by-side view, kind of story by kind of story; and the documents
 * themselves.
 */
public final class DocumentComparer {

    private final WordDocument left;
    private final WordDocument right;
    private final CompareOptions options;
    private final Vocabulary vocabulary = new Vocabulary();
    private final ParagraphComparer paragraphs;
    private final TableComparer tables;

    private DocumentComparer(WordDocument left, WordDocument right, CompareOptions options) {
        this.left = left;
        this.right = right;
        this.options = options;
        this.paragraphs = new ParagraphComparer(options, vocabulary, left.styles(), right.styles());
        this.tables = new TableComparer(options, paragraphs, left.styles(), right.styles());
    }

    public static Comparison compare(WordDocument left, WordDocument right, CompareOptions options) {
        return new DocumentComparer(left, right, options).compare();
    }

    private Comparison compare() {
        List<StoryComparison> stories = new ArrayList<>();
        stories.add(compare(StoryComparison.Part.BODY, List.of(left.body()), List.of(right.body())));
        if (options.has(Aspect.HEADERS_AND_FOOTERS)) {
            stories.add(compare(StoryComparison.Part.HEADERS_AND_FOOTERS, left.headersAndFooters(),
                    right.headersAndFooters()));
        }
        stories.add(compare(StoryComparison.Part.NOTES, left.notes(), right.notes()));
        return new Comparison(left, right, options, stories, documentDifferences());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // The stories

    private StoryComparison compare(StoryComparison.Part part, List<Story> leftStories, List<Story> rightStories) {
        List<Unit> l = units(leftStories);
        List<Unit> r = units(rightStories);
        List<Slot> slots = Slot.align(l.size(), r.size(), Pairer.pair(l, r));
        List<Pairing> pairings = new ArrayList<>();
        for (Slot slot : slots) {
            if (slot.left() >= 0 && slot.right() >= 0) {
                pairings.add(pair(l.get(slot.left()), r.get(slot.right()), -1));
            } else if (slot.left() >= 0) {
                Unit unit = l.get(slot.left());
                if (slot.moved()) {
                    Pairing pair = pair(unit, r.get(slots.get(slot.counterpart()).right()), slot.counterpart());
                    pairings.add(new Pairing(pair.left(), null, pair.status(), pair.counterpart(), pair.diff(), pair.table()));
                } else {
                    pairings.add(new Pairing(unit.item(), null, alone(Pairing.Status.LEFT_ONLY), -1, ParagraphDiff.alone(
                            unit.isTable() ? "" : unit.text(), true, options.text()),
                            unit.isTable() ? tables.alone(unit, true) : null));
                }
            } else {
                Unit unit = r.get(slot.right());
                if (slot.moved()) {
                    Pairing pair = pair(l.get(slots.get(slot.counterpart()).left()), unit, slot.counterpart());
                    pairings.add(new Pairing(null, pair.right(), pair.status(), pair.counterpart(), pair.diff(), pair.table()));
                } else {
                    pairings.add(new Pairing(null, unit.item(), alone(Pairing.Status.RIGHT_ONLY), -1, ParagraphDiff.alone(
                            unit.isTable() ? "" : unit.text(), false, options.text()),
                            unit.isTable() ? tables.alone(unit, false) : null));
                }
            }
        }
        return new StoryComparison(part, pairings);
    }

    /** A paragraph or table alone counts as text: added, or removed. */
    private Pairing.Status alone(Pairing.Status status) {
        return options.text() ? status : Pairing.Status.SAME;
    }

    /** Two paragraphs, or two tables, compared; a moved pair otherwise the same is shown moved, when the text counts. */
    private Pairing pair(Unit a, Unit b, int counterpart) {
        Pairing.Status status;
        ParagraphDiff diff;
        TableComparison table = null;
        if (a.isTable()) {
            table = tables.compare(a, b);
            diff = tables.properties(a, b);
            status = tables.status(table, diff);
        } else {
            diff = paragraphs.compare(a, b);
            status = Pairing.statusOf(diff);
        }
        if (counterpart >= 0 && status == Pairing.Status.SAME && options.text()) {
            status = Pairing.Status.MOVED;
        }
        return new Pairing(a.item(), b.item(), status, counterpart, diff, table);
    }

    /** The paragraphs to compare; with the blanks left out, empty ones are not. */
    private List<Unit> units(List<Story> stories) {
        List<Unit> units = new ArrayList<>();
        for (Item item : Items.of(stories)) {
            Unit unit = Unit.of(item, options.blanks(), vocabulary);
            if (options.blanks() || !unit.isBlank()) {
                units.add(unit);
            }
        }
        return units;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // The documents themselves

    private List<PropertyDifference> documentDifferences() {
        Differences differences = new Differences(options);
        differences.add(Aspect.TEMPLATE, Said.of("compared.attachedTemplate"), left.template().attached(),
                right.template().attached());
        differences.add(Aspect.TEMPLATE, Said.of("compared.template"), left.template().named(),
                right.template().named());
        differences.add(Aspect.SECTIONS, Said.of("compared.sections"), String.valueOf(sections(left)),
                String.valueOf(sections(right)));
        if (options.has(Aspect.STYLES)) {
            styleDifferences(differences, left.styles(), right.styles());
        }
        return differences.list();
    }

    /** The sections of a body: one, and one more after each paragraph that ends one. */
    private static int sections(WordDocument document) {
        int sections = 1;
        for (Block block : document.body().blocks()) {
            if (block instanceof Paragraph paragraph && paragraph.endsSection()) {
                sections++;
            }
        }
        return sections;
    }

    /** The defaults, then each style by name, as both documents define them: all of it a matter of styles. */
    /** A style one document defines and the other has not. */
    private static final Said DEFINED = Said.of("value.defined");

    private static void styleDifferences(Differences differences, StyleSheet a, StyleSheet b) {
        differences.formattingAs(Aspect.STYLES, name -> Said.of("compared.ofDefaultParagraph", name),
                a.defaultParagraph(), b.defaultParagraph());
        differences.formattingAs(Aspect.STYLES, name -> Said.of("compared.ofDefaultText", name), a.defaultRun(),
                b.defaultRun());
        Map<String, Style> stylesA = byName(a);
        Map<String, Style> stylesB = byName(b);
        TreeSet<String> names = new TreeSet<>(stylesA.keySet());
        names.addAll(stylesB.keySet());
        for (String name : names) {
            Style styleA = stylesA.get(name);
            Style styleB = stylesB.get(name);
            if (styleA == null || styleB == null) {
                differences.add(Aspect.STYLES, Said.of("compared.style", name),
                        styleA == null ? null : DEFINED, styleB == null ? null : DEFINED);
                continue;
            }
            differences.add(Aspect.STYLES, Said.of("compared.styleType", name), styleA.type(), styleB.type());
            differences.add(Aspect.STYLES, Said.of("compared.styleBasedOn", name), a.nameOf(styleA.basedOn()),
                    b.nameOf(styleB.basedOn()));
            differences.formattingAs(Aspect.STYLES, property -> Said.of("compared.ofStyleParagraph", property, name),
                    styleA.paragraph(), styleB.paragraph());
            differences.formattingAs(Aspect.STYLES, property -> Said.of("compared.ofStyleText", property, name),
                    styleA.run(), styleB.run());
        }
    }

    private static Map<String, Style> byName(StyleSheet styles) {
        Map<String, Style> byName = new TreeMap<>();
        for (Style style : styles.styles().values()) {
            byName.putIfAbsent(style.name() == null ? style.id() : style.name(), style);
        }
        return byName;
    }
}
