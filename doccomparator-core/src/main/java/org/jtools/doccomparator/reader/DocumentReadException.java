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
import org.jtools.doccomparator.i18n.I18n;

/**
 * A file a reader cannot read, and why: its message names the file once and says why, in words for the person who
 * chose it ("notes.docx: it is not a zip archive"). What every DocumentReader throws, whatever its format.
 */
public class DocumentReadException extends IOException {

    private final String fileName;
    private final String reason;

    /** A file of this name not read, for this reason (the reason in the language in use, without the name). */
    public DocumentReadException(String fileName, String reason, Throwable cause) {
        super(I18n.text("error.in", fileName, reason), cause);
        this.fileName = fileName;
        this.reason = reason;
    }

    public DocumentReadException(String fileName, String reason) {
        this(fileName, reason, null);
    }

    /** The name of the file not read. */
    public String fileName() {
        return fileName;
    }

    /** Why it was not read, the file not named. */
    public String reason() {
        return reason;
    }
}
