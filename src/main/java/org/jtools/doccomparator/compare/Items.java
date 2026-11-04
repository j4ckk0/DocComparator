package org.jtools.doccomparator.compare;

import java.util.ArrayList;
import java.util.List;
import org.jtools.doccomparator.document.Block;
import org.jtools.doccomparator.document.Formatting;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.Run;
import org.jtools.doccomparator.document.Story;
import org.jtools.doccomparator.document.Table;
import org.jtools.doccomparator.document.TextBox;

/** Stories into the items the comparison pairs: their paragraphs and tables in reading order, text boxes opened. */
final class Items {

    private final Story story;
    private final List<Item> items = new ArrayList<>();
    private int tables;

    private Items(Story story) {
        this.story = story;
    }

    static List<Item> of(List<Story> stories) {
        List<Item> items = new ArrayList<>();
        for (Story story : stories) {
            Items reader = new Items(story);
            reader.add(story.blocks(), List.of());
            items.addAll(reader.items);
        }
        return items;
    }

    private void add(List<Block> blocks, List<Item.Place> places) {
        for (Block block : blocks) {
            switch (block) {
                case Paragraph paragraph -> items.add(new Item(story.kind(), story.label(), places, paragraph, null));
                case TextBox textBox -> add(textBox.blocks(), within(places, Item.Place.inTextBox()));
                case Table table -> {
                    List<Run> runs = new ArrayList<>();
                    for (Paragraph paragraph : paragraphs(List.of(table))) {
                        runs.add(new Run((runs.isEmpty() ? "" : "\t") + paragraph.text(), null, Formatting.NONE));
                    }
                    Paragraph text = new Paragraph(null, Formatting.NONE, 0, null, false, runs);
                    items.add(new Item(story.kind(), story.label(), within(places, Item.Place.inTable(++tables)),
                            text, table));
                }
            }
        }
    }

    /** The paragraphs of blocks in reading order, those of their tables and text boxes included. */
    static List<Paragraph> paragraphs(List<Block> blocks) {
        List<Paragraph> paragraphs = new ArrayList<>();
        for (Block block : blocks) {
            switch (block) {
                case Paragraph paragraph -> paragraphs.add(paragraph);
                case TextBox textBox -> paragraphs.addAll(paragraphs(textBox.blocks()));
                case Table table -> {
                    for (Table.Row row : table.rows()) {
                        for (Table.Cell cell : row.cells()) {
                            paragraphs.addAll(paragraphs(cell.blocks()));
                        }
                    }
                }
            }
        }
        return paragraphs;
    }

    static List<Item.Place> within(List<Item.Place> places, Item.Place place) {
        List<Item.Place> inner = new ArrayList<>(places);
        inner.add(place);
        return inner;
    }
}
