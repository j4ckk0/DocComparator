package org.jtools.doccomparator.document;

/**
 * A piece of a paragraph's text that has one formatting: the character style it is in (null for none) and the
 * properties set on it directly. Word splits text into runs at will; the reader joins those that look the same.
 */
public record Run(String text, String styleId, Formatting formatting) {
}
