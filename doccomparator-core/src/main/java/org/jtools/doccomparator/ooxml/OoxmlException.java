package org.jtools.doccomparator.ooxml;

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
 * Why a file cannot be read as a Word document or an Excel workbook, the file not named: its reader names it
 * (DocumentReadException); a part of the file, when the fault is in one, is named first.
 */
public final class OoxmlException extends IOException {

    public OoxmlException(String message) {
        super(message);
    }

    public OoxmlException(String part, String message) {
        super(I18n.text("error.in", part, message));
    }
}
