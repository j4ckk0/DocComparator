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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jtools.doccomparator.i18n.I18n;
import org.jtools.doccomparator.pdf.PdfObjects.Bytes;
import org.jtools.doccomparator.pdf.PdfObjects.Operator;
import org.jtools.doccomparator.pdf.PdfObjects.Ref;
import org.jtools.doccomparator.pdf.PdfObjects.Stream;

/**
 * A PDF file's objects, found through its cross-references and object streams, decrypted when the file is (with the
 * empty password a file opens with). A file whose cross-references do not lead to its objects is read again by
 * looking for every "n g obj" in it, as viewers do.
 */
final class PdfFile {

    /** The largest file read: past it, a PDF is a book's print master, not a document to compare. */
    static final long LARGEST_FILE = 512L << 20;
    /** The most references followed to reach a value: a longer chain loops. */
    private static final int LONGEST_CHAIN = 32;

    private final String fileName;
    private final byte[] data;
    private final Filters filters = new Filters();
    /** Where each object is: an offset in the file, or its object stream and its index in it. */
    private final Map<Integer, long[]> places = new HashMap<>();
    private final Map<Integer, Object> objects = new HashMap<>();
    private final Set<Integer> reading = new HashSet<>();
    private final Map<Integer, Map<Integer, Object>> objectStreams = new HashMap<>();
    private Map<String, Object> trailer = new LinkedHashMap<>();
    private StandardSecurity security;
    private Ref encryptRef;
    private boolean rebuilt;

    private PdfFile(String fileName, byte[] data) {
        this.fileName = fileName;
        this.data = data;
    }

    String fileName() {
        return fileName;
    }

    static PdfFile open(Path path) throws IOException {
        String name = String.valueOf(path.getFileName());
        if (Files.size(path) > LARGEST_FILE) {
            throw new PdfException(I18n.text("error.in", name, I18n.text("error.fileTooLarge", LARGEST_FILE >> 20)));
        }
        PdfFile file = new PdfFile(name, Files.readAllBytes(path));
        file.start();
        return file;
    }

    /** A file read from these bytes, for the tests. */
    static PdfFile of(String name, byte[] data) throws PdfException {
        PdfFile file = new PdfFile(name, data);
        file.start();
        return file;
    }

    private void start() throws PdfException {
        if (indexOf(data, "%PDF-", 0, Math.min(data.length, 1024)) < 0) {
            throw new PdfException(I18n.text("error.notPdf", fileName));
        }
        try {
            readCrossReferences();
        } catch (RuntimeException _) {
            places.clear();
        }
        if (!(resolve(trailer.get("Root")) instanceof Map<?, ?>)) {
            rebuild();
        }
        if (!(resolve(trailer.get("Root")) instanceof Map<?, ?>)) {
            throw new PdfException(I18n.text("error.in", fileName, I18n.text("error.pdfDamaged")));
        }
        Object encrypt = trailer.get("Encrypt");
        if (encrypt != null) {
            encryptRef = encrypt instanceof Ref ref ? ref : null;
            Map<String, Object> dictionary = PdfObjects.dictionary(resolve(encrypt));
            if (dictionary != null) {
                List<?> ids = PdfObjects.array(resolve(trailer.get("ID")));
                byte[] id = ids != null && !ids.isEmpty() && resolve(ids.getFirst()) instanceof Bytes bytes
                        ? bytes.value() : new byte[0];
                security = StandardSecurity.of(dictionary, id, this);
                objects.clear();   // read again, decrypted
                objectStreams.clear();
            }
        }
    }

    String error(String message) {
        return I18n.text("error.in", fileName, message);
    }

    Map<String, Object> catalog() {
        return PdfObjects.dictionary(resolve(trailer.get("Root")));
    }

    /** The value itself: a reference followed to its object (null for one the file does not hold). */
    Object resolve(Object value) {
        for (int i = 0; i < LONGEST_CHAIN && value instanceof Ref ref; i++) {
            value = object(ref.number());
        }
        return value instanceof Ref ? null : value;
    }

    /** A stream's bytes, decoded; null when they are an image's or cannot be. */
    byte[] decoded(Stream stream) throws PdfException {
        return filters.decode(stream, this);
    }

    private Object object(int number) {
        if (objects.containsKey(number)) {
            return objects.get(number);
        }
        if (!reading.add(number)) {
            return null;   // an object that needs itself, through its stream's length: a loop
        }
        try {
            Object value = read(number);
            if (value == MISPLACED && !rebuilt) {
                rebuild();
                value = read(number);
            }
            if (value == MISPLACED) {
                value = null;
            }
            objects.put(number, value);
            return value;
        } finally {
            reading.remove(number);
        }
    }

    /** What read() gives when the cross-references lead elsewhere than to the object. */
    private static final Object MISPLACED = new Object();

    private Object read(int number) {
        long[] place = places.get(number);
        if (place == null || place[0] == 0) {
            return null;   // not in the file, or freed
        }
        if (place[0] == 2) {
            Map<Integer, Object> held = objectStream((int) place[1]);
            return held == null ? null : held.get(number);
        }
        long offset = place[1];
        if (offset < 0 || offset >= data.length) {
            return MISPLACED;
        }
        Lexer lexer = new Lexer(data, (int) offset);
        if (!(lexer.next() instanceof Long found) || found != number || !(lexer.next() instanceof Long)
                || !lexer.at("obj")) {
            return MISPLACED;
        }
        lexer.position(lexer.position() + 3);
        return body(lexer, number, place.length > 2 ? (int) place[2] : 0, true);
    }

    /** An object's value after "n g obj", its stream read with it, decrypted. */
    private Object body(Lexer lexer, int number, int generation, boolean encrypted) {
        Object value = lexer.next();
        if (value instanceof Operator || value == Lexer.END) {
            return null;
        }
        if (value instanceof Map<?, ?> && lexer.at("stream")) {
            Map<String, Object> dictionary = PdfObjects.dictionary(value);
            byte[] raw = streamBytes(lexer, dictionary);
            boolean ours = encryptRef == null || encryptRef.number() != number;
            if (security != null && encrypted && ours && !"XRef".equals(PdfObjects.name(dictionary.get("Type")))) {
                raw = security.decryptStream(raw, number, generation, dictionary);
                dictionary = decryptStrings(dictionary, number, generation);
            }
            return new Stream(dictionary, raw);
        }
        if (security != null && encrypted && (encryptRef == null || encryptRef.number() != number)) {
            return decryptStrings(value, number, generation);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private <T> T decryptStrings(T value, int number, int generation) {
        if (value instanceof Bytes bytes) {
            return (T) new Bytes(security.decryptString(bytes.value(), number, generation));
        }
        if (value instanceof List<?> list) {
            List<Object> decrypted = new ArrayList<>(list.size());
            for (Object item : list) {
                decrypted.add(decryptStrings(item, number, generation));
            }
            return (T) decrypted;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> decrypted = new LinkedHashMap<>();
            map.forEach((key, item) -> decrypted.put((String) key, decryptStrings(item, number, generation)));
            return (T) decrypted;
        }
        return value;
    }

    private byte[] streamBytes(Lexer lexer, Map<String, Object> dictionary) {
        int start = lexer.position() + "stream".length();
        if (start < data.length && data[start] == '\r') {
            start++;
        }
        if (start < data.length && data[start] == '\n') {
            start++;
        }
        Object lengthValue = dictionary.get("Length");
        long length = lengthValue instanceof Ref ref ? PdfObjects.integer(object(ref.number()), -1)
                : PdfObjects.integer(lengthValue, -1);
        if (length >= 0 && start + length <= data.length) {
            Lexer after = new Lexer(data, (int) (start + length));
            if (after.at("endstream")) {
                return slice(start, (int) (start + length));
            }
        }
        int end = indexOf(data, "endstream", start, data.length);
        if (end < 0) {
            end = data.length;
        }
        int stop = end;
        if (stop > start && data[stop - 1] == '\n') {
            stop--;
        }
        if (stop > start && data[stop - 1] == '\r') {
            stop--;
        }
        return slice(start, stop);
    }

    private byte[] slice(int start, int end) {
        byte[] bytes = new byte[Math.max(0, end - start)];
        System.arraycopy(data, start, bytes, 0, bytes.length);
        return bytes;
    }

    private Map<Integer, Object> objectStream(int number) {
        if (objectStreams.containsKey(number)) {
            return objectStreams.get(number);
        }
        objectStreams.put(number, null);   // a stream held in itself: nothing
        Map<Integer, Object> held = new HashMap<>();
        if (object(number) instanceof Stream stream) {
            try {
                byte[] bytes = decoded(stream);
                if (bytes != null) {
                    int count = PdfObjects.integer(resolve(stream.dictionary().get("N")), 0);
                    int first = PdfObjects.integer(resolve(stream.dictionary().get("First")), 0);
                    Lexer header = new Lexer(bytes, 0, Math.min(first, bytes.length));
                    List<int[]> entries = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        if (!(header.next() instanceof Long objectNumber) || !(header.next() instanceof Long at)) {
                            break;
                        }
                        entries.add(new int[] {objectNumber.intValue(), at.intValue()});
                    }
                    for (int[] entry : entries) {
                        int at = first + entry[1];
                        if (at >= 0 && at < bytes.length) {
                            Object value = new Lexer(bytes, at).next();
                            held.putIfAbsent(entry[0], value instanceof Operator || value == Lexer.END ? null : value);
                        }
                    }
                }
            } catch (PdfException _) {
                held.clear();
            }
        }
        objectStreams.put(number, held);
        return held;
    }

    private void readCrossReferences() {
        int startxref = lastIndexOf(data, "startxref");
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
        if (!(body(lexer, number.intValue(), 0, false) instanceof Stream stream)
                || !"XRef".equals(PdfObjects.name(stream.dictionary().get("Type")))) {
            return null;
        }
        Map<String, Object> dictionary = stream.dictionary();
        byte[] bytes;
        try {
            bytes = decoded(stream);
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
    private void rebuild() {
        rebuilt = true;
        places.clear();
        objects.clear();
        objectStreams.clear();
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
            } else if (data[i] == 't' && startsWith(data, i, "trailer")) {
                Map<String, Object> dictionary = PdfObjects.dictionary(new Lexer(data, i + 7).next());
                if (dictionary != null && dictionary.containsKey("Root")) {
                    found.putAll(dictionary);
                }
            }
        }
        // the object streams' objects, after every plain one is known
        for (Map.Entry<Integer, long[]> entry : new ArrayList<>(places.entrySet())) {
            Object value = object(entry.getKey());
            if (value instanceof Stream stream) {
                String type = PdfObjects.name(stream.dictionary().get("Type"));
                if ("ObjStm".equals(type)) {
                    Map<Integer, Object> held = objectStream(entry.getKey());
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
        objects.clear();
        if (!found.containsKey("Root")) {
            for (Integer number : places.keySet()) {
                Map<String, Object> dictionary = PdfObjects.dictionary(object(number));
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
        objects.clear();
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

    private static boolean startsWith(byte[] data, int at, String text) {
        if (at + text.length() > data.length) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (data[at + i] != text.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    static int indexOf(byte[] data, String text, int from, int to) {
        byte[] wanted = text.getBytes(StandardCharsets.ISO_8859_1);
        for (int i = Math.max(0, from); i + wanted.length <= to; i++) {
            if (data[i] == wanted[0] && startsWith(data, i, text)) {
                return i;
            }
        }
        return -1;
    }

    private static int lastIndexOf(byte[] data, String text) {
        for (int i = data.length - text.length(); i >= 0; i--) {
            if (data[i] == text.charAt(0) && startsWith(data, i, text)) {
                return i;
            }
        }
        return -1;
    }
}
