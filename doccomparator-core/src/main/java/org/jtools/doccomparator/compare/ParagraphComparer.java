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
import java.util.Objects;
import java.util.TreeSet;
import org.jtools.doccomparator.compare.CompareOptions.Aspect;
import org.jtools.doccomparator.document.Formatting;
import org.jtools.doccomparator.document.ListItem;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.Picture;
import org.jtools.doccomparator.document.Run;
import org.jtools.doccomparator.document.StyleSheet;
import java.util.function.UnaryOperator;

/**
 * Step two of a comparison: how two paired paragraphs differ. Their words first — the fewest words removed and added
 * — then, where a word or two was replaced by one close to it ("colour", "color"), the characters that differ.
 */
final class ParagraphComparer {

    /** The longest replaced text, in characters, whose characters are compared one by one. */
    static final int LONGEST_REFINED = 200;
    /** The least share of characters a replaced text and its replacement keep for the characters to be shown… */
    static final double REFINED_SIMILAR = 2.0 / 3;
    /** …in at most this many differences: more reads as confetti; the whole replacement shows better. */
    static final int MOST_REFINED_PIECES = 2;
    /** Between two changes, this many tokens or fewer, blanks and punctuation only, are part of the change. */
    static final int ABSORBED_TOKENS = 2;

    /** The token code of any blank, when blanks are left out. */
    private static final int BLANK = 0;

    private final CompareOptions options;
    private final Vocabulary vocabulary;
    private final StyleSheet leftStyles;
    private final StyleSheet rightStyles;

    ParagraphComparer(CompareOptions options, Vocabulary vocabulary, StyleSheet leftStyles, StyleSheet rightStyles) {
        this.options = options;
        this.vocabulary = vocabulary;
        this.leftStyles = leftStyles;
        this.rightStyles = rightStyles;
    }

    ParagraphDifference compare(Unit a, Unit b) {
        TextComparison comparison = new TextComparison(a, b);
        comparison.compareText();
        return new ParagraphDifference(counted(comparison.left.spans), counted(comparison.right.spans),
                comparison.left.formatting, comparison.right.formatting,
                properties(a.item().paragraph(), b.item().paragraph(), a.item(), b.item()));
    }

    /** The spans as the options compare them: the text left out, nothing in it differs — the whole of it the same. */
    private List<TextSpan> counted(List<TextSpan> spans) {
        if (options.text() || spans.isEmpty()) {
            return spans;
        }
        return List.of(new TextSpan(0, spans.getLast().end(), TextSpan.Kind.SAME));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // The text

    /** The spans of one side as they are found, in order, those alike joined. */
    private static final class Side {
        final List<TextSpan> spans = new ArrayList<>();
        final List<FormatSpan> formatting = new ArrayList<>();

        void add(int start, int end, TextSpan.Kind kind) {
            if (start == end && kind != TextSpan.Kind.ABSENT) {
                return;
            }
            if (!spans.isEmpty()) {
                TextSpan last = spans.getLast();
                if (last.kind() == kind && last.end() == start && kind != TextSpan.Kind.ABSENT) {
                    spans.set(spans.size() - 1, new TextSpan(last.start(), end, kind));
                    return;
                }
            }
            spans.add(new TextSpan(start, end, kind));
        }

        void addFormatting(int start, int end, List<PropertyDifference> differences) {
            if (!formatting.isEmpty()) {
                FormatSpan last = formatting.getLast();
                if (last.end() == start && last.differences().equals(differences)) {
                    formatting.set(formatting.size() - 1, new FormatSpan(last.start(), end, differences));
                    return;
                }
            }
            formatting.add(new FormatSpan(start, end, differences));
        }
    }

    /** Two runs' formatting, as a key: runs formatted alike are compared once. */
    private record RunPair(String styleA, Formatting formattingA, String styleB, Formatting formattingB) {
    }

    private final class TextComparison {
        final Unit a;
        final Unit b;
        final Side left = new Side();
        final Side right = new Side();
        final Map<RunPair, List<PropertyDifference>> runDifferences = new HashMap<>();

        TextComparison(Unit a, Unit b) {
            this.a = a;
            this.b = b;
        }

        void compareText() {
            List<Token> ta = a.tokens();
            List<Token> tb = b.tokens();
            int[] rangeA = comparedRange(ta);
            int[] rangeB = comparedRange(tb);
            edge(position(ta, 0, a.text()), position(ta, rangeA[0], a.text()),
                    position(tb, 0, b.text()), position(tb, rangeB[0], b.text()));

            List<EditScript.Edit> edits = absorbSmallEqualities(
                    EditScript.of(codes(ta, rangeA, a), codes(tb, rangeB, b)), ta, rangeA[0], a.text());
            for (EditScript.Edit edit : edits) {
                int fromA = rangeA[0] + edit.fromA();
                int toA = rangeA[0] + edit.toA();
                int fromB = rangeB[0] + edit.fromB();
                int toB = rangeB[0] + edit.toB();
                if (edit.equal()) {
                    for (int k = 0; k < toA - fromA; k++) {
                        Token x = ta.get(fromA + k);
                        Token y = tb.get(fromB + k);
                        if (x.text(a.text()).equals(y.text(b.text()))) {
                            same(x.start(), x.end(), y.start(), y.end());
                        } else {   // two different blanks, coded alike: the blanks are left out
                            left.add(x.start(), x.end(), TextSpan.Kind.SAME);
                            right.add(y.start(), y.end(), TextSpan.Kind.SAME);
                        }
                    }
                } else {
                    replaced(position(ta, fromA, a.text()), position(ta, toA, a.text()),
                            position(tb, fromB, b.text()), position(tb, toB, b.text()), true);
                }
            }
            edge(position(ta, rangeA[1], a.text()), a.text().length(),
                    position(tb, rangeB[1], b.text()), b.text().length());
        }

        /**
         * The tokens compared, [from, to): all of them, or with the blanks left out, those between the first and last
         * that are not blanks.
         */
        private int[] comparedRange(List<Token> tokens) {
            int from = 0;
            int to = tokens.size();
            if (!options.blanks()) {
                while (from < to && tokens.get(from).type() == Token.Type.BLANK) {
                    from++;
                }
                while (to > from && tokens.get(to - 1).type() == Token.Type.BLANK) {
                    to--;
                }
            }
            return new int[] {from, to};
        }

        /**
         * Each token's code: its text's; a blank's, the same for all when blanks are left out; a picture's, its
         * image's.
         */
        private int[] codes(List<Token> tokens, int[] range, Unit unit) {
            int[] codes = new int[range[1] - range[0]];
            for (int i = 0; i < codes.length; i++) {
                Token token = tokens.get(range[0] + i);
                String picture = unit.pictures().get(token.start());
                codes[i] = !options.blanks() && token.type() == Token.Type.BLANK ? BLANK
                        : vocabulary.code(picture != null ? Picture.IN_TEXT + picture : token.text(unit.text()));
            }
            return codes;
        }

        /** Blanks before the first token compared, or after the last: left out of the comparison, the same. */
        private void edge(int startA, int endA, int startB, int endB) {
            left.add(startA, endA, TextSpan.Kind.SAME);
            right.add(startB, endB, TextSpan.Kind.SAME);
        }

        /**
         * Text a[startA, endA) replaced by b[startB, endB), either of which may be empty. Refined, when both are short
         * and close, into the characters that differ.
         */
        private void replaced(int startA, int endA, int startB, int endB, boolean refine) {
            String x = a.text().substring(startA, endA);
            String y = b.text().substring(startB, endB);
            boolean blanksOnly = !options.blanks() && isBlank(x) && isBlank(y);
            if (blanksOnly) {   // blanks left out: no difference
                left.add(startA, endA, TextSpan.Kind.SAME);
                right.add(startB, endB, TextSpan.Kind.SAME);
            } else if (x.isEmpty()) {
                left.add(startA, startA, TextSpan.Kind.ABSENT);
                right.add(startB, endB, TextSpan.Kind.ONLY_HERE);
            } else if (y.isEmpty()) {
                left.add(startA, endA, TextSpan.Kind.ONLY_HERE);
                right.add(startB, startB, TextSpan.Kind.ABSENT);
            } else if (!refine || !refined(x, startA, y, startB)) {
                left.add(startA, endA, TextSpan.Kind.CHANGED);
                right.add(startB, endB, TextSpan.Kind.CHANGED);
            }
        }

        /** The characters of x and y compared one by one, if they are close enough to show it so; false if not. */
        private boolean refined(String x, int startA, String y, int startB) {
            if (x.length() > LONGEST_REFINED || y.length() > LONGEST_REFINED || !hasLetter(x) || !hasLetter(y)
                    || x.indexOf(Picture.IN_TEXT) >= 0 || y.indexOf(Picture.IN_TEXT) >= 0) {
                // too long; a number, which reads better whole ("2025" → "2026"); a picture, told by its image only
                return false;
            }
            int[] cx = x.codePoints().toArray();
            int[] cy = y.codePoints().toArray();
            List<EditScript.Edit> edits = EditScript.of(cx, cy);
            int kept = 0;
            int pieces = 0;
            for (EditScript.Edit edit : edits) {
                if (edit.equal()) {
                    kept += edit.toA() - edit.fromA();
                } else {
                    pieces++;
                }
            }
            if (pieces > MOST_REFINED_PIECES || 2.0 * kept < REFINED_SIMILAR * (cx.length + cy.length)) {
                return false;
            }
            int[] offsetsX = offsets(x, cx.length);
            int[] offsetsY = offsets(y, cy.length);
            for (EditScript.Edit edit : edits) {
                int fromA = startA + offsetsX[edit.fromA()];
                int toA = startA + offsetsX[edit.toA()];
                int fromB = startB + offsetsY[edit.fromB()];
                int toB = startB + offsetsY[edit.toB()];
                if (edit.equal()) {
                    same(fromA, toA, fromB, toB);
                } else {
                    replaced(fromA, toA, fromB, toB, false);
                }
            }
            return true;
        }

        /** The same text on both sides: then, with the styles compared, its formatting, character by character. */
        private void same(int startA, int endA, int startB, int endB) {
            left.add(startA, endA, TextSpan.Kind.SAME);
            right.add(startB, endB, TextSpan.Kind.SAME);
            if (!options.has(Aspect.STYLES) && !options.has(Aspect.CHARACTERS) || endA - startA != endB - startB) {
                return;
            }
            for (int k = 0; k < endA - startA; k++) {
                Run runA = a.runAt(startA + k);
                Run runB = b.runAt(startB + k);
                List<PropertyDifference> differences = runDifferences.computeIfAbsent(
                        new RunPair(runA.styleId(), runA.formatting(), runB.styleId(), runB.formatting()),
                        _ -> runDifferences(runA, runB));
                if (!differences.isEmpty()) {
                    left.addFormatting(startA + k, startA + k + 1, differences);
                    right.addFormatting(startB + k, startB + k + 1, differences);
                }
            }
        }
    }

    /**
     * Between two changes, a stretch of a blank or a mark or two is taken into the change: "housing and hoses" →
     * "housing, hoses" reads better as one change than as pieces around a shared space.
     */
    private static List<EditScript.Edit> absorbSmallEqualities(List<EditScript.Edit> edits, List<Token> tokensA,
                                                               int offsetA, String textA) {
        List<EditScript.Edit> result = new ArrayList<>(edits);
        boolean absorbed = true;
        while (absorbed) {
            absorbed = false;
            for (int i = 1; i < result.size() - 1; i++) {
                EditScript.Edit edit = result.get(i);
                if (edit.equal() && edit.toA() - edit.fromA() <= ABSORBED_TOKENS
                        && tokensA.subList(offsetA + edit.fromA(), offsetA + edit.toA()).stream()
                                .noneMatch(token -> token.type() == Token.Type.WORD || isPicture(token, textA))) {
                    EditScript.Edit before = result.get(i - 1);
                    EditScript.Edit after = result.get(i + 1);
                    result.subList(i - 1, i + 2).clear();
                    result.add(i - 1,
                            new EditScript.Edit(false, before.fromA(), after.toA(), before.fromB(), after.toB()));
                    absorbed = true;
                    break;
                }
            }
        }
        return result;
    }

    /** A picture's character: never absorbed into a change beside it, since a picture differs by its image. */
    private static boolean isPicture(Token token, String text) {
        return token.end() - token.start() == 1 && text.charAt(token.start()) == Picture.IN_TEXT;
    }

    /** Where a token starts in the text; the text's end past the last token. */
    private static int position(List<Token> tokens, int index, String text) {
        return index < tokens.size() ? tokens.get(index).start() : text.length();
    }

    /** The offset in the text of each code point, and of its end. */
    private static int[] offsets(String text, int codePoints) {
        int[] offsets = new int[codePoints + 1];
        for (int i = 0, offset = 0; i < codePoints; i++) {
            offsets[i] = offset;
            offset += Character.charCount(text.codePointAt(offset));
            offsets[i + 1] = offset;
        }
        return offsets;
    }

    private static boolean hasLetter(String text) {
        return text.codePoints().anyMatch(Character::isLetter);
    }

    private static boolean isBlank(String text) {
        return text.codePoints().allMatch(Token::isBlank);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Formatting and properties

    /** How two runs are formatted otherwise, as the options compare it: their character style, their own properties. */
    private List<PropertyDifference> runDifferences(Run a, Run b) {
        Differences differences = new Differences(options);
        differences.add(Aspect.STYLES, Phrase.of("compared.characterStyle"), leftStyles.nameOf(a.styleId()),
                rightStyles.nameOf(b.styleId()));
        differences.formatting(Aspects.Source.RUN, a.formatting(), b.formatting());
        return differences.list();
    }

    /**
     * What else than their text differs between two paragraphs, as the options compare it. Indentation, spacing and
     * alignment as they apply, through the styles; the rest of the paragraph's formatting as it sets it itself.
     */
    private List<PropertyDifference> properties(Paragraph a, Paragraph b, ComparedBlock itemA, ComparedBlock itemB) {
        Differences differences = new Differences(options);
        differences.add(Aspect.SECTIONS, Phrase.of("compared.story"),
                Phrase.of("value.story." + itemA.story().kind().name()),
                Phrase.of("value.story." + itemB.story().kind().name()));
        differences.add(Aspect.HEADINGS, Phrase.of("compared.heading"), heading(a.headingLevel()),
                heading(b.headingLevel()));
        differences.add(Aspect.LISTS, Phrase.of("compared.list"), list(a.listItem()), list(b.listItem()));
        differences.add(Aspect.SECTIONS, Phrase.of("compared.place"), itemA.placeInStructure(),
                itemB.placeInStructure());
        differences.add(Aspect.SECTIONS, Phrase.of("compared.section"), a.endsSection() ? ENDS_HERE : null,
                b.endsSection() ? ENDS_HERE : null);
        differences.add(Aspect.STYLES, Phrase.of("compared.paragraphStyle"),
                paragraphStyleName(leftStyles, a.styleId()), paragraphStyleName(rightStyles, b.styleId()));

        Formatting appliedA = leftStyles.effectiveParagraph(a);
        Formatting appliedB = rightStyles.effectiveParagraph(b);
        differences.formatting(Aspects.Source.PARAGRAPH, UnaryOperator.identity(), appliedA, appliedB,
                aspect -> aspect == Aspect.INDENTATION || aspect == Aspect.SPACING || aspect == Aspect.ALIGNMENT);
        differences.formatting(Aspects.Source.PARAGRAPH, UnaryOperator.identity(), a.formatting(), b.formatting(),
                aspect -> aspect == Aspect.PARAGRAPHS);   // headings, lists, styles: said above
        return differences.list();
    }

    /** A paragraph without a style is in the default one, which Word names "Normal". */
    private static String paragraphStyleName(StyleSheet styles, String id) {
        return Objects.requireNonNullElse(styles.nameOf(styles.paragraphStyle(id)), "Normal");
    }

    /** Where a section ends: on this paragraph. */
    private static final Phrase ENDS_HERE = Phrase.of("value.endsHere");

    private static Phrase heading(int level) {
        return level == 0 ? null : Phrase.of("value.heading", level);
    }

    private static Phrase list(ListItem item) {
        if (item == null) {
            return null;
        }
        Phrase format = item.numberFormat() == null ? Phrase.of("value.list") : Phrase.verbatim(item.numberFormat());
        String text = item.levelText() == null || item.numberFormat() != null && item.numberFormat().equals("bullet")
                ? "" : " \"" + item.levelText() + "\"";
        return Phrase.of("value.listLevel", Phrase.joined(format, Phrase.verbatim(text)), item.level() + 1);
    }
}
