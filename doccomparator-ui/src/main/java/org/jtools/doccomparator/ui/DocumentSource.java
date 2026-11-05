package org.jtools.doccomparator.ui;

/*-
 * #%L
 * DocComparator
 * %%
 * Copyright (C) 2026 jtools.org
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.IOException;
import java.nio.file.Path;
import org.jtools.doccomparator.document.Document;
import org.jtools.doccomparator.i18n.I18n;
import org.jtools.doccomparator.reader.DocumentReadException;
import org.jtools.doccomparator.reader.DocumentReaders;
import org.jtools.doccomparator.text.TextReader;

/**
 * What a side compares: a file (File), text pasted on it (Pasted), or what a program that shows a ComparatorPanel
 * reads its own way — a document from a database, from memory — as a source of its own.
 */
public interface DocumentSource {

    /** Its name, as the title and the side say it. */
    String name();

    /**
     * The document, read away from the window's thread.
     *
     * @throws IOException when it cannot be read, its message saying why, in words for the person who chose it
     */
    Document read() throws IOException;

    /** What its side's head says of how it is read ("PDF: its paragraphs rebuilt…"); null for nothing. */
    default String readingNote() {
        return null;
    }

    /** A file, read by the reader of its extension; one of no reader's, as a Word document (DocumentReaders). */
    record File(Path path) implements DocumentSource {

        @Override
        public String name() {
            return String.valueOf(path.getFileName());
        }

        @Override
        public Document read() throws DocumentReadException {
            return DocumentReaders.forFile(path).read(path);
        }

        @Override
        public String readingNote() {
            return DocumentReaders.forFile(path).readingNote();
        }
    }

    /** Text pasted or dropped: a paragraph a line (TextReader). */
    record Pasted(String text) implements DocumentSource {

        @Override
        public String name() {
            return I18n.text("paste.name");
        }

        @Override
        public Document read() {
            return TextReader.read(name(), text);
        }

        @Override
        public String readingNote() {
            return I18n.text("header.pasted");
        }
    }
}
