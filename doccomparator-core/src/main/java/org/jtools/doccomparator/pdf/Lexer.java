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
import org.jtools.doccomparator.pdf.PdfObjects.Bytes;
import org.jtools.doccomparator.pdf.PdfObjects.Name;
import org.jtools.doccomparator.pdf.PdfObjects.Operator;
import org.jtools.doccomparator.pdf.PdfObjects.Ref;

/**
 * Reads PDF values from bytes — a file's objects, a content stream's operands and operators — leniently: a stray
 * delimiter is passed over, a value cut short ends where the bytes do. Keywords come as Operator, "n g R" as a Ref.
 */
final class Lexer {

    /** What next() gives at the end of the bytes. */
    static final Object END = new Object();

    /** The deepest nesting of arrays and dictionaries read: deeper is a crafted file, not a document. */
    private static final int DEEPEST = 256;

    private final byte[] data;
    private final int end;
    private int position;

    Lexer(byte[] data, int position) {
        this(data, position, data.length);
    }

    Lexer(byte[] data, int position, int end) {
        this.data = data;
        this.position = position;
        this.end = Math.min(end, data.length);
    }

    int position() {
        return position;
    }

    void position(int position) {
        this.position = position;
    }

    byte[] data() {
        return data;
    }

    /** The next value, an Operator for a keyword, END at the end. */
    Object next() {
        Object value;
        do {
            value = value(0);
        } while (value == CLOSE);
        return value;
    }

    static boolean isWhitespace(int c) {
        return c == 0 || c == 9 || c == 10 || c == 12 || c == 13 || c == 32;
    }

    static boolean isDelimiter(int c) {
        return c == '(' || c == ')' || c == '<' || c == '>' || c == '[' || c == ']' || c == '{' || c == '}'
                || c == '/' || c == '%';
    }

    void skipWhitespace() {
        while (position < end) {
            int c = data[position] & 0xFF;
            if (c == '%') {
                while (position < end && data[position] != '\n' && data[position] != '\r') {
                    position++;
                }
            } else if (isWhitespace(c)) {
                position++;
            } else {
                return;
            }
        }
    }

    private Object value(int depth) {
        while (true) {
            skipWhitespace();
            if (position >= end) {
                return END;
            }
            int c = data[position] & 0xFF;
            switch (c) {
                case '/' -> {
                    return name();
                }
                case '(' -> {
                    return literal();
                }
                case '[' -> {
                    position++;
                    return array(depth + 1);
                }
                case '<' -> {
                    if (position + 1 < end && data[position + 1] == '<') {
                        position += 2;
                        return dictionary(depth + 1);
                    }
                    return hex();
                }
                case ')', '>', ']', '{', '}' -> position++;   // stray: passed over (a closing one ends its caller)
                default -> {
                    return word();
                }
            }
            if (c == ']' || c == '>') {
                return CLOSE;
            }
        }
    }

    /** What ends an array or a dictionary, given to them. */
    private static final Object CLOSE = new Object();

    private List<Object> array(int depth) {
        List<Object> list = new ArrayList<>();
        if (depth > DEEPEST) {
            return list;
        }
        while (true) {
            Object value = value(depth);
            if (value == END || value == CLOSE) {
                return list;
            }
            list.add(value);
        }
    }

    private Map<String, Object> dictionary(int depth) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (depth > DEEPEST) {
            return map;
        }
        while (true) {
            Object key = value(depth);
            if (key == END || key == CLOSE) {
                if (key == CLOSE && position < end && data[position] == '>') {
                    position++;   // the second '>' of '>>'
                }
                return map;
            }
            if (!(key instanceof Name name)) {
                continue;
            }
            Object value = value(depth);
            if (value == END) {
                return map;
            }
            if (value == CLOSE) {
                if (position < end && data[position] == '>') {
                    position++;
                }
                return map;
            }
            if (value != null) {
                map.put(name.value(), value);
            }
        }
    }

    private Name name() {
        position++;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        while (position < end) {
            int c = data[position] & 0xFF;
            if (isWhitespace(c) || isDelimiter(c)) {
                break;
            }
            if (c == '#' && position + 2 < end && hexDigit(data[position + 1]) >= 0
                    && hexDigit(data[position + 2]) >= 0) {
                bytes.write(hexDigit(data[position + 1]) * 16 + hexDigit(data[position + 2]));
                position += 3;
            } else {
                bytes.write(c);
                position++;
            }
        }
        byte[] raw = bytes.toByteArray();
        String utf8 = new String(raw, StandardCharsets.UTF_8);
        return new Name(utf8.indexOf('�') >= 0 ? new String(raw, StandardCharsets.ISO_8859_1) : utf8);
    }

    private Bytes literal() {
        position++;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int open = 1;
        while (position < end) {
            int c = data[position++] & 0xFF;
            if (c == '(') {
                open++;
            } else if (c == ')') {
                if (--open == 0) {
                    break;
                }
            } else if (c == '\\' && position < end) {
                int e = data[position++] & 0xFF;
                switch (e) {
                    case 'n' -> bytes.write('\n');
                    case 'r' -> bytes.write('\r');
                    case 't' -> bytes.write('\t');
                    case 'b' -> bytes.write('\b');
                    case 'f' -> bytes.write('\f');
                    case '\r' -> {
                        if (position < end && data[position] == '\n') {
                            position++;
                        }
                    }
                    case '\n' -> {
                        // a line continued
                    }
                    default -> {
                        if (e >= '0' && e <= '7') {
                            int octal = e - '0';
                            for (int i = 0; i < 2 && position < end && data[position] >= '0'
                                    && data[position] <= '7'; i++) {
                                octal = octal * 8 + data[position++] - '0';
                            }
                            bytes.write(octal & 0xFF);
                        } else {
                            bytes.write(e);
                        }
                    }
                }
                continue;
            } else if (c == '\r') {
                // an end of line in a string is a line feed, whichever the file writes
                if (position < end && data[position] == '\n') {
                    position++;
                }
                c = '\n';
            }
            bytes.write(c);
        }
        return new Bytes(bytes.toByteArray());
    }

    private Bytes hex() {
        position++;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int high = -1;
        while (position < end) {
            int c = data[position++] & 0xFF;
            if (c == '>') {
                break;
            }
            int digit = hexDigit((byte) c);
            if (digit < 0) {
                continue;
            }
            if (high < 0) {
                high = digit;
            } else {
                bytes.write(high * 16 + digit);
                high = -1;
            }
        }
        if (high >= 0) {
            bytes.write(high * 16);   // an odd count: the last digit followed by 0
        }
        return new Bytes(bytes.toByteArray());
    }

    static int hexDigit(byte b) {
        int c = b & 0xFF;
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return -1;
    }

    /** A number, a reference "n g R", true, false, null, or a keyword. */
    private Object word() {
        int start = position;
        while (position < end) {
            int c = data[position] & 0xFF;
            if (isWhitespace(c) || isDelimiter(c)) {
                break;
            }
            position++;
        }
        if (position == start) {
            position++;   // a byte no token starts with
            return new Operator(String.valueOf((char) (data[start] & 0xFF)));
        }
        String word = new String(data, start, position - start, StandardCharsets.ISO_8859_1);
        int first = word.charAt(0);
        if (first >= '0' && first <= '9' || first == '+' || first == '-' || first == '.') {
            Number number = number(word);
            if (number == null) {
                return new Operator(word);
            }
            if (number instanceof Long generationOf && word.indexOf('.') < 0) {
                Ref ref = reference(generationOf);
                if (ref != null) {
                    return ref;
                }
            }
            return number;
        }
        return switch (word) {
            case "true" -> Boolean.TRUE;
            case "false" -> Boolean.FALSE;
            case "null" -> null;
            default -> new Operator(word);
        };
    }

    /** After an integer: "g R" makes it a reference; otherwise the position is left where it was. */
    private Ref reference(long number) {
        int saved = position;
        skipWhitespace();
        int start = position;
        while (position < end && data[position] >= '0' && data[position] <= '9') {
            position++;
        }
        if (position > start && position - start < 7) {
            int generation = Integer.parseInt(new String(data, start, position - start, StandardCharsets.ISO_8859_1));
            skipWhitespace();
            if (position < end && data[position] == 'R'
                    && (position + 1 >= end || isWhitespace(data[position + 1] & 0xFF)
                    || isDelimiter(data[position + 1] & 0xFF))) {
                position++;
                if (number >= 0 && number <= Integer.MAX_VALUE) {
                    return new Ref((int) number, generation);
                }
            }
        }
        position = saved;
        return null;
    }

    private static Number number(String word) {
        try {
            if (word.indexOf('.') < 0) {
                return Long.parseLong(word.startsWith("+") ? word.substring(1) : word);
            }
            return Double.parseDouble(word);
        } catch (NumberFormatException _) {
            // "--5", "1.2.3": what some writers leave, read as a number all the same
            return word.matches("[+-]*[0-9.]+") ? lenient(word) : null;
        }
    }

    private static Number lenient(String word) {
        boolean negative = word.startsWith("-");
        String digits = word.replaceAll("^[+-]+", "");
        int dot = digits.indexOf('.');
        if (dot >= 0) {
            digits = digits.substring(0, dot + 1) + digits.substring(dot + 1).replace(".", "");
        }
        try {
            double value = digits.equals(".") || digits.isEmpty() ? 0 : Double.parseDouble(digits);
            return negative ? -value : value;
        } catch (NumberFormatException _) {
            return null;
        }
    }

    /** Whether the bytes at the position are this keyword, followed by whitespace, a delimiter or the end. */
    boolean at(String keyword) {
        skipWhitespace();
        int length = keyword.length();
        if (position + length > end) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (data[position + i] != keyword.charAt(i)) {
                return false;
            }
        }
        return position + length == end || isWhitespace(data[position + length] & 0xFF)
                || isDelimiter(data[position + length] & 0xFF);
    }
}
