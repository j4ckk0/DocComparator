/*
 * Copyright 2026 j4ckk0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
