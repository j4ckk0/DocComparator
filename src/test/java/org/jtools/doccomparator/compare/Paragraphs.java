package org.jtools.doccomparator.compare;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jtools.doccomparator.document.Formatting;
import org.jtools.doccomparator.document.ListItem;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.Run;
import org.jtools.doccomparator.document.Story;

/** Paragraphs and units for a test, without a document. */
final class Paragraphs {

    private Paragraphs() {
    }

    static Paragraph paragraph(String text) {
        return paragraph(null, 0, null, new Run(text, null, Formatting.NONE));
    }

    static Paragraph paragraph(String styleId, int headingLevel, ListItem listItem, Run... runs) {
        return new Paragraph(styleId, Formatting.NONE, headingLevel, listItem, false, List.of(runs));
    }

    static Run run(String text, String... properties) {
        Map<String, String> values = new TreeMap<>();
        for (String property : properties) {
            values.put(property, "true");
        }
        return new Run(text, null, new Formatting(values));
    }

    static Item item(Paragraph paragraph) {
        return new Item(Story.Kind.BODY, "Body", List.of(), paragraph);
    }

    static Unit unit(Paragraph paragraph, boolean blanks, Vocabulary vocabulary) {
        return Unit.of(item(paragraph), blanks, vocabulary);
    }

    static List<Unit> units(Vocabulary vocabulary, String... texts) {
        List<Unit> units = new ArrayList<>();
        for (String text : texts) {
            units.add(unit(paragraph(text), false, vocabulary));
        }
        return units;
    }
}
