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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jtools.doccomparator.document.Block;
import org.jtools.doccomparator.document.Formatting;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.Run;
import org.jtools.doccomparator.document.Story;
import org.jtools.doccomparator.document.StyleSheet;
import org.jtools.doccomparator.document.Table;
import org.jtools.doccomparator.document.Template;
import org.jtools.doccomparator.document.WordDocument;
import org.jtools.doccomparator.ooxml.OoxmlPackage;
import org.jtools.doccomparator.ooxml.Relationship;
import org.jtools.doccomparator.ooxml.XmlCursor;

/**
 * Reads an Excel workbook (.xlsx) into a WordDocument, so that it compares as a document does: each sheet, in the
 * workbook's order, a heading with its name and the table of its cells (SheetReader). Charts, pictures, comments,
 * conditional formatting and data validation are not read.
 */
public final class XlsxReader {

    /** The workbooks it reads, by their extension: Excel's, its macro-enabled ones, and their templates. */
    public static final List<String> EXTENSIONS = List.of("xlsx", "xlsm", "xltx", "xltm");

    private XlsxReader() {
    }

    /** Whether this file is read as a workbook: its extension is one of EXTENSIONS. */
    public static boolean reads(Path path) {
        String name = String.valueOf(path.getFileName()).toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot >= 0 && EXTENSIONS.contains(name.substring(dot + 1));
    }

    public static WordDocument read(Path file) throws IOException {
        try (OoxmlPackage xlsx = OoxmlPackage.open(file)) {
            String workbook = xlsx.mainPart("xl/workbook.xml");
            List<Relationship> relationships = xlsx.relationships(workbook);
            Colors colors = Colors.NONE;
            Optional<String> theme = OoxmlPackage.target(relationships, "theme");
            if (theme.isPresent() && xlsx.has(theme.get())) {
                try (XmlCursor xml = xlsx.open(theme.get())) {
                    colors = Colors.ofTheme(xml);
                }
            }
            Styles styles = Styles.NONE;
            Optional<String> stylesPart = OoxmlPackage.target(relationships, "styles");
            if (stylesPart.isPresent() && xlsx.has(stylesPart.get())) {
                styles = Styles.read(xlsx, stylesPart.get(), colors);
            }
            List<List<SheetReader.RichRun>> strings = new ArrayList<>();
            Optional<String> stringsPart = OoxmlPackage.target(relationships, "sharedStrings");
            if (stringsPart.isPresent() && xlsx.has(stringsPart.get())) {
                Colors fontColors = colors;
                try (XmlCursor xml = xlsx.open(stringsPart.get())) {
                    xml.root("sst");
                    xml.forChildren(si -> strings.add(SheetReader.richText(si, fontColors)));
                }
            }
            List<Block> blocks = new ArrayList<>();
            boolean date1904 = false;
            List<String[]> sheets = new ArrayList<>();   // name, relationship id
            try (XmlCursor xml = xlsx.open(workbook)) {
                xml.root("workbook");
                while (xml.nextChild()) {
                    if (xml.isSpreadsheet("workbookPr")) {
                        String value = xml.plainAttribute("date1904");
                        date1904 = "1".equals(value) || "true".equals(value);
                        xml.skip();
                    } else if (xml.isSpreadsheet("sheets")) {
                        xml.forChildren(sheet -> {
                            sheets.add(new String[] {sheet.plainAttribute("name"),
                                    sheet.relationshipAttribute("id")});
                            sheet.skip();
                        });
                    } else {
                        xml.skip();
                    }
                }
            }
            long left = SheetReader.LARGEST_WORKBOOK;   // the cells the workbook's tables may still hold
            for (String[] sheet : sheets) {
                String name = sheet[0] == null ? "" : sheet[0];
                blocks.add(heading(name));
                Optional<Relationship> link = relationships.stream()
                        .filter(r -> r.id().equals(sheet[1]) && r.isPart() && r.isA("worksheet")).findFirst();
                if (link.isEmpty() || !xlsx.has(link.get().target())) {
                    continue;   // a chart sheet, a dialog sheet: its name alone
                }
                try (XmlCursor xml = xlsx.open(link.get().target())) {
                    Table table = new SheetReader(strings, styles, colors, date1904, left).read(xml, name);
                    if (table != null) {
                        blocks.add(table);
                        left -= (long) table.rows().size() * table.rows().getFirst().cells().stream()
                                .mapToLong(cell -> Math.max(1, span(cell))).sum();
                    }
                }
            }
            return new WordDocument(String.valueOf(file.getFileName()), new Story(Story.Name.BODY, blocks),
                    List.of(), List.of(), StyleSheet.NONE, Template.NONE, false);
        }
    }

    private static int span(Table.Cell cell) {
        Integer across = Styles.number(cell.formatting().get("gridSpan"));
        return across == null ? 1 : across;
    }

    /** A sheet's name, as a heading of the first level. */
    private static Paragraph heading(String name) {
        return new Paragraph(null, Formatting.NONE, 1, null, false,
                name.isEmpty() ? List.of() : List.of(new Run(name, null, Formatting.NONE)));
    }
}
