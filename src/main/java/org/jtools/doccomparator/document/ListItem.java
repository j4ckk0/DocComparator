package org.jtools.doccomparator.document;

/**
 * Where a paragraph sits in a list: the list (Word's numId), its level from 0, and that level's numbering as the
 * document defines it — its format ("decimal", "bullet", "lowerLetter"…) and its text ("%1.", "•"), null when the
 * document does not say.
 */
public record ListItem(String listId, int level, String numberFormat, String levelText) {
}
