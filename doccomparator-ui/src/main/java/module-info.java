
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

/**
 * DocComparator's comparison view, a Swing component another program can show (ComparatorPanel): two documents side
 * by side, their differences marked, the switches of what is compared. It needs no look and feel of its own: it takes
 * the colours of the one in use.
 */
module org.jtools.doccomparator.ui {
    requires transitive org.jtools.doccomparator.core;
    requires transitive java.desktop;

    exports org.jtools.doccomparator.ui;
    // not exported: ui.texts (this jar's texts)

    provides org.jtools.doccomparator.i18n.I18nTexts with org.jtools.doccomparator.ui.texts.UiTexts;
}
