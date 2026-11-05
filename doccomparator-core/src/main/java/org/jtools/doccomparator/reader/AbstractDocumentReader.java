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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.jtools.doccomparator.document.Document;

/**
 * A reader's extensions and order, given once to its constructor, and its errors named once: a reader of a new format
 * extends it and writes formatName() and readFile(), and readingNote() when its format is read otherwise than as it
 * is. Whatever readFile() throws becomes a DocumentReadException naming the file.
 */
public abstract class AbstractDocumentReader implements DocumentReader {

    private final List<String> extensions;
    private final int order;

    /** A reader of these extensions (their case and leading dot do not matter), after this jar's own (LATER). */
    protected AbstractDocumentReader(String... extensions) {
        this(LATER, extensions);
    }

    /** A reader of these extensions, at this place among the readers. */
    protected AbstractDocumentReader(int order, String... extensions) {
        if (extensions.length == 0) {
            throw new IllegalArgumentException("a reader reads files of at least one extension");
        }
        this.order = order;
        this.extensions = Arrays.stream(extensions)
                .map(extension -> extension.toLowerCase(Locale.ROOT).replaceFirst("^\\.", ""))
                .toList();
    }

    @Override
    public final Document read(Path file) throws DocumentReadException {
        try {
            return readFile(file);
        } catch (DocumentReadException e) {
            throw e;
        } catch (IOException e) {
            throw new DocumentReadException(String.valueOf(file.getFileName()), e.getMessage(), e);
        }
    }

    /**
     * The file as a document, named by its file name.
     *
     * @throws IOException when it cannot be read, its message saying why without naming the file (read() does)
     */
    protected abstract Document readFile(Path file) throws IOException;

    @Override
    public final List<String> extensions() {
        return extensions;
    }

    @Override
    public final int order() {
        return order;
    }
}
