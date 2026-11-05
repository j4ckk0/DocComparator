package org.jtools.doccomparator.pdf;

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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import org.jtools.doccomparator.document.WordDocument;

/**
 * Writes small PDFs for the tests: pages of content written as operators, fonts and other objects given as their
 * dictionaries, which name one another by the numbers object() and stream() give back. Its fonts are /F1 Helvetica,
 * /F2 Helvetica-Bold, /F3 Helvetica-Oblique, every character 500 thousandths wide — 6 points at 12 points — so that
 * where text ends is known. Written plain, or with its streams compressed and its objects in an object stream behind a
 * cross-reference stream, as Word writes them.
 */
final class TestPdf {

    private final List<byte[]> objects = new ArrayList<>();   // object n is objects[n - 1]; null: a page, later
    private final List<Integer> pages = new ArrayList<>();
    private final List<String> contents = new ArrayList<>();
    private final Map<String, Integer> fonts = new LinkedHashMap<>();
    private final StringBuilder extraResources = new StringBuilder();
    private boolean compressed;
    private boolean objectStreams;
    private boolean wrongOffsets;
    private double[] box = {0, 0, 612, 792};

    TestPdf() {
        StringBuilder widths = new StringBuilder();
        for (int code = 32; code <= 255; code++) {
            widths.append(" 500");
        }
        for (String[] font : new String[][] {{"F1", "Helvetica"}, {"F2", "Helvetica-Bold"},
            {"F3", "Helvetica-Oblique"}}) {
            font(font[0], "<< /Type /Font /Subtype /Type1 /BaseFont /" + font[1]
                    + " /Encoding /WinAnsiEncoding /FirstChar 32 /LastChar 255 /Widths [" + widths + " ] >>");
        }
    }

    /** Adds an object, its number given back. */
    int object(String body) {
        objects.add(body.getBytes(StandardCharsets.ISO_8859_1));
        return objects.size();
    }

    /** Adds a stream with this dictionary (its /Length added) and these bytes. */
    int stream(String dictionary, byte[] data) {
        objects.add(streamBytes(dictionary, data));
        return objects.size();
    }

    int stream(String dictionary, String data) {
        return stream(dictionary, data.getBytes(StandardCharsets.ISO_8859_1));
    }

    TestPdf font(String name, String dictionary) {
        fonts.put(name, object(dictionary));
        return this;
    }

    /** A resource of the pages beside their fonts: "/XObject << /X1 7 0 R >>". */
    TestPdf resources(String entries) {
        extraResources.append(' ').append(entries);
        return this;
    }

    TestPdf page(String content) {
        contents.add(content);
        return this;
    }

    TestPdf box(double width, double height) {
        box = new double[] {0, 0, width, height};
        return this;
    }

    TestPdf compressed() {
        compressed = true;
        return this;
    }

    /** Its objects in an object stream, found through a cross-reference stream: PDF 1.5's way. */
    TestPdf inObjectStreams() {
        compressed = true;
        objectStreams = true;
        return this;
    }

    /** Its cross-reference table off by a few bytes: the file to be read by looking for its objects. */
    TestPdf withWrongOffsets() {
        wrongOffsets = true;
        return this;
    }

    WordDocument read() throws PdfException {
        return PdfReader.read(PdfFile.of("test.pdf", bytes()));
    }

    byte[] bytes() {
        List<byte[]> all = new ArrayList<>(objects);
        StringBuilder fontResources = new StringBuilder();
        fonts.forEach((name, number) -> fontResources.append(" /").append(name).append(' ').append(number)
                .append(" 0 R"));
        int pagesNumber = all.size() + 1;
        all.add(null);   // the page tree, once its kids are known
        List<Integer> kids = new ArrayList<>();
        for (String content : contents) {
            byte[] data = content.getBytes(StandardCharsets.ISO_8859_1);
            String dictionary = "<< >>";
            if (compressed) {
                data = deflate(data);
                dictionary = "<< /Filter /FlateDecode >>";
            }
            all.add(streamBytes(dictionary, data));
            int contentNumber = all.size();
            all.add(("<< /Type /Page /Parent " + pagesNumber + " 0 R /Contents " + contentNumber + " 0 R >>")
                    .getBytes(StandardCharsets.ISO_8859_1));
            kids.add(all.size());
        }
        StringBuilder kidList = new StringBuilder();
        kids.forEach(kid -> kidList.append(kid).append(" 0 R "));
        all.set(pagesNumber - 1, ("<< /Type /Pages /Kids [" + kidList + "] /Count " + kids.size()
                + " /MediaBox [" + box[0] + " " + box[1] + " " + box[2] + " " + box[3] + "] /Resources << /Font <<"
                + fontResources + " >>" + extraResources + " >> >>").getBytes(StandardCharsets.ISO_8859_1));
        all.add(("<< /Type /Catalog /Pages " + pagesNumber + " 0 R >>").getBytes(StandardCharsets.ISO_8859_1));
        int catalog = all.size();
        return objectStreams ? withObjectStreams(all, catalog) : plain(all, catalog);
    }

    private byte[] plain(List<byte[]> all, int catalog) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "%PDF-1.4\n%âãÏÓ\n");
        long[] offsets = new long[all.size() + 1];
        for (int i = 0; i < all.size(); i++) {
            offsets[i + 1] = out.size();
            write(out, (i + 1) + " 0 obj\n");
            out.write(all.get(i), 0, all.get(i).length);
            write(out, "\nendobj\n");
        }
        long xref = out.size();
        write(out, "xref\n0 " + (all.size() + 1) + "\n0000000000 65535 f \n");
        for (int i = 1; i <= all.size(); i++) {
            write(out, String.format("%010d 00000 n \n", offsets[i] + (wrongOffsets ? 7 : 0)));
        }
        write(out, "trailer\n<< /Size " + (all.size() + 1) + " /Root " + catalog + " 0 R >>\nstartxref\n" + xref
                + "\n%%EOF\n");
        return out.toByteArray();
    }

    /** Every object that is no stream in one object stream; the streams beside it; a cross-reference stream. */
    private byte[] withObjectStreams(List<byte[]> all, int catalog) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "%PDF-1.7\n%âãÏÓ\n");
        int streamNumber = all.size() + 1;
        int xrefNumber = all.size() + 2;
        long[][] entries = new long[xrefNumber + 1][];
        entries[0] = new long[] {0, 0, 65535};
        StringBuilder header = new StringBuilder();
        ByteArrayOutputStream held = new ByteArrayOutputStream();
        int index = 0;
        for (int i = 0; i < all.size(); i++) {
            String text = new String(all.get(i), StandardCharsets.ISO_8859_1);
            if (text.contains("\nstream\n")) {
                entries[i + 1] = new long[] {1, out.size(), 0};
                write(out, (i + 1) + " 0 obj\n");
                out.write(all.get(i), 0, all.get(i).length);
                write(out, "\nendobj\n");
            } else {
                header.append(i + 1).append(' ').append(held.size()).append(' ');
                held.write(all.get(i), 0, all.get(i).length);
                held.write('\n');
                entries[i + 1] = new long[] {2, streamNumber, index++};
            }
        }
        byte[] first = header.toString().getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(first, 0, first.length);
        byte[] heldBytes = held.toByteArray();
        body.write(heldBytes, 0, heldBytes.length);
        byte[] packed = deflate(body.toByteArray());
        entries[streamNumber] = new long[] {1, out.size(), 0};
        write(out, streamNumber + " 0 obj\n");
        byte[] stream = streamBytes("<< /Type /ObjStm /N " + index + " /First " + first.length
                + " /Filter /FlateDecode >>", packed);
        out.write(stream, 0, stream.length);
        write(out, "\nendobj\n");
        long xref = out.size();
        entries[xrefNumber] = new long[] {1, xref, 0};
        ByteArrayOutputStream rows = new ByteArrayOutputStream();
        for (long[] entry : entries) {
            byte[] row = {(byte) entry[0], (byte) (entry[1] >> 24), (byte) (entry[1] >> 16), (byte) (entry[1] >> 8),
                (byte) entry[1], (byte) entry[2]};
            // PNG "up" predictor, as Word writes its cross-reference streams
            rows.write(2);
            rows.write(row, 0, row.length);
        }
        byte[] predicted = up(rows.toByteArray(), 6);
        write(out, xrefNumber + " 0 obj\n");
        byte[] xrefStream = streamBytes("<< /Type /XRef /Size " + (xrefNumber + 1) + " /W [1 4 1] /Root " + catalog
                + " 0 R /Filter /FlateDecode /DecodeParms << /Predictor 12 /Columns 6 >> >>", deflate(predicted));
        out.write(xrefStream, 0, xrefStream.length);
        write(out, "\nendobj\nstartxref\n" + xref + "\n%%EOF\n");
        return out.toByteArray();
    }

    /** Rows of a filter byte and their bytes, each row made the difference with the one above it. */
    private static byte[] up(byte[] rows, int columns) {
        byte[] out = rows.clone();
        int row = columns + 1;
        for (int start = rows.length - row; start >= row; start -= row) {
            for (int i = 1; i <= columns; i++) {
                out[start + i] = (byte) (rows[start + i] - rows[start - row + i]);
            }
        }
        return out;
    }

    private static byte[] streamBytes(String dictionary, byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, dictionary.substring(0, dictionary.lastIndexOf(">>")) + " /Length " + data.length
                + " >>\nstream\n");
        out.write(data, 0, data.length);
        write(out, "\nendstream");
        return out.toByteArray();
    }

    static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        while (!deflater.finished()) {
            int n = deflater.deflate(buffer);
            out.write(buffer, 0, n);
        }
        deflater.end();
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.ISO_8859_1);
        out.write(bytes, 0, bytes.length);
    }

    /** A line of text at this place, in this font and size: "BT /F1 12 Tf 72 700 Td (…) Tj ET". */
    static String text(String font, double size, double x, double y, String text) {
        return "BT /" + font + " " + size + " Tf " + x + " " + y + " Td (" + text.replace("\\", "\\\\")
                .replace("(", "\\(").replace(")", "\\)") + ") Tj ET\n";
    }
}
