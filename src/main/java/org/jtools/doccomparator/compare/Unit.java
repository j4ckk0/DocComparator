package org.jtools.doccomparator.compare;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.jtools.doccomparator.document.Run;

/**
 * An item ready to compare: its tokens; its key, the same for two items whose text is the same as the options
 * compare it; its words, in lower case, which tell how much two texts have in common.
 *
 * @param words   its words' codes, in order
 * @param bag     the same, sorted: what two items have in common whatever the order, an upper bound of the rest
 * @param runEnds where each run of the paragraph ends in its text
 */
record Unit(Item item, String text, List<Token> tokens, String key, int[] words, int[] bag, int[] runEnds) {

    static Unit of(Item item, boolean blanks, Vocabulary vocabulary) {
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
        int[] bag = words.clone();
        Arrays.sort(bag);
        List<Run> runs = item.paragraph().runs();
        int[] runEnds = new int[runs.size()];
        int end = 0;
        for (int i = 0; i < runs.size(); i++) {
            end += runs.get(i).text().length();
            runEnds[i] = end;
        }
        return new Unit(item, text, tokens, key.toString(), words, bag, runEnds);
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
