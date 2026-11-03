package org.jtools.doccomparator.document;

import java.util.List;

/** A text box, or a shape holding text: its blocks come right after the paragraph it is anchored in. */
public record TextBox(List<Block> blocks) implements Block {

    public TextBox {
        blocks = List.copyOf(blocks);
    }
}
