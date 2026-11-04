package org.jtools.doccomparator.compare;

import java.util.HashMap;
import java.util.Map;

/** A number for each distinct string, the same for both documents: sequences of them compare fast. */
final class Vocabulary {

    private final Map<String, Integer> codes = new HashMap<>();

    int code(String text) {
        return codes.computeIfAbsent(text, unused -> codes.size() + 1);   // 0 is left for what is not compared
    }
}
