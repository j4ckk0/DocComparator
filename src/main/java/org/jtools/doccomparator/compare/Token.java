package org.jtools.doccomparator.compare;

import java.util.ArrayList;
import java.util.List;

/**
 * A piece of a paragraph's text, what its differences are counted in: a word (letters and digits), a blank (spaces,
 * tabs, breaks, of any length) or a mark (one character of anything else: punctuation, a symbol).
 */
record Token(int start, int end, Type type) {

    enum Type {
        WORD, BLANK, MARK
    }

    String text(String of) {
        return of.substring(start, end);
    }

    /** The tokens of a text, end to end. */
    static List<Token> of(String text) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int codePoint = text.codePointAt(i);
            Type type = typeOf(codePoint);
            int end = i + Character.charCount(codePoint);
            if (type != Type.MARK) {
                while (end < text.length() && typeOf(text.codePointAt(end)) == type) {
                    end += Character.charCount(text.codePointAt(end));
                }
            }
            tokens.add(new Token(i, end, type));
            i = end;
        }
        return tokens;
    }

    static boolean isBlank(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);   // the latter: no-break spaces
    }

    private static Type typeOf(int codePoint) {
        if (isBlank(codePoint)) {
            return Type.BLANK;
        }
        if (Character.isLetterOrDigit(codePoint)) {
            return Type.WORD;
        }
        return switch (Character.getType(codePoint)) {
            case Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> Type.WORD;
            default -> Type.MARK;
        };
    }
}
