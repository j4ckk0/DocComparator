package org.jtools.doccomparator.compare;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.jtools.doccomparator.document.Run;

/**
 * An item ready to compare: its tokens; its key, the same for two items whose text is the same as the options compare
 * it; its words, in lower case, which tell how much two texts have in common. A table, one of its rows or one of its
 * columns is a unit too, made of its cells': it is paired as a paragraph is, by its words.
 *
 * @param words   its words' codes, in order
 * @param bag     the same, sorted: what two items have in common whatever the order, an upper bound of the rest
 * @param runEnds where each run of the paragraph ends in its text
 * @param cells   for a table, its cells by row and column on its grid, null where none starts; null otherwise
 */
record Unit(Item item, String text, List<Token> tokens, String key, int[] words, int[] bag, int[] runEnds,
            Unit[][] cells) {

    /** Starts the key of a table, a row or a column: never the same as a paragraph's. */
    private static final char COMPOSITE = '\u0002';
    /** Between the keys of cells. */
    private static final char BETWEEN_CELLS = '\u0001';

    static Unit of(Item item, boolean blanks, Vocabulary vocabulary) {
        if (item.isTable()) {
            return table(item, blanks, vocabulary);
        }
        String text = item.text();
        List<Token> tokens = Token.of(text);
        StringBuilder key = new StringBuilder();
        for (Token token : tokens) {
            if (blanks || token.type() != Token.Type.BLANK) {
                key.append(token.text(text));
            }
        }
        int[] words = tokens.stream().filter(token -> token.type() == Token.Type.WORD)
                .mapToInt(token -> vocabulary.code(token.text(text).toLowerCase(Locale.ROOT))).toArray();
        List<Run> runs = item.paragraph().runs();
        int[] runEnds = new int[runs.size()];
        int end = 0;
        for (int i = 0; i < runs.size(); i++) {
            end += runs.get(i).text().length();
            runEnds[i] = end;
        }
        return new Unit(item, text, tokens, key.toString(), words, sorted(words), runEnds, null);
    }

    private static Unit table(Item item, boolean blanks, Vocabulary vocabulary) {
        TableGrid grid = TableGrid.of(item);
        Unit[][] cells = new Unit[grid.cells.length][grid.columns];
        List<Unit> all = new ArrayList<>();
        for (int r = 0; r < cells.length; r++) {
            for (int c = 0; c < grid.columns; c++) {
                if (grid.cells[r][c] != null) {
                    cells[r][c] = of(grid.cells[r][c], blanks, vocabulary);
                    all.add(cells[r][c]);
                }
            }
        }
        Unit table = composite(item, all);
        return new Unit(item, table.text, table.tokens, table.key, table.words, table.bag, table.runEnds, cells);
    }

    /** A unit made of others — a table, a row, a column — and standing for this item. */
    static Unit composite(Item item, List<Unit> parts) {
        StringBuilder text = new StringBuilder();
        StringBuilder key = new StringBuilder().append(COMPOSITE);
        int[] words = new int[parts.stream().mapToInt(part -> part.words.length).sum()];
        int w = 0;
        for (Unit part : parts) {
            if (!text.isEmpty()) {
                text.append('\t');
            }
            text.append(part.text);
            key.append(part.key).append(BETWEEN_CELLS);
            System.arraycopy(part.words, 0, words, w, part.words.length);
            w += part.words.length;
        }
        return new Unit(item, text.toString(), Token.of(text.toString()), key.toString(), words, sorted(words),
                new int[0], null);
    }

    private static int[] sorted(int[] words) {
        int[] bag = words.clone();
        Arrays.sort(bag);
        return bag;
    }

    boolean isTable() {
        return cells != null;
    }

    /** Nothing but blanks, or nothing at all. */
    boolean isBlank() {
        return tokens.stream().allMatch(token -> token.type() == Token.Type.BLANK);
    }

    /** How much text it holds, to weigh one match against another: its words, one at least. */
    int size() {
        return Math.max(1, words.length);
    }

    /** The run a character of its text is in. */
    Run runAt(int offset) {
        int index = Arrays.binarySearch(runEnds, offset);
        index = index >= 0 ? index + 1 : -index - 1;   // the first run ending after offset
        List<Run> runs = item.paragraph().runs();
        return runs.get(Math.min(index, runs.size() - 1));
    }
}
