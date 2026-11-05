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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * The readers there are: this jar's and those other jars provide (DocumentReader), loaded once, in a fixed order —
 * by DocumentReader.order(), then class name — whatever order the class path or the module graph gives them in.
 */
public final class DocumentReaders {

    private DocumentReaders() {
    }

    private static final class Loaded {
        static final List<DocumentReader> ALL = load();

        private static List<DocumentReader> load() {
            List<DocumentReader> readers = new ArrayList<>();
            ServiceLoader.load(DocumentReader.class, DocumentReader.class.getClassLoader()).forEach(reader -> {
                if (readers.stream().noneMatch(other -> other.getClass() == reader.getClass())) {
                    readers.add(reader);   // once, should a module's and the class path's both name it
                }
            });
            readers.sort(Comparator.comparingInt(DocumentReader::order)
                    .thenComparing(reader -> reader.getClass().getName()));
            return List.copyOf(readers);
        }
    }

    /** Every reader, in their order. */
    public static List<DocumentReader> all() {
        return Loaded.ALL;
    }

    /** The first reader, in their order, that reads this file by its extension. */
    public static Optional<DocumentReader> of(Path file) {
        return all().stream().filter(reader -> reader.reads(file)).findFirst();
    }

    /** The extensions of every reader, in their order, each once. */
    public static List<String> extensions() {
        return all().stream().flatMap(reader -> reader.extensions().stream()).distinct().toList();
    }
}
