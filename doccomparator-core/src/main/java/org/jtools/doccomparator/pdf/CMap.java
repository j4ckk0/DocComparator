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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jtools.doccomparator.pdf.PdfObjects.Bytes;
import org.jtools.doccomparator.pdf.PdfObjects.Name;
import org.jtools.doccomparator.pdf.PdfObjects.Operator;

/**
 * A CMap a file holds: how a string's bytes split into codes (its code space ranges), and what each code is — the
 * characters it stands for (a font's ToUnicode), or a glyph's number in a composite font (CID, its Encoding).
 */
final class CMap {

    /** The widest range a bfrange or cidrange is read over: wider is a crafted file, not a font. */
    private static final int WIDEST_RANGE = 1 << 16;

    private record Range(int length, long low, long high) {
    }

    private final List<Range> codeSpace = new ArrayList<>();
    private final Map<Long, String> characters = new HashMap<>();
    private final Map<Long, Integer> cids = new HashMap<>();
    private final List<long[]> cidRanges = new ArrayList<>();   // length, low, high, first CID

    static CMap parse(byte[] bytes) {
        CMap map = new CMap();
        Lexer lexer = new Lexer(bytes, 0);
        List<Object> operands = new ArrayList<>();
        while (true) {
            Object value = lexer.next();
            if (value == Lexer.END) {
                break;
            }
            if (!(value instanceof Operator operator)) {
                operands.add(value);
                continue;
            }
            switch (operator.name()) {
                case "endcodespacerange" -> {
                    for (int i = 0; i + 1 < operands.size(); i += 2) {
                        if (operands.get(i) instanceof Bytes low && operands.get(i + 1) instanceof Bytes high
                                && low.value().length == high.value().length && low.value().length <= 4
                                && low.value().length > 0) {
                            map.codeSpace.add(new Range(low.value().length, code(low.value()), code(high.value())));
                        }
                    }
                }
                case "endbfchar" -> {
                    for (int i = 0; i + 1 < operands.size(); i += 2) {
                        if (operands.get(i) instanceof Bytes source && source.value().length <= 4) {
                            String target = target(operands.get(i + 1));
                            if (target != null) {
                                map.characters.put(key(source.value()), target);
                            }
                        }
                    }
                }
                case "endbfrange" -> {
                    for (int i = 0; i + 2 < operands.size(); i += 3) {
                        if (operands.get(i) instanceof Bytes low && operands.get(i + 1) instanceof Bytes high
                                && low.value().length <= 4 && low.value().length == high.value().length) {
                            map.range(low.value(), code(high.value()), operands.get(i + 2));
                        }
                    }
                }
                case "endcidchar" -> {
                    for (int i = 0; i + 1 < operands.size(); i += 2) {
                        if (operands.get(i) instanceof Bytes source && source.value().length <= 4
                                && operands.get(i + 1) instanceof Long cid) {
                            map.cids.put(key(source.value()), cid.intValue());
                        }
                    }
                }
                case "endcidrange" -> {
                    for (int i = 0; i + 2 < operands.size(); i += 3) {
                        if (operands.get(i) instanceof Bytes low && operands.get(i + 1) instanceof Bytes high
                                && low.value().length <= 4 && operands.get(i + 2) instanceof Long cid) {
                            map.cidRanges.add(new long[] {low.value().length, code(low.value()), code(high.value()),
                                cid});
                        }
                    }
                }
                default -> {
                    // begin…, def, usecmap and the rest: only what is between them counts
                }
            }
            operands.clear();
        }
        return map;
    }

    private void range(byte[] low, long high, Object target) {
        long first = code(low);
        if (high < first || high - first >= WIDEST_RANGE) {
            return;
        }
        if (target instanceof List<?> list) {
            for (int k = 0; k < list.size() && first + k <= high; k++) {
                String character = target(list.get(k));
                if (character != null) {
                    characters.put(key(low.length, first + k), character);
                }
            }
        } else if (target instanceof Bytes bytes && bytes.value().length > 0) {
            byte[] destination = bytes.value().clone();
            for (long code = first; code <= high; code++) {
                characters.put(key(low.length, code), utf16(destination));
                increment(destination);
            }
        }
    }

    /** The last UTF-16 unit of a destination plus one, as a bfrange runs. */
    private static void increment(byte[] destination) {
        for (int i = destination.length - 1; i >= 0; i--) {
            if (++destination[i] != 0) {
                return;
            }
        }
    }

    private static String target(Object value) {
        if (value instanceof Bytes bytes) {
            return utf16(bytes.value());
        }
        if (value instanceof Name name) {
            return Encodings.glyph(name.value());
        }
        return null;
    }

    private static String utf16(byte[] bytes) {
        if (bytes.length == 1) {
            return String.valueOf((char) (bytes[0] & 0xFF));
        }
        return new String(bytes, StandardCharsets.UTF_16BE);
    }

    private static long code(byte[] bytes) {
        long code = 0;
        for (byte b : bytes) {
            code = (code << 8) | (b & 0xFF);
        }
        return code;
    }

    private static long key(byte[] bytes) {
        return key(bytes.length, code(bytes));
    }

    private static long key(int length, long code) {
        return ((long) length << 32) | code;
    }

    boolean hasCodeSpace() {
        return !codeSpace.isEmpty();
    }

    /** The length of the code that starts here: the code space range it falls in, else the shortest one. */
    int codeLength(byte[] bytes, int at) {
        int shortest = 4;
        for (int length = 1; length <= 4 && at + length <= bytes.length; length++) {
            long code = 0;
            for (int i = 0; i < length; i++) {
                code = (code << 8) | (bytes[at + i] & 0xFF);
            }
            for (Range range : codeSpace) {
                if (range.length() == length && code >= range.low() && code <= range.high()) {
                    return length;
                }
            }
        }
        for (Range range : codeSpace) {
            shortest = Math.min(shortest, range.length());
        }
        return codeSpace.isEmpty() ? 1 : shortest;
    }

    /** The characters a code stands for, null when it is not mapped. */
    String characters(int length, long code) {
        String found = characters.get(key(length, code));
        if (found == null && length > 1) {
            // a one-byte code space whose codes the mapping wrote on two bytes, or the reverse
            found = characters.get(key(length == 2 ? 1 : 2, code));
        }
        return found;
    }

    /** The CID of a code, -1 when it is not mapped. */
    int cid(int length, long code) {
        Integer cid = cids.get(key(length, code));
        if (cid != null) {
            return cid;
        }
        for (long[] range : cidRanges) {
            if (range[0] == length && code >= range[1] && code <= range[2]) {
                return (int) (range[3] + code - range[1]);
            }
        }
        return -1;
    }
}
