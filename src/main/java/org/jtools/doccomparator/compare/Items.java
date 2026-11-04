package org.jtools.doccomparator.compare;

import java.util.ArrayList;
import java.util.List;
import org.jtools.doccomparator.document.Block;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.Story;
import org.jtools.doccomparator.document.Table;
import org.jtools.doccomparator.document.TextBox;

/** Stories into the items the comparison pairs: their paragraphs in reading order, tables and text boxes opened. */
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
                case Paragraph paragraph -> items.add(new Item(story.kind(), story.label(), places, paragraph));
                case TextBox textBox -> add(textBox.blocks(), within(places, Item.Place.inTextBox()));
                case Table table -> {
                    int number = ++tables;
                    for (int row = 0; row < table.rows().size(); row++) {
                        List<Table.Cell> cells = table.rows().get(row).cells();
                        for (int cell = 0; cell < cells.size(); cell++) {
                            add(cells.get(cell).blocks(), within(places, Item.Place.inCell(number, row + 1, cell + 1)));
                        }
                    }
                }
            }
        }
    }

    private static List<Item.Place> within(List<Item.Place> places, Item.Place place) {
        List<Item.Place> inner = new ArrayList<>(places);
        inner.add(place);
        return inner;
    }
}
