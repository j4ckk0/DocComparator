/*
 * Copyright 2026 j4ckk0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jtools.doccomparator.ui;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A font family of a document, as this machine can show it: itself if installed; else a family made to take its
 * place, with the same widths (Carlito for Calibri, Liberation for Arial and Times New Roman…), which Linux often has;
 * else Java's serif, sans serif or monospaced font, after the name.
 */
final class FontFamilies {

    private static final Map<String, List<String>> SUBSTITUTES = Map.of(
            "calibri", List.of("Carlito"),
            "cambria", List.of("Caladea"),
            "arial", List.of("Liberation Sans", "Arimo"),
            "helvetica", List.of("Liberation Sans", "Arimo"),
            "times new roman", List.of("Liberation Serif", "Tinos"),
            "courier new", List.of("Liberation Mono", "Cousine"),
            "aptos", List.of("Carlito"));

    private static Set<String> installed;

    private FontFamilies() {
    }

    static String resolve(String family) {
        if (family == null) {
            return Font.SANS_SERIF;
        }
        String name = family.toLowerCase(Locale.ROOT);
        if (installed().contains(name)) {
            return family;
        }
        for (String substitute : SUBSTITUTES.getOrDefault(name, List.of())) {
            if (installed().contains(substitute.toLowerCase(Locale.ROOT))) {
                return substitute;
            }
        }
        if (name.matches(".*(mono|courier|consolas|console|code).*")) {
            return Font.MONOSPACED;
        }
        if (name.matches(".*(times|cambria|georgia|garamond|book|serif|roman|palatino|caslon|minion).*")
                && !name.contains("sans")) {
            return Font.SERIF;
        }
        return Font.SANS_SERIF;
    }

    private static synchronized Set<String> installed() {
        if (installed == null) {
            installed = new HashSet<>();
            for (String family : GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames(Locale.ROOT)) {
                installed.add(family.toLowerCase(Locale.ROOT));
            }
        }
        return installed;
    }
}
