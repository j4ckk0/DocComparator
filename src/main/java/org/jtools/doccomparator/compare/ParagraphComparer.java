package org.jtools.doccomparator.compare;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeSet;
import org.jtools.doccomparator.compare.CompareOptions.Aspect;
import org.jtools.doccomparator.document.Formatting;
import org.jtools.doccomparator.document.ListItem;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.Run;
import org.jtools.doccomparator.document.Style;
import org.jtools.doccomparator.document.StyleSheet;

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

    ParagraphDiff compare(Unit a, Unit b) {
        TextComparison comparison = new TextComparison(a, b);
        comparison.compareText();
        return new ParagraphDiff(counted(comparison.left.spans), counted(comparison.right.spans),
                comparison.left.formatting, comparison.right.formatting,
                properties(a.item().paragraph(), b.item().paragraph(), a.item(), b.item()));
    }

    /** The spans as the options compare them: the text left out, nothing in it differs — the whole of it the same. */
    private List<Span> counted(List<Span> spans) {
        if (options.text() || spans.isEmpty()) {
            return spans;
        }
        return List.of(new Span(0, spans.getLast().end(), Span.Kind.SAME));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // The text

    /** The spans of one side as they are found, in order, those alike joined. */
    private static final class Side {
        final List<Span> spans = new ArrayList<>();
        final List<FormatSpan> formatting = new ArrayList<>();

        void add(int start, int end, Span.Kind kind) {
            if (start == end && kind != Span.Kind.ABSENT) {
                return;
            }
            if (!spans.isEmpty()) {
                Span last = spans.getLast();
                if (last.kind() == kind && last.end() == start && kind != Span.Kind.ABSENT) {
                    spans.set(spans.size() - 1, new Span(last.start(), end, kind));
                    return;
                }
            }
            spans.add(new Span(start, end, kind));
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

    /** What the formatting of a run is: two runs alike differ alike from a third. */
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

            List<Diff.Edit> edits = absorbSmallEqualities(
                    Diff.of(codes(ta, rangeA, a.text()), codes(tb, rangeB, b.text())), ta, rangeA[0]);
            for (Diff.Edit edit : edits) {
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
                        } else {   // blanks, which the code took as the same: left out, they are
                            left.add(x.start(), x.end(), Span.Kind.SAME);
                            right.add(y.start(), y.end(), Span.Kind.SAME);
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

        /** The tokens compared, [from, to): all of them, or with the blanks left out, those between the first and last that are not blanks. */
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

        private int[] codes(List<Token> tokens, int[] range, String text) {
            int[] codes = new int[range[1] - range[0]];
            for (int i = 0; i < codes.length; i++) {
                Token token = tokens.get(range[0] + i);
                codes[i] = !options.blanks() && token.type() == Token.Type.BLANK ? BLANK
                        : vocabulary.code(token.text(text));
            }
            return codes;
        }

        /** Blanks before the first token compared, or after the last: left out of the comparison, the same. */
        private void edge(int startA, int endA, int startB, int endB) {
            left.add(startA, endA, Span.Kind.SAME);
            right.add(startB, endB, Span.Kind.SAME);
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
                left.add(startA, endA, Span.Kind.SAME);
                right.add(startB, endB, Span.Kind.SAME);
            } else if (x.isEmpty()) {
                left.add(startA, startA, Span.Kind.ABSENT);
                right.add(startB, endB, Span.Kind.ONLY_HERE);
            } else if (y.isEmpty()) {
                left.add(startA, endA, Span.Kind.ONLY_HERE);
                right.add(startB, startB, Span.Kind.ABSENT);
            } else if (!refine || !refined(x, startA, y, startB)) {
                left.add(startA, endA, Span.Kind.CHANGED);
                right.add(startB, endB, Span.Kind.CHANGED);
            }
        }

        /** The characters of x and y compared one by one, if they are close enough to show it so; false if not. */
        private boolean refined(String x, int startA, String y, int startB) {
            if (x.length() > LONGEST_REFINED || y.length() > LONGEST_REFINED || !hasLetter(x) || !hasLetter(y)) {
                return false;   // too long; or a number, which reads better whole: "2025" → "2026"
            }
            int[] cx = x.codePoints().toArray();
            int[] cy = y.codePoints().toArray();
            List<Diff.Edit> edits = Diff.of(cx, cy);
            int kept = 0;
            int pieces = 0;
            for (Diff.Edit edit : edits) {
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
            for (Diff.Edit edit : edits) {
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
            left.add(startA, endA, Span.Kind.SAME);
            right.add(startB, endB, Span.Kind.SAME);
            if (!options.has(Aspect.STYLES) && !options.has(Aspect.CHARACTERS) || endA - startA != endB - startB) {
                return;
            }
            for (int k = 0; k < endA - startA; k++) {
                Run runA = a.runAt(startA + k);
                Run runB = b.runAt(startB + k);
                List<PropertyDifference> differences = runDifferences.computeIfAbsent(
                        new RunPair(runA.styleId(), runA.formatting(), runB.styleId(), runB.formatting()),
                        p -> runDifferences(runA, runB));
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
    private static List<Diff.Edit> absorbSmallEqualities(List<Diff.Edit> edits, List<Token> tokensA, int offsetA) {
        List<Diff.Edit> result = new ArrayList<>(edits);
        boolean absorbed = true;
        while (absorbed) {
            absorbed = false;
            for (int i = 1; i < result.size() - 1; i++) {
                Diff.Edit edit = result.get(i);
                if (edit.equal() && edit.toA() - edit.fromA() <= ABSORBED_TOKENS
                        && tokensA.subList(offsetA + edit.fromA(), offsetA + edit.toA()).stream()
                                .noneMatch(token -> token.type() == Token.Type.WORD)) {
                    Diff.Edit before = result.get(i - 1);
                    Diff.Edit after = result.get(i + 1);
                    result.subList(i - 1, i + 2).clear();
                    result.add(i - 1, new Diff.Edit(false, before.fromA(), after.toA(), before.fromB(), after.toB()));
                    absorbed = true;
                    break;
                }
            }
        }
        return result;
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
        differences.add(Aspect.STYLES, "character style", styleName(leftStyles, a.styleId()),
                styleName(rightStyles, b.styleId()));
        differences.formatting(Aspects.Source.RUN, "", a.formatting(), b.formatting());
        return differences.list();
    }

    /**
     * What else than their text differs between two paragraphs, as the options compare it. Indentation, spacing and
     * alignment as they apply, through the styles; the rest of the paragraph's formatting as it sets it itself.
     */
    private List<PropertyDifference> properties(Paragraph a, Paragraph b, Item itemA, Item itemB) {
        Differences differences = new Differences(options);
        differences.add(Aspect.SECTIONS, "story", itemA.story().name().toLowerCase(Locale.ROOT),
                itemB.story().name().toLowerCase(Locale.ROOT));
        differences.add(Aspect.HEADINGS, "heading", heading(a.headingLevel()), heading(b.headingLevel()));
        differences.add(Aspect.LISTS, "list", list(a.listItem()), list(b.listItem()));
        differences.add(Aspect.SECTIONS, "place", itemA.placeInStructure(), itemB.placeInStructure());
        differences.add(Aspect.SECTIONS, "section", a.endsSection() ? "ends here" : null,
                b.endsSection() ? "ends here" : null);
        differences.add(Aspect.STYLES, "paragraph style", paragraphStyleName(leftStyles, a.styleId()),
                paragraphStyleName(rightStyles, b.styleId()));

        Formatting appliedA = leftStyles.effectiveParagraph(a);
        Formatting appliedB = rightStyles.effectiveParagraph(b);
        differences.formatting(Aspects.Source.PARAGRAPH, "", appliedA, appliedB,
                aspect -> aspect == Aspect.INDENTATION || aspect == Aspect.SPACING || aspect == Aspect.ALIGNMENT);
        differences.formatting(Aspects.Source.PARAGRAPH, "", a.formatting(), b.formatting(),
                aspect -> aspect == Aspect.PARAGRAPHS);   // headings, lists, styles: said above
        return differences.list();
    }

    String leftStyleName(String id) {
        return styleName(leftStyles, id);
    }

    String rightStyleName(String id) {
        return styleName(rightStyles, id);
    }

    /** A style by its name, which is the same in two documents when its id may not be ("Heading1", "Titre1"). */
    static String styleName(StyleSheet styles, String id) {
        if (id == null) {
            return null;
        }
        Style style = styles.style(id);
        return style == null || style.name() == null ? id : style.name();
    }

    /** A paragraph without a style is in the default one, which Word names "Normal". */
    private static String paragraphStyleName(StyleSheet styles, String id) {
        return Objects.requireNonNullElse(styleName(styles, styles.paragraphStyle(id)), "Normal");
    }

    private static String heading(int level) {
        return level == 0 ? null : "heading " + level;
    }

    private static String list(ListItem item) {
        if (item == null) {
            return null;
        }
        String format = item.numberFormat() == null ? "list" : item.numberFormat();
        String text = item.levelText() == null || item.numberFormat() != null && item.numberFormat().equals("bullet")
                ? "" : " \"" + item.levelText() + "\"";
        return format + text + ", level " + (item.level() + 1);
    }
}
