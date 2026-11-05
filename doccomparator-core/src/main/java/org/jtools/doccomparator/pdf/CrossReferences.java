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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jtools.doccomparator.pdf.PdfObjects.Operator;
import org.jtools.doccomparator.pdf.PdfObjects.Ref;
import org.jtools.doccomparator.pdf.PdfObjects.Stream;

/**
 * Where a PDF file's objects are, and its trailer: read from its cross-reference sections — tables or streams, the
 * newest first along /Prev — or, when they do not lead to the objects, rebuilt by looking for every "n g obj" in the
 * file, as viewers do.
 */
final class CrossReferences {

    private final byte[] data;
    private final PdfFile file;
    /**
     * Where each object is, by number: {0} freed; {1, offset, generation} in the file; {2, object stream, index} in an
     * object stream.
     */
    final Map<Integer, long[]> places = new HashMap<>();
    Map<String, Object> trailer = new LinkedHashMap<>();

    CrossReferences(byte[] data, PdfFile file) {
        this.data = data;
        this.file = file;
    }

    /** The sections the file's last startxref leads to, along /Prev, the newest first; none when it leads nowhere. */
    void read() {
        int startxref = PdfFile.lastIndexOf(data, "startxref");
        if (startxref < 0) {
            return;
        }
        Lexer lexer = new Lexer(data, startxref + "startxref".length());
        if (!(lexer.next() instanceof Long offset)) {
            return;
        }
        Set<Long> seen = new HashSet<>();
        List<Long> pending = new ArrayList<>(List.of(offset));
        boolean newest = true;
        while (!pending.isEmpty()) {
            long at = pending.removeFirst();
            if (at < 0 || at >= data.length || !seen.add(at)) {
                continue;
            }
            Map<String, Object> sectionTrailer = section((int) at);
            if (sectionTrailer == null) {
                if (newest) {
                    places.clear();
                    return;   // the newest section is not where it is said to be: rebuilt
                }
                continue;
            }
            if (newest) {
                trailer = new LinkedHashMap<>(sectionTrailer);
                newest = false;
            } else {
                sectionTrailer.forEach(trailer::putIfAbsent);
            }
            if (sectionTrailer.get("XRefStm") instanceof Long hybrid) {
                pending.addFirst(hybrid);
            }
            if (sectionTrailer.get("Prev") instanceof Number previous) {
                pending.add(previous.longValue());
            }
        }
    }

    /** A cross-reference section's entries, kept where no newer one says otherwise; its trailer, null if none is. */
    private Map<String, Object> section(int at) {
        Lexer lexer = new Lexer(data, at);
        if (lexer.at("xref")) {
            lexer.position(lexer.position() + 4);
            while (true) {
                lexer.skipWhitespace();
                if (lexer.at("trailer")) {
                    lexer.position(lexer.position() + 7);
                    return PdfObjects.dictionary(lexer.next());
                }
                if (!(lexer.next() instanceof Long first) || !(lexer.next() instanceof Long count)) {
                    return null;
                }
                for (long i = 0; i < count; i++) {
                    Object offset = lexer.next();
                    Object generation = lexer.next();
                    Object kind = lexer.next();
                    if (!(offset instanceof Long o) || !(generation instanceof Long g)
                            || !(kind instanceof Operator k)) {
                        return null;
                    }
                    int number = (int) (first + i);
                    if (k.name().equals("n") && o > 0) {
                        places.putIfAbsent(number, new long[] {1, o, g});
                    } else {
                        places.putIfAbsent(number, new long[] {0, 0});
                    }
                }
            }
        }
        if (!(lexer.next() instanceof Long number) || !(lexer.next() instanceof Long) || !lexer.at("obj")) {
            return null;
        }
        lexer.position(lexer.position() + 3);
        if (!(file.body(lexer, number.intValue(), 0, false) instanceof Stream stream)
                || !"XRef".equals(PdfObjects.name(stream.dictionary().get("Type")))) {
            return null;
        }
        Map<String, Object> dictionary = stream.dictionary();
        byte[] bytes;
        try {
            bytes = file.decoded(stream);
        } catch (PdfException _) {
            return null;
        }
        List<?> widths = PdfObjects.array(dictionary.get("W"));
        if (bytes == null || widths == null || widths.size() < 3) {
            return null;
        }
        int[] w = {PdfObjects.integer(widths.get(0), 0), PdfObjects.integer(widths.get(1), 0),
                PdfObjects.integer(widths.get(2), 0)};
        if (w[0] < 0 || w[1] < 0 || w[2] < 0 || w[0] + w[1] + w[2] == 0 || w[0] + w[1] + w[2] > 32) {
            return null;
        }
        List<?> index = PdfObjects.array(dictionary.get("Index"));
        if (index == null) {
            index = List.of(0L, (long) PdfObjects.integer(dictionary.get("Size"), 0));
        }
        int row = w[0] + w[1] + w[2];
        int offset = 0;
        for (int i = 0; i + 1 < index.size(); i += 2) {
            int first = PdfObjects.integer(index.get(i), 0);
            int count = PdfObjects.integer(index.get(i + 1), 0);
            for (int k = 0; k < count && offset + row <= bytes.length; k++) {
                long type = w[0] == 0 ? 1 : field(bytes, offset, w[0]);
                long second = field(bytes, offset + w[0], w[1]);
                long third = field(bytes, offset + w[0] + w[1], w[2]);
                offset += row;
                int objectNumber = first + k;
                if (type == 1) {
                    places.putIfAbsent(objectNumber, new long[] {1, second, third});
                } else if (type == 2) {
                    places.putIfAbsent(objectNumber, new long[] {2, second, third});
                } else {
                    places.putIfAbsent(objectNumber, new long[] {0, 0});
                }
            }
        }
        Map<String, Object> sectionTrailer = new LinkedHashMap<>(dictionary);
        return sectionTrailer;
    }

    private static long field(byte[] bytes, int at, int width) {
        long value = 0;
        for (int i = 0; i < width; i++) {
            value = (value << 8) | (bytes[at + i] & 0xFF);
        }
        return value;
    }

    /** Every "n g obj" of the file, the last of a number kept; the trailer from the last that names a catalog. */
    void rebuild() {
        places.clear();
        Map<String, Object> found = new LinkedHashMap<>();
        for (int i = 0; i < data.length - 4; i++) {
            if (data[i] == 'o' && data[i + 1] == 'b' && data[i + 2] == 'j'
                    && (i + 3 == data.length || Lexer.isWhitespace(data[i + 3] & 0xFF)
                    || Lexer.isDelimiter(data[i + 3] & 0xFF))) {
                int start = numbersBefore(i);
                if (start >= 0) {
                    Lexer lexer = new Lexer(data, start);
                    if (lexer.next() instanceof Long number && lexer.next() instanceof Long generation) {
                        places.put(number.intValue(), new long[] {1, start, generation});
                    }
                }
            } else if (data[i] == 't' && PdfFile.startsWith(data, i, "trailer")) {
                Map<String, Object> dictionary = PdfObjects.dictionary(new Lexer(data, i + 7).next());
                if (dictionary != null && dictionary.containsKey("Root")) {
                    found.putAll(dictionary);
                }
            }
        }
        // the object streams' objects, after every plain one is known
        for (Map.Entry<Integer, long[]> entry : new ArrayList<>(places.entrySet())) {
            Object value = file.object(entry.getKey());
            if (value instanceof Stream stream) {
                String type = PdfObjects.name(stream.dictionary().get("Type"));
                if ("ObjStm".equals(type)) {
                    Map<Integer, Object> held = file.objectStream(entry.getKey());
                    if (held != null) {
                        for (Integer number : held.keySet()) {
                            places.putIfAbsent(number, new long[] {2, entry.getKey(), 0});
                        }
                    }
                } else if ("XRef".equals(type) && stream.dictionary().containsKey("Root")) {
                    found.putAll(stream.dictionary());
                }
            }
        }
        file.forget();
        if (!found.containsKey("Root")) {
            for (Integer number : places.keySet()) {
                Map<String, Object> dictionary = PdfObjects.dictionary(file.object(number));
                if (dictionary != null && "Catalog".equals(PdfObjects.name(dictionary.get("Type")))) {
                    found.put("Root", new Ref(number, 0));
                }
            }
        }
        if (!found.isEmpty()) {
            found.forEach(trailer::putIfAbsent);
            if (found.containsKey("Root")) {
                trailer.put("Root", found.get("Root"));
            }
        }
        file.forget();
    }

    /** The start of "n g " before an "obj" at this place, -1 when there is none. */
    private int numbersBefore(int obj) {
        int i = obj - 1;
        int numbers = 0;
        while (numbers < 2) {
            while (i >= 0 && Lexer.isWhitespace(data[i] & 0xFF)) {
                i--;
            }
            int end = i;
            while (i >= 0 && data[i] >= '0' && data[i] <= '9') {
                i--;
            }
            if (i == end) {
                return -1;
            }
            numbers++;
        }
        return i + 1;
    }
}
