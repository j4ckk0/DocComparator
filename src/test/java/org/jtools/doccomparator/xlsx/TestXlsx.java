package org.jtools.doccomparator.xlsx;


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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds a workbook for a test: its sheets' XML, its shared strings, its styles and theme, as Excel writes them. */
final class TestXlsx {

    private static final String MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    private final List<String[]> sheets = new ArrayList<>();   // name, the worksheet's content
    private final List<String> strings = new ArrayList<>();   // each si's content
    private String styles;
    private String theme;
    private boolean date1904;

    /** A sheet: its name and what its worksheet element holds (cols, sheetData, mergeCells…). */
    TestXlsx sheet(String name, String content) {
        sheets.add(new String[] {name, content});
        return this;
    }

    /** Shared strings, plain, in their order: the first is index 0. */
    TestXlsx strings(String... texts) {
        for (String text : texts) {
            strings.add("<t>" + text + "</t>");
        }
        return this;
    }

    /** A shared string written as it is inside its si: rich runs, phonetic guides. */
    TestXlsx richString(String si) {
        strings.add(si);
        return this;
    }

    /** What styleSheet holds: numFmts, fonts, fills, borders, cellXfs… */
    TestXlsx styles(String content) {
        styles = content;
        return this;
    }

    /** What the theme's colour scheme holds: dk1, lt1… */
    TestXlsx theme(String scheme) {
        theme = scheme;
        return this;
    }

    TestXlsx date1904() {
        date1904 = true;
        return this;
    }

    Path write(Path folder, String name) throws IOException {
        Path file = folder.resolve(name);
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
            put(zip, "_rels/.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/"
                    + "relationships\"><Relationship Id=\"rId1\" Type=\"" + R + "/officeDocument\" "
                    + "Target=\"xl/workbook.xml\"/></Relationships>");
            StringBuilder links = new StringBuilder("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/"
                    + "2006/relationships\">");
            StringBuilder list = new StringBuilder();
            for (int i = 0; i < sheets.size(); i++) {
                links.append("<Relationship Id=\"rIdS").append(i + 1).append("\" Type=\"").append(R)
                        .append("/worksheet\" Target=\"worksheets/sheet").append(i + 1).append(".xml\"/>");
                list.append("<sheet name=\"").append(sheets.get(i)[0]).append("\" sheetId=\"").append(i + 1)
                        .append("\" r:id=\"rIdS").append(i + 1).append("\"/>");
                put(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", "<worksheet xmlns=\"" + MAIN + "\" xmlns:r=\""
                        + R + "\">" + sheets.get(i)[1] + "</worksheet>");
            }
            if (!strings.isEmpty()) {
                links.append("<Relationship Id=\"rIdT\" Type=\"").append(R)
                        .append("/sharedStrings\" Target=\"sharedStrings.xml\"/>");
                StringBuilder sst = new StringBuilder("<sst xmlns=\"" + MAIN + "\">");
                strings.forEach(si -> sst.append("<si>").append(si).append("</si>"));
                put(zip, "xl/sharedStrings.xml", sst.append("</sst>").toString());
            }
            if (styles != null) {
                links.append("<Relationship Id=\"rIdY\" Type=\"").append(R)
                        .append("/styles\" Target=\"styles.xml\"/>");
                put(zip, "xl/styles.xml", "<styleSheet xmlns=\"" + MAIN + "\">" + styles + "</styleSheet>");
            }
            if (theme != null) {
                links.append("<Relationship Id=\"rIdH\" Type=\"").append(R)
                        .append("/theme\" Target=\"theme/theme1.xml\"/>");
                put(zip, "xl/theme/theme1.xml", "<a:theme xmlns:a=\"http://schemas.openxmlformats.org/drawingml/"
                        + "2006/main\" name=\"Office\"><a:themeElements><a:clrScheme name=\"Office\">" + theme
                        + "</a:clrScheme></a:themeElements></a:theme>");
            }
            put(zip, "xl/_rels/workbook.xml.rels", links.append("</Relationships>").toString());
            put(zip, "xl/workbook.xml", "<workbook xmlns=\"" + MAIN + "\" xmlns:r=\"" + R + "\">"
                    + (date1904 ? "<workbookPr date1904=\"1\"/>" : "") + "<sheets>" + list + "</sheets></workbook>");
        }
        return file;
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
