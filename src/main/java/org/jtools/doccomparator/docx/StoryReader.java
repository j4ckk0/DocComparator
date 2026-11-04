package org.jtools.doccomparator.docx;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import org.jtools.doccomparator.document.Block;
import org.jtools.doccomparator.document.Formatting;
import org.jtools.doccomparator.document.ListItem;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.Run;
import org.jtools.doccomparator.document.Story;
import org.jtools.doccomparator.document.StyleSheet;
import org.jtools.doccomparator.document.Table;
import org.jtools.doccomparator.document.TextBox;

/**
 * The blocks of a story — the body, a header, a footnote… — as the reader of the comparison wants them: the text as
 * shown, tracked changes accepted (insertions in, deletions out), a field's result rather than its code, a text box
 * once (Word writes it twice, for older versions), headings and lists resolved through the styles.
 *
 * One reader serves all the stories of a document: it gathers what they say of the whole — the tracked changes, the
 * sections and the headers and footers they refer to.
 */
final class StoryReader {

    /** A section's header or footer, as the body refers to it: its kind, its type ("default", "first", "even"). */
    record SectionReference(int section, Story.Kind kind, String type, String relationshipId) {
    }

    /** How the reader marks a run of an equation: a run's properties come from w:rPr, an equation's from none. */
    private static final Formatting EQUATION = new Formatting(Map.of("oMath", "true"));

    private final StyleSheet styles;
    private final Numbering numbering;
    private boolean trackedChanges;
    private int sections;
    private final List<SectionReference> sectionReferences = new ArrayList<>();

    StoryReader(StyleSheet styles, Numbering numbering) {
        this.styles = styles;
        this.numbering = numbering;
    }

    boolean trackedChanges() {
        return trackedChanges;
    }

    /** The sections the body has closed so far: one per w:sectPr. */
    int sections() {
        return sections;
    }

    List<SectionReference> sectionReferences() {
        return List.copyOf(sectionReferences);
    }

    /** The blocks of the element the cursor is on (w:body, w:hdr, w:tc, w:txbxContent…), consumed. */
    List<Block> readBlocks(XmlCursor xml) throws DocxException {
        List<Block> blocks = new ArrayList<>();
        xml.forChildren(child -> readBlock(child, blocks));
        return blocks;
    }

    /** The footnotes or endnotes of footnotes.xml or endnotes.xml, numbered as they come; separators are not notes. */
    List<Story> readNotes(XmlCursor xml, Story.Kind kind, String noun) throws DocxException {
        List<Story> notes = new ArrayList<>();
        while (xml.nextChild()) {
            String type = xml.wordAttribute("type");
            boolean note = xml.isWord("footnote") || xml.isWord("endnote");
            if (note && (type == null || type.equals("normal"))) {
                notes.add(new Story(kind, noun + " " + (notes.size() + 1), readBlocks(xml)));
            } else {
                xml.skip();
            }
        }
        return notes;
    }

    private void readBlock(XmlCursor xml, List<Block> blocks) throws DocxException {
        if (xml.isWord("p")) {
            readParagraph(xml, blocks);
        } else if (xml.isWord("tbl")) {
            blocks.add(readTable(xml));
        } else if (xml.isWord("sdt")) {
            xml.sdtContent(content -> content.forChildren(child -> readBlock(child, blocks)));
        } else if (xml.isWord("customXml")) {
            xml.forChildren(child -> readBlock(child, blocks));
        } else if (xml.isWord("ins") || xml.isWord("moveTo")) {
            trackedChanges = true;
            xml.forChildren(child -> readBlock(child, blocks));
        } else if (xml.isWord("del") || xml.isWord("moveFrom")) {
            trackedChanges = true;
            xml.skip();
        } else if (xml.isWord("sectPr")) {
            readSection(xml);
        } else if (xml.isMarkupCompatibility("AlternateContent")) {
            xml.firstChoice(choice -> choice.forChildren(child -> readBlock(child, blocks)));
        } else {
            xml.skip();   // bookmarks, comments' ranges, permissions: nothing shown
        }
    }

    // ---- paragraphs ------------------------------------------------------------------------------------------

    /** What a paragraph gathers as it is read; its text boxes follow it as blocks. */
    private static final class ParagraphContent {

        final SortedMap<String, String> properties = new TreeMap<>();
        final List<Run> runs = new ArrayList<>();
        final List<TextBox> textBoxes = new ArrayList<>();
        boolean endsSection;

        /** Joined to the run before when it looks the same: Word splits runs for its own reasons (revision ids…). */
        void addRun(String text, String styleId, Formatting formatting) {
            if (text.isEmpty()) {
                return;
            }
            if (!runs.isEmpty()) {
                Run last = runs.getLast();
                if (Objects.equals(last.styleId(), styleId) && last.formatting().equals(formatting)) {
                    runs.set(runs.size() - 1, new Run(last.text() + text, styleId, formatting));
                    return;
                }
            }
            runs.add(new Run(text, styleId, formatting));
        }
    }

    private void readParagraph(XmlCursor xml, List<Block> blocks) throws DocxException {
        ParagraphContent content = new ParagraphContent();
        while (xml.nextChild()) {
            if (xml.isWord("pPr")) {
                readParagraphProperties(xml, content);
            } else {
                readInline(xml, content);
            }
        }
        Formatting direct = new Formatting(content.properties);
        String styleId = direct.get("pStyle");
        String outline = direct.get("outlineLvl");
        int headingLevel = outline != null ? StyleSheet.headingLevelOfOutline(outline) : styles.headingLevel(styleId);
        blocks.add(new Paragraph(styleId, direct.without("pStyle", "numPr"), headingLevel, listItem(direct, styleId),
                content.endsSection, content.runs));
        blocks.addAll(content.textBoxes);
    }

    private void readParagraphProperties(XmlCursor xml, ParagraphContent content) throws DocxException {
        while (xml.nextChild()) {
            if (xml.isWord("sectPr")) {
                readSection(xml);
                content.endsSection = true;
            } else if (xml.isWord("rPr")) {
                xml.skip();   // the formatting of the paragraph mark, which shows nothing
            } else if (xml.isWord("pPrChange")) {
                trackedChanges = true;
                xml.skip();
            } else if (xml.isWord()) {
                FormattingReader.readProperty(xml, content.properties);
            } else {
                xml.skip();
            }
        }
    }

    /** The list the paragraph is in, from its own w:numPr or its style's; numId 0 takes it out of its style's list. */
    private ListItem listItem(Formatting direct, String styleId) {
        String listId = direct.get("numPr.numId");
        String level = direct.get("numPr.ilvl");
        if (listId == null) {
            listId = styles.inheritedParagraphProperty(styleId, "numPr.numId");
        }
        if (level == null) {
            level = styles.inheritedParagraphProperty(styleId, "numPr.ilvl");
        }
        if (listId == null || listId.equals("0")) {
            return null;
        }
        int ilvl = Numbering.parseLevel(level);
        Numbering.Level shown = numbering.level(listId, ilvl);
        return shown == null ? new ListItem(listId, ilvl, null, null)
                : new ListItem(listId, ilvl, shown.format(), shown.text(), shown.paragraph());
    }

    /** What a paragraph holds besides its properties: runs, and what wraps runs. */
    private void readInline(XmlCursor xml, ParagraphContent content) throws DocxException {
        if (xml.isWord("r")) {
            readRun(xml, content);
        } else if (xml.isWord("hyperlink") || xml.isWord("smartTag") || xml.isWord("customXml")
                || xml.isWord("fldSimple") || xml.isWord("dir") || xml.isWord("bdo")) {
            xml.forChildren(child -> readInline(child, content));
        } else if (xml.isWord("sdt")) {
            xml.sdtContent(sdt -> sdt.forChildren(child -> readInline(child, content)));
        } else if (xml.isWord("ins") || xml.isWord("moveTo")) {
            trackedChanges = true;
            xml.forChildren(child -> readInline(child, content));
        } else if (xml.isWord("del") || xml.isWord("moveFrom")) {
            trackedChanges = true;
            xml.skip();
        } else if (xml.isMarkupCompatibility("AlternateContent")) {
            xml.firstChoice(choice -> choice.forChildren(child -> readInline(child, content)));
        } else if (xml.isMath("oMath") || xml.isMath("oMathPara")) {
            content.addRun(equationText(xml), null, EQUATION);
        } else {
            xml.skip();   // bookmarks, proofing marks, comments' ranges
        }
    }

    private void readRun(XmlCursor xml, ParagraphContent content) throws DocxException {
        SortedMap<String, String> properties = new TreeMap<>();
        StringBuilder text = new StringBuilder();
        xml.forChildren(child -> readRunChild(child, properties, text, content));
        Formatting formatting = new Formatting(properties);
        content.addRun(text.toString(), formatting.get("rStyle"), formatting.without("rStyle"));
    }

    private void readRunChild(XmlCursor xml, SortedMap<String, String> properties, StringBuilder text,
                              ParagraphContent content) throws DocxException {
        if (xml.isMarkupCompatibility("AlternateContent")) {
            xml.firstChoice(choice -> choice.forChildren(child -> readRunChild(child, properties, text, content)));
            return;
        }
        if (!xml.isWord()) {
            xml.skip();
            return;
        }
        switch (xml.localName()) {
            case "rPr" -> {
                while (xml.nextChild()) {
                    if (xml.isWord("rPrChange")) {
                        trackedChanges = true;
                        xml.skip();
                    } else if (xml.isWord()) {
                        FormattingReader.readProperty(xml, properties);
                    } else {
                        xml.skip();
                    }
                }
            }
            case "t" -> text.append(xml.text());
            case "tab", "ptab" -> append(xml, text, "\t");
            case "br" -> {
                String type = xml.wordAttribute("type");
                append(xml, text, "page".equals(type) || "column".equals(type) ? "\f" : "\n");
            }
            case "cr" -> append(xml, text, "\n");
            case "noBreakHyphen" -> append(xml, text, "‑");
            case "softHyphen" -> append(xml, text, "­");
            case "sym" -> append(xml, text, symbol(xml.wordAttribute("char")));
            case "drawing", "pict", "object" -> findTextBoxes(xml, content);
            // w:instrText (a field's code), w:delText (deleted), w:fldChar, w:footnoteReference…: no text shown
            default -> xml.skip();
        }
    }

    private static void append(XmlCursor xml, StringBuilder text, String shown) throws DocxException {
        text.append(shown);
        xml.skip();
    }

    /** A symbol of a symbol font, by its code in hexadecimal ("F0E0"); nothing when unreadable. */
    private static String symbol(String code) {
        try {
            return code == null ? "" : new String(Character.toChars(Integer.parseInt(code, 16)));
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    /** The text boxes of a drawing, a VML picture or an object, wherever they are in it. */
    private void findTextBoxes(XmlCursor xml, ParagraphContent content) throws DocxException {
        xml.forChildren(child -> {
            if (child.isWord("txbxContent")) {
                content.textBoxes.add(new TextBox(readBlocks(child)));
            } else if (child.isMarkupCompatibility("AlternateContent")) {
                child.firstChoice(choice -> findTextBoxes(choice, content));
            } else {
                findTextBoxes(child, content);
            }
        });
    }

    /** An equation's text: its m:t, in order. */
    private String equationText(XmlCursor xml) throws DocxException {
        StringBuilder text = new StringBuilder();
        xml.forChildren(child -> text.append(child.isMath("t") ? child.text() : equationText(child)));
        return text.toString();
    }

    // ---- tables ----------------------------------------------------------------------------------------------

    private Table readTable(XmlCursor xml) throws DocxException {
        Formatting properties = Formatting.NONE;
        List<Table.Row> rows = new ArrayList<>();
        while (xml.nextChild()) {
            if (xml.isWord("tblPr")) {
                properties = FormattingReader.read(xml, () -> trackedChanges = true);
            } else {
                readRows(xml, rows);
            }
        }
        return new Table(properties.get("tblStyle"), properties.without("tblStyle"), rows);
    }

    private void readRows(XmlCursor xml, List<Table.Row> rows) throws DocxException {
        if (xml.isWord("tr")) {
            List<Table.Cell> cells = new ArrayList<>();
            Formatting[] properties = {Formatting.NONE};
            xml.forChildren(child -> {
                if (child.isWord("trPr")) {
                    properties[0] = FormattingReader.read(child, () -> trackedChanges = true);
                } else {
                    readCells(child, cells);
                }
            });
            // a row inserted or deleted with track changes: read as accepted, as all tracked changes are
            Formatting row = properties[0];
            if (sets(row, "ins") || sets(row, "del")) {
                trackedChanges = true;
            }
            if (!sets(row, "del")) {
                rows.add(new Table.Row(row.without("ins", "del"), cells));
            }
        } else if (xml.isWord("sdt")) {
            xml.sdtContent(content -> content.forChildren(child -> readRows(child, rows)));
        } else if (xml.isWord("customXml")) {
            xml.forChildren(child -> readRows(child, rows));
        } else {
            xml.skip();   // w:tblGrid: the columns' widths
        }
    }

    /** Whether a formatting sets this property: its value, or one of its attributes (w:ins w:author="…"). */
    private static boolean sets(Formatting formatting, String name) {
        return formatting.values().keySet().stream().anyMatch(key -> key.equals(name) || key.startsWith(name + "."));
    }

    private void readCells(XmlCursor xml, List<Table.Cell> cells) throws DocxException {
        if (xml.isWord("tc")) {
            Formatting properties = Formatting.NONE;
            List<Block> blocks = new ArrayList<>();
            while (xml.nextChild()) {
                if (xml.isWord("tcPr")) {
                    properties = FormattingReader.read(xml, () -> trackedChanges = true);
                } else {
                    readBlock(xml, blocks);
                }
            }
            // a cell inserted, deleted or merged with track changes: read as accepted
            if (sets(properties, "cellIns") || sets(properties, "cellDel") || sets(properties, "cellMerge")) {
                trackedChanges = true;
            }
            if (!sets(properties, "cellDel")) {
                cells.add(new Table.Cell(properties.without("cellIns", "cellDel", "cellMerge"), blocks));
            }
        } else if (xml.isWord("sdt")) {
            xml.sdtContent(content -> content.forChildren(child -> readCells(child, cells)));
        } else if (xml.isWord("customXml")) {
            xml.forChildren(child -> readCells(child, cells));
        } else {
            xml.skip();   // w:tblPrEx: the table's properties, for this row
        }
    }

    // ---- sections --------------------------------------------------------------------------------------------

    /** A section's end (w:sectPr): its headers and footers, by type. */
    private void readSection(XmlCursor xml) throws DocxException {
        sections++;
        while (xml.nextChild()) {
            if (xml.isWord("headerReference") || xml.isWord("footerReference")) {
                Story.Kind kind = xml.isWord("headerReference") ? Story.Kind.HEADER : Story.Kind.FOOTER;
                String type = Objects.requireNonNullElse(xml.wordAttribute("type"), "default");
                String id = xml.relationshipAttribute("id");
                if (id != null) {
                    sectionReferences.add(new SectionReference(sections, kind, type, id));
                }
            }
            xml.skip();
        }
    }
}
