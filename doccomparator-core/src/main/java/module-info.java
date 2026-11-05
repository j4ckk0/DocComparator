
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
 * DocComparator's comparison, with no window: the model of a document (document), the readers of the files compared
 * (reader, and docx, xlsx, pdf, text for each format), the comparison (compare), what it says in words (report), and
 * the languages (i18n). It needs nothing but the JDK, and reaches no network (README, "No network").
 */
module org.jtools.doccomparator.core {
    requires java.xml;

    exports org.jtools.doccomparator.document;
    exports org.jtools.doccomparator.reader;
    exports org.jtools.doccomparator.compare;
    exports org.jtools.doccomparator.report;
    exports org.jtools.doccomparator.i18n;
    exports org.jtools.doccomparator.docx;
    exports org.jtools.doccomparator.xlsx;
    exports org.jtools.doccomparator.pdf;
    exports org.jtools.doccomparator.text;
    // not exported: ooxml (what the Word and Excel readers share), i18n.texts (this jar's texts)

    // the readers of the files compared, found as services: another jar's own among them (DocumentReaders)
    uses org.jtools.doccomparator.reader.DocumentReader;
    provides org.jtools.doccomparator.reader.DocumentReader with org.jtools.doccomparator.docx.DocxReader,
            org.jtools.doccomparator.xlsx.XlsxReader, org.jtools.doccomparator.pdf.PdfReader,
            org.jtools.doccomparator.text.TextReader;

    // the texts of every jar, its own among them (I18n)
    uses org.jtools.doccomparator.i18n.I18nTexts;
    provides org.jtools.doccomparator.i18n.I18nTexts with org.jtools.doccomparator.i18n.texts.CoreTexts;
}
