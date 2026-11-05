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
import java.util.List;
import java.util.Map;

/**
 * The values a PDF is made of, as Java holds them: null, Boolean, Long or Double for numbers, Name, Bytes for strings,
 * List for arrays, Map for dictionaries (keys without their slash), Stream, Ref for a reference to an object of the
 * file, and Operator in a content stream.
 */
final class PdfObjects {

    private PdfObjects() {
    }

    /** A name, without its slash, its #xx escapes undone. */
    record Name(String value) {
    }

    /** A string's bytes, as the file holds them (decrypted): their meaning is the font's or the text's encoding. */
    record Bytes(byte[] value) {

        String latin1() {
            return new String(value, StandardCharsets.ISO_8859_1);
        }
    }

    /** "n g R": an object of the file, by its number and generation. */
    record Ref(int number, int generation) {
    }

    /** A stream: its dictionary and its bytes as the file holds them (decrypted, not decoded). */
    record Stream(Map<String, Object> dictionary, byte[] raw) {
    }

    /** A content stream's operator: "Tj", "BT", "cm". */
    record Operator(String name) {
    }

    static Map<String, Object> dictionary(Object value) {
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dictionary = (Map<String, Object>) map;
            return dictionary;
        }
        if (value instanceof Stream stream) {
            return stream.dictionary();
        }
        return null;
    }

    static List<?> array(Object value) {
        return value instanceof List<?> list ? list : null;
    }

    static String name(Object value) {
        return value instanceof Name name ? name.value() : null;
    }

    static double number(Object value, double otherwise) {
        return value instanceof Number number ? number.doubleValue() : otherwise;
    }

    static int integer(Object value, int otherwise) {
        return value instanceof Number number ? (int) Math.max(Integer.MIN_VALUE,
                Math.min(Integer.MAX_VALUE, number.longValue())) : otherwise;
    }
}
