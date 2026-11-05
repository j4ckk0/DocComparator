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
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import org.jtools.doccomparator.i18n.I18n;

/**
 * Undoes a stream's filters, those text and structure are kept under: Flate (with its predictors), LZW, ASCII hex and
 * 85, run length. Images' own (DCT, JPX, CCITT, JBIG2) are not undone: what they hold is never read. Every byte
 * decoded counts against one budget for the whole file, as an Office file's parts do: a stream that would inflate
 * past it is a decompression bomb, refused.
 */
final class Filters {

    /** The bytes a file may decode in all, as many as an Office file's part may unzip to. */
    static final long LARGEST_DECODED = 512L << 20;

    private long left = LARGEST_DECODED;

    /** The stream's bytes once its filters are undone; null when one of them is an image's. */
    byte[] decode(PdfObjects.Stream stream, PdfFile file) throws PdfException {
        Map<String, Object> dictionary = stream.dictionary();
        Object filter = file.resolve(dictionary.get("Filter"));
        Object parameters = file.resolve(dictionary.get("DecodeParms"));
        if (parameters == null) {
            parameters = file.resolve(dictionary.get("DP"));
        }
        byte[] bytes = stream.raw();
        List<?> filters = filter instanceof List<?> list ? list : filter == null ? List.of() : List.of(filter);
        for (int i = 0; i < filters.size(); i++) {
            String name = PdfObjects.name(file.resolve(filters.get(i)));
            Object those = parameters instanceof List<?> list ? (i < list.size() ? file.resolve(list.get(i)) : null)
                    : parameters;
            Map<String, Object> parameter = PdfObjects.dictionary(those);
            if (name == null) {
                continue;
            }
            bytes = switch (name) {
                case "FlateDecode", "Fl" -> predicted(inflate(bytes), parameter, file);
                case "LZWDecode", "LZW" -> predicted(lzw(bytes, parameter == null
                        || PdfObjects.integer(file.resolve(parameter.get("EarlyChange")), 1) != 0), parameter, file);
                case "ASCIIHexDecode", "AHx" -> asciiHex(bytes);
                case "ASCII85Decode", "A85" -> ascii85(bytes);
                case "RunLengthDecode", "RL" -> runLength(bytes);
                case "Crypt" -> bytes;   // undone by the file, with the rest of its encryption
                default -> null;
            };
            if (bytes == null) {
                return null;
            }
        }
        return bytes;
    }

    private void count(long bytes) throws PdfException {
        left -= bytes;
        if (left < 0) {
            throw new PdfException(I18n.text("error.tooLarge", LARGEST_DECODED >> 20));
        }
    }

    /** Inflates what can be: a stream cut short or damaged keeps the bytes inflated before it. */
    private byte[] inflate(byte[] bytes) throws PdfException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, bytes.length * 3));
        if (!inflate(bytes, false, out) && out.size() == 0) {
            inflate(bytes, true, out);   // without zlib's header: some writers leave it out
        }
        return out.toByteArray();
    }

    private boolean inflate(byte[] bytes, boolean raw, ByteArrayOutputStream out) throws PdfException {
        Inflater inflater = new Inflater(raw);
        try {
            inflater.setInput(bytes);
            byte[] buffer = new byte[16384];
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        break;
                    }
                    continue;
                }
                count(n);
                out.write(buffer, 0, n);
            }
            return true;
        } catch (DataFormatException _) {
            return false;
        } finally {
            inflater.end();
        }
    }

    private byte[] predicted(byte[] bytes, Map<String, Object> parameters, PdfFile file) throws PdfException {
        if (parameters == null) {
            return bytes;
        }
        int predictor = PdfObjects.integer(file.resolve(parameters.get("Predictor")), 1);
        if (predictor < 2) {
            return bytes;
        }
        int colors = Math.max(1, PdfObjects.integer(file.resolve(parameters.get("Colors")), 1));
        int bits = Math.max(1, PdfObjects.integer(file.resolve(parameters.get("BitsPerComponent")), 8));
        int columns = Math.max(1, PdfObjects.integer(file.resolve(parameters.get("Columns")), 1));
        int pixel = Math.max(1, (colors * bits + 7) / 8);
        int row = (int) Math.min(Integer.MAX_VALUE / 2, ((long) colors * bits * columns + 7) / 8);
        ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length);
        if (predictor == 2) {   // TIFF, 8 bits a component only
            for (int start = 0; start < bytes.length; start += row) {
                int length = Math.min(row, bytes.length - start);
                byte[] line = new byte[length];
                System.arraycopy(bytes, start, line, 0, length);
                if (bits == 8) {
                    for (int i = pixel; i < length; i++) {
                        line[i] += line[i - pixel];
                    }
                }
                out.write(line, 0, length);
            }
            return out.toByteArray();
        }
        byte[] previous = new byte[row];
        for (int start = 0; start < bytes.length; start += row + 1) {
            int type = bytes[start] & 0xFF;
            int length = Math.min(row, bytes.length - start - 1);
            if (length <= 0) {
                break;
            }
            byte[] line = new byte[row];
            System.arraycopy(bytes, start + 1, line, 0, length);
            for (int i = 0; i < length; i++) {
                int left = i >= pixel ? line[i - pixel] & 0xFF : 0;
                int up = previous[i] & 0xFF;
                int upLeft = i >= pixel ? previous[i - pixel] & 0xFF : 0;
                int value = line[i] & 0xFF;
                line[i] = (byte) switch (type) {
                    case 1 -> value + left;
                    case 2 -> value + up;
                    case 3 -> value + (left + up) / 2;
                    case 4 -> value + paeth(left, up, upLeft);
                    default -> value;
                };
            }
            out.write(line, 0, length);
            previous = line;
        }
        return out.toByteArray();
    }

    private static int paeth(int left, int up, int upLeft) {
        int p = left + up - upLeft;
        int pa = Math.abs(p - left);
        int pb = Math.abs(p - up);
        int pc = Math.abs(p - upLeft);
        return pa <= pb && pa <= pc ? left : pb <= pc ? up : upLeft;
    }

    private byte[] lzw(byte[] bytes, boolean early) throws PdfException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length * 2);
        byte[][] table = new byte[4096][];
        for (int i = 0; i < 256; i++) {
            table[i] = new byte[] {(byte) i};
        }
        int next = 258;
        int width = 9;
        byte[] previous = null;
        long buffer = 0;
        int held = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xFF);
            held += 8;
            while (held >= width) {
                int code = (int) ((buffer >> (held - width)) & ((1 << width) - 1));
                held -= width;
                if (code == 256) {
                    next = 258;
                    width = 9;
                    previous = null;
                    continue;
                }
                if (code == 257) {
                    return out.toByteArray();
                }
                byte[] entry;
                if (code < next && table[code] != null) {
                    entry = table[code];
                } else if (code == next && previous != null) {
                    entry = new byte[previous.length + 1];
                    System.arraycopy(previous, 0, entry, 0, previous.length);
                    entry[previous.length] = previous[0];
                } else {
                    return out.toByteArray();   // damaged: what was decoded before
                }
                count(entry.length);
                out.write(entry, 0, entry.length);
                if (previous != null && next < 4096) {
                    byte[] added = new byte[previous.length + 1];
                    System.arraycopy(previous, 0, added, 0, previous.length);
                    added[previous.length] = entry[0];
                    table[next++] = added;
                }
                previous = entry;
                int limit = early ? next + 1 : next;
                if (limit >= 512 && width == 9 || limit >= 1024 && width == 10 || limit >= 2048 && width == 11) {
                    width++;
                }
            }
        }
        return out.toByteArray();
    }

    private byte[] asciiHex(byte[] bytes) throws PdfException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length / 2);
        int high = -1;
        for (byte b : bytes) {
            if (b == '>') {
                break;
            }
            int digit = Lexer.hexDigit(b);
            if (digit < 0) {
                continue;
            }
            if (high < 0) {
                high = digit;
            } else {
                out.write(high * 16 + digit);
                high = -1;
            }
        }
        if (high >= 0) {
            out.write(high * 16);
        }
        count(out.size());
        return out.toByteArray();
    }

    private byte[] ascii85(byte[] bytes) throws PdfException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length);
        long group = 0;
        int count = 0;
        for (int i = 0; i < bytes.length; i++) {
            int c = bytes[i] & 0xFF;
            if (c == '~') {
                break;
            }
            if (Lexer.isWhitespace(c)) {
                continue;
            }
            if (c == 'z' && count == 0) {
                out.write(0);
                out.write(0);
                out.write(0);
                out.write(0);
                continue;
            }
            if (c < '!' || c > 'u') {
                continue;
            }
            group = group * 85 + (c - '!');
            if (++count == 5) {
                for (int shift = 24; shift >= 0; shift -= 8) {
                    out.write((int) (group >> shift) & 0xFF);
                }
                group = 0;
                count = 0;
            }
        }
        if (count > 1) {
            for (int i = count; i < 5; i++) {
                group = group * 85 + 84;
            }
            for (int k = 0; k < count - 1; k++) {
                out.write((int) (group >> (24 - 8 * k)) & 0xFF);
            }
        }
        count(out.size());
        return out.toByteArray();
    }

    private byte[] runLength(byte[] bytes) throws PdfException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length * 2);
        int i = 0;
        while (i < bytes.length) {
            int length = bytes[i++] & 0xFF;
            if (length == 128) {
                break;
            }
            if (length < 128) {
                int n = Math.min(length + 1, bytes.length - i);
                count(n);
                out.write(bytes, i, n);
                i += n;
            } else if (i < bytes.length) {
                count(257 - length);
                for (int k = 0; k < 257 - length; k++) {
                    out.write(bytes[i]);
                }
                i++;
            }
        }
        return out.toByteArray();
    }
}
