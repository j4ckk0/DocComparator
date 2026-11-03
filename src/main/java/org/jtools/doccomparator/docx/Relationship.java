package org.jtools.doccomparator.docx;

/**
 * A link from one part of a .docx to another, by id: its type (a URI ending in what it links to — "/styles",
 * "/header"…) and its target, a part name inside the file or, when external, the link as written (a path, a URL),
 * which nothing opens.
 */
record Relationship(String id, String type, String target, boolean external) {

    /** Of this kind: "styles", "header", "officeDocument"… — the end of the type, whichever variant of the format. */
    boolean isA(String kind) {
        return type.endsWith("/" + kind);
    }

    boolean isPart() {
        return !external;
    }
}
