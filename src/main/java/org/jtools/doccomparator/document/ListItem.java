package org.jtools.doccomparator.document;

/**
 * Where a paragraph sits in a list: the list (Word's numId), its level from 0, and that level's numbering as the
 * document defines it — its format ("decimal", "bullet", "lowerLetter"…) and its text ("%1.", "•"), null when the
 * document does not say — and the paragraph properties the level gives its items: their indent, mostly, which wins
 * over the paragraph's style, and loses to the paragraph's own.
 */
public record ListItem(String listId, int level, String numberFormat, String levelText, Formatting paragraph) {

    public ListItem(String listId, int level, String numberFormat, String levelText) {
        this(listId, level, numberFormat, levelText, Formatting.NONE);
    }
}
