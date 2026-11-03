package org.jtools.doccomparator.document;

/**
 * The template of a document, as it names it — never opened: the template attached to it (its settings' link, often a
 * path or a network share, null when none) and the template Word records in its properties ("Normal.dotm", null when
 * none).
 */
public record Template(String attached, String named) {

    public static final Template NONE = new Template(null, null);
}
