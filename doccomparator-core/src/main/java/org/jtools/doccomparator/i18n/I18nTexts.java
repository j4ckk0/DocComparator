package org.jtools.doccomparator.i18n;

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

/**
 * A jar's texts, found as a service (java.util.ServiceLoader) by I18n, which speaks them all: each jar — the
 * comparison's, the view's, the program's, another one adding a format — provides its own, declared in its module
 * ({@code provides org.jtools.doccomparator.i18n.I18nTexts with ...}) or in META-INF/services. A key is one jar's alone.
 *
 * <p>The provider opens its files from its own code ({@code MyTexts.class.getResourceAsStream(...)}): in a module, a
 * resource of its packages is not another module's to read.
 */
public interface I18nTexts {

    /**
     * Its texts in this language, as a .properties file in UTF-8 (the same keys in every language), or null when it
     * has none in it: its keys are then said as they are.
     */
    InputStream open(I18nLanguage language);
}
