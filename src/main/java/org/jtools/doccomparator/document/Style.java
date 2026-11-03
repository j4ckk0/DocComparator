package org.jtools.doccomparator.document;

/**
 * A style as the document defines it: its id (what paragraphs and runs refer to), its name as Word shows it, its type
 * ("paragraph", "character", "table", "numbering"), the style it is based on (null for none), and the paragraph and
 * run properties it sets itself.
 */
public record Style(String id, String name, String type, String basedOn, Formatting paragraph, Formatting run) {
}
