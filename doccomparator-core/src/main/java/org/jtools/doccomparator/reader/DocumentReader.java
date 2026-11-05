package org.jtools.doccomparator.reader;

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

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.jtools.doccomparator.document.Document;

/**
 * Reads files of one format into a Document, the model every comparison works on. This jar's readers — Word,
 * Excel, PDF, text — are found as services (java.util.ServiceLoader, see DocumentReaders): another format is added by
 * a jar that provides one more, declared in its module ({@code provides ... with}) or in
 * META-INF/services/org.jtools.doccomparator.reader.DocumentReader. AbstractDocumentReader does the extensions' part.
 *
 * <p>A reader reads a file by its path, not a stream: Office files are zips, read by random access. It must read the
 * file and nothing else — no link followed, no network reached (README, "No network").
 */
public interface DocumentReader {

    /** The order of a reader that names none: after this jar's own, whose orders run from 10 to 40. */
    int LATER = 100;

    /** The file name extensions it reads: lower case, without their dot ("docx"). */
    List<String> extensions();

    /** What files of its format are called, in the language in use, as a list of them is named: "Word documents". */
    String formatName();

    /**
     * What a reader of the comparison should know of how such a file is read, in the language in use ("PDF: its
     * paragraphs rebuilt from the lines of its pages"); null when it is read as it is.
     */
    default String readingNote() {
        return null;
    }

    /** Where it comes among the readers, those of a smaller order first; then by class name. */
    default int order() {
        return LATER;
    }

    /** Whether it reads this file: its extension is one of extensions(), whatever its case. */
    default boolean reads(Path file) {
        String name = String.valueOf(file.getFileName()).toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot >= 0 && extensions().contains(name.substring(dot + 1));
    }

    /**
     * The file as a document, named by its file name.
     *
     * @throws DocumentReadException when it cannot be read, its message naming the file and saying why
     */
    Document read(Path file) throws DocumentReadException;
}
