package org.jtools.doccomparator.compare;

import static org.jtools.doccomparator.compare.Paragraphs.paragraph;
import static org.jtools.doccomparator.compare.Paragraphs.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.jtools.doccomparator.compare.CompareOptions.Aspect;
import java.util.Map;
import org.jtools.doccomparator.document.Formatting;
import org.jtools.doccomparator.document.ListItem;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.Picture;
import org.jtools.doccomparator.document.Style;
import org.jtools.doccomparator.document.StyleSheet;
import org.junit.jupiter.api.Test;

class ParagraphComparerTest {

    @Test
    void aReplacedWordIsChangedOnBothSides() {
        ParagraphDiff diff = compare("the cat sat down", "the dog sat down");
        assertEquals("the |{cat}| sat down", marked("the cat sat down", diff.left()));
        assertEquals("the |{dog}| sat down", marked("the dog sat down", diff.right()));
        assertTrue(diff.textDiffers());
    }

    @Test
    void addedWordsAreOnlyOnTheirSideAndMarkedWhereTheyAreMissing() {
        ParagraphDiff diff = compare("Restart the pump and check.", "Restart the pump, wait two minutes and check.");
        assertEquals("Restart the pump|^| and check.", marked("Restart the pump and check.", diff.left()));
        assertEquals("Restart the pump|[, wait two minutes]| and check.",
                marked("Restart the pump, wait two minutes and check.", diff.right()));
    }

    @Test
    void aWordCloseToTheOtherIsComparedCharacterByCharacter() {
        ParagraphDiff diff = compare("the oil colour", "the oil color");
        assertEquals("the oil colo|[u]|r", marked("the oil colour", diff.left()));
        assertEquals("the oil colo|^|r", marked("the oil color", diff.right()));
    }

    @Test
    void numbersAndWordsFarApartAreChangedWhole() {
        assertEquals("in |{2025}", marked("in 2025", compare("in 2025", "in 2026").left()));
        assertEquals("wait |{ten}| minutes", marked("wait ten minutes", compare("wait ten minutes", "wait fifteen minutes").left()));
    }

    @Test
    void blanksLeftOutDifferWithoutCounting() {
        String left = "covered by this document; they are described";
        String right = "covered by this document;  they are described ";
        ParagraphDiff diff = compare(left, right);
        assertFalse(diff.textDiffers());
        assertEquals(right, marked(right, diff.right()), "nothing marked");
        assertFalse(compare("foo, bar", "foo,bar").textDiffers(), "a blank added is no difference either");
        assertFalse(compare("tighten to 25 N", "tighten to 25 N").textDiffers(), "nor a no-break space");
    }

    @Test
    void blanksComparedCount() {
        CompareOptions blanks = Paragraphs.FULL.with(Aspect.BLANKS, true);
        assertTrue(compare(blanks, paragraph("one two"), paragraph("one  two")).textDiffers());
        assertTrue(compare(blanks, paragraph("one two"), paragraph("one two ")).textDiffers());
    }

    @Test
    void theFormattingOfTheSameTextIsComparedWithTheStyles() {
        Paragraph bold = paragraph(null, 0, null, run("A pressure "), run("must", "b"), run(" be reported."));
        Paragraph underlined = paragraph(null, 0, null, run("A pressure "), run("must", "u"), run(" be reported."));
        ParagraphDiff diff = compare(Paragraphs.FULL, bold, underlined);
        assertFalse(diff.textDiffers());
        assertTrue(diff.otherwiseDiffers());
        assertEquals(List.of(new FormatSpan(11, 15, List.of(new PropertyDifference(Aspect.CHARACTERS, "bold", "true", "(none)"),
                new PropertyDifference(Aspect.CHARACTERS, "underline", "(none)", "true")))), diff.leftFormatting());
        assertEquals(diff.leftFormatting(), diff.rightFormatting());

        assertFalse(compare(Paragraphs.FULL.with(Aspect.CHARACTERS, false), bold, underlined).otherwiseDiffers());
    }

    @Test
    void stylesAreComparedByName() {
        StyleSheet english = styles("Heading1", "heading 1");
        StyleSheet french = styles("Titre1", "heading 1");
        ParagraphComparer comparer = new ParagraphComparer(Paragraphs.FULL, new Vocabulary(), english, french);
        Vocabulary vocabulary = new Vocabulary();
        ParagraphDiff diff = comparer.compare(
                Paragraphs.unit(paragraph("Heading1", 1, null, run("Scope")), false, vocabulary),
                Paragraphs.unit(paragraph("Titre1", 1, null, run("Scope")), false, vocabulary));
        assertEquals(List.of(), diff.properties());
    }

    @Test
    void aParagraphWithoutAStyleIsLaidOutByTheDefaultOne() {
        StyleSheet tight = normal(Map.of("spacing.after", "0"));
        StyleSheet airy = normal(Map.of("spacing.after", "240"));
        Vocabulary vocabulary = new Vocabulary();
        ParagraphDiff diff = new ParagraphComparer(CompareOptions.TEXT_ONLY.with(Aspect.SPACING, true), vocabulary,
                tight, airy).compare(Paragraphs.unit(paragraph("Plain text."), false, vocabulary),
                Paragraphs.unit(paragraph("Plain text."), false, vocabulary));
        assertEquals(List.of(new PropertyDifference(Aspect.SPACING, "spacing after", "0", "240")), diff.properties());
    }

    private static StyleSheet normal(Map<String, String> paragraph) {
        return new StyleSheet(Map.of("Normal", new Style("Normal", "Normal", "paragraph", null,
                new Formatting(paragraph), Formatting.NONE)), Formatting.NONE, Formatting.NONE, "Normal");
    }

    /** A picture added: only on its side; one swapped: changed, whatever the words beside it do. */
    @Test
    void picturesDifferByTheirImages() {
        Paragraph none = paragraph(null, 0, null, run("See the pump."));
        Paragraph one = paragraph(null, 0, null, run("See the pump."), Paragraphs.picture("pump"));
        ParagraphDiff added = compare(Paragraphs.FULL, none, one);
        assertEquals("See the pump.|[\uFFFC]", marked(one.text(), added.right()));

        Paragraph colour = paragraph(null, 0, null, run("The colour "), Paragraphs.picture("green"));
        Paragraph color = paragraph(null, 0, null, run("The color "), Paragraphs.picture("red"));
        ParagraphDiff swapped = compare(Paragraphs.FULL, colour, color);
        int at = color.text().indexOf(Picture.IN_TEXT);
        assertTrue(swapped.right().stream().anyMatch(span -> span.start() <= at && at < span.end() && span.differs()),
                "the picture swapped is marked, never matched character by character: "
                        + marked(color.text(), swapped.right()));

        Paragraph same = paragraph(null, 0, null, run("The colour "), Paragraphs.picture("green"));
        Paragraph wordChanged = paragraph(null, 0, null, run("The color "), Paragraphs.picture("green"));
        String kept = marked(wordChanged.text(), compare(Paragraphs.FULL, same, wordChanged).right());
        assertTrue(kept.endsWith(" \uFFFC"), "the same picture beside a word changed is not marked: " + kept);
    }

    @Test
    void theStructureIsCompared() {
        Paragraph heading = paragraph(null, 1, null, run("Records"));
        Paragraph item = paragraph(null, 0, new ListItem("4", 0, "decimal", "%1."), run("Records"));
        ParagraphDiff diff = compare(Paragraphs.FULL.with(Aspect.STYLES, false), heading, item);
        assertEquals(List.of(new PropertyDifference(Aspect.HEADINGS, "heading", "heading 1", "(none)"),
                new PropertyDifference(Aspect.LISTS, "list", "(none)", "decimal \"%1.\", level 1")), diff.properties());
        assertFalse(compare(Paragraphs.FULL.with(Aspect.HEADINGS, false).with(Aspect.LISTS, false), heading, item)
                .otherwiseDiffers());
    }

    private static StyleSheet styles(String id, String name) {
        return new StyleSheet(Map.of(id, new Style(id, name, "paragraph", null, Formatting.NONE, Formatting.NONE)),
                Formatting.NONE, Formatting.NONE);
    }

    private static ParagraphDiff compare(String left, String right) {
        return compare(Paragraphs.FULL, paragraph(left), paragraph(right));
    }

    private static ParagraphDiff compare(CompareOptions options, Paragraph left, Paragraph right) {
        Vocabulary vocabulary = new Vocabulary();
        return new ParagraphComparer(options, vocabulary, StyleSheet.NONE, StyleSheet.NONE).compare(
                Paragraphs.unit(left, options.blanks(), vocabulary), Paragraphs.unit(right, options.blanks(), vocabulary));
    }

    /** The text with its differences marked: {changed}, [only here], ^ absent, | between spans. */
    static String marked(String text, List<Span> spans) {
        StringBuilder marked = new StringBuilder();
        Span.Kind previous = null;
        for (Span span : spans) {
            String part = text.substring(span.start(), span.end());
            if (previous != null && (previous != Span.Kind.SAME || span.kind() != Span.Kind.SAME)) {
                marked.append('|');
            }
            marked.append(switch (span.kind()) {
                case SAME -> part;
                case CHANGED -> "{" + part + "}";
                case ONLY_HERE -> "[" + part + "]";
                case ABSENT -> "^";
            });
            previous = span.kind();
        }
        int covered = spans.isEmpty() ? 0 : spans.getLast().end();
        assertEquals(text.length(), covered, "the spans cover the text");
        return marked.toString();
    }
}
