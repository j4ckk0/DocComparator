package org.jtools.doccomparator.ui;

import java.io.IOException;
import java.nio.file.Path;
import org.jtools.doccomparator.document.WordDocument;
import org.jtools.doccomparator.docx.DocxReader;
import org.jtools.doccomparator.i18n.I18n;
import org.jtools.doccomparator.text.TextReader;

/** What a side compares: a Word document's file, or text pasted on it. Read again each time the language changes. */
sealed interface DocumentSource {

    /** Its name, as the title and the side say it. */
    String name();

    WordDocument read() throws IOException;

    /** A Word document, read from its file. */
    record File(Path path) implements DocumentSource {

        @Override
        public String name() {
            return String.valueOf(path.getFileName());
        }

        @Override
        public WordDocument read() throws IOException {
            try {
                return DocxReader.read(path);
            } catch (IOException e) {
                throw new IOException(I18n.text("error.in", name(), e.getMessage()), e);
            }
        }
    }

    /** Text pasted or dropped: a paragraph a line (TextReader). */
    record Pasted(String text) implements DocumentSource {

        @Override
        public String name() {
            return I18n.text("paste.name");
        }

        @Override
        public WordDocument read() {
            return TextReader.read(name(), text);
        }
    }
}
