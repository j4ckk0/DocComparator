package org.jtools.doccomparator.text;

import java.util.ArrayList;
import java.util.List;
import org.jtools.doccomparator.document.Block;
import org.jtools.doccomparator.document.Formatting;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.Run;
import org.jtools.doccomparator.document.Story;
import org.jtools.doccomparator.document.StyleSheet;
import org.jtools.doccomparator.document.Template;
import org.jtools.doccomparator.document.WordDocument;
import org.jtools.doccomparator.i18n.I18n;

/**
 * Reads plain text — pasted, dropped — into a WordDocument, so it compares as a document does: a body alone, a
 * paragraph a line, without style nor formatting, an empty line an empty paragraph.
 */
public final class TextReader {

    private TextReader() {
    }

    /** The text as a document named so; its last line break ends its last paragraph, it does not start one. */
    public static WordDocument read(String name, String text) {
        String[] lines = text.split("\r\n|\r|\n", -1);
        int count = lines.length > 1 && lines[lines.length - 1].isEmpty() ? lines.length - 1 : lines.length;
        List<Block> paragraphs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            List<Run> runs = lines[i].isEmpty() ? List.of() : List.of(new Run(lines[i], null, Formatting.NONE));
            paragraphs.add(new Paragraph(null, Formatting.NONE, 0, null, false, runs));
        }
        return new WordDocument(name, new Story(Story.Kind.BODY, I18n.text("story.body"), paragraphs), List.of(),
                List.of(), StyleSheet.NONE, Template.NONE, false);
    }
}
