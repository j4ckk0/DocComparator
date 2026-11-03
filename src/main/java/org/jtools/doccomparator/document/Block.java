package org.jtools.doccomparator.document;

/** What a story is made of, in reading order. */
public sealed interface Block permits Paragraph, Table, TextBox {
}
