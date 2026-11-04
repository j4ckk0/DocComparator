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
import org.jtools.doccomparator.document.WordDocument;

/**
 * What a side of the comparison has: what it compares (its source, null until one is opened) and its document once read
 * (null until then). A side changes by being given another state, never in place: a reading away from the window's
 * thread works on the state it was given, whatever the side has become since.
 */
record SideState(DocumentSource source, WordDocument read) {

    /** A side with nothing opened. */
    static final SideState EMPTY = new SideState(null, null);

    /** A side given this source, not read yet. */
    static SideState of(DocumentSource source) {
        return new SideState(source, null);
    }

    boolean isOpen() {
        return source != null;
    }

    /** The same source, its document read. */
    SideState withRead(WordDocument document) {
        return new SideState(source, document);
    }

    /** Its document: the one read, else read now from its source — away from the window's thread. */
    WordDocument document() throws IOException {
        return read != null ? read : source.read();
    }
}
