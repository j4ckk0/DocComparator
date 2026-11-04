package org.jtools.doccomparator.docx;

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

/** A file that cannot be read as a Word document, and why, in words for the person who chose it. */
public final class DocxException extends IOException {

    public DocxException(String message) {
        super(message);
    }

    DocxException(String part, String message) {
        super(I18n.text("error.in", part, message));
    }
}
