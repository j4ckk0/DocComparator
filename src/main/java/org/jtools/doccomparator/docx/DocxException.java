package org.jtools.doccomparator.docx;

import java.io.IOException;

/** A file that cannot be read as a Word document, and why, in words for the person who chose it. */
public final class DocxException extends IOException {

    public DocxException(String message) {
        super(message);
    }

    DocxException(String part, String message) {
        super(part + ": " + message);
    }
}
