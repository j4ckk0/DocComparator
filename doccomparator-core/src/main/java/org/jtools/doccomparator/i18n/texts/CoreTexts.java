package org.jtools.doccomparator.i18n.texts;

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

import java.io.InputStream;
import org.jtools.doccomparator.i18n.I18nLanguage;
import org.jtools.doccomparator.i18n.I18nTexts;

/** The texts of the comparison and the readers: their messages, the words of a comparison (I18nTexts). */
public final class CoreTexts implements I18nTexts {

    @Override
    public InputStream open(I18nLanguage language) {
        return CoreTexts.class.getResourceAsStream("messages_" + language.code() + ".properties");
    }
}
