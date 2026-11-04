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
package org.jtools.doccomparator.docx;

import java.util.HashMap;
import java.util.Map;
import org.jtools.doccomparator.document.Formatting;

/**
 * The document's lists (numbering.xml): for a list (numId) and a level, the numbering it shows. A list is an
 * instance of an abstract list, which may change some of its levels (w:lvlOverride).
 */
final class Numbering {

    /** A level: its number's format and text, and the paragraph properties it gives its items (their indent). */
    record Level(String format, String text, Formatting paragraph) {
    }

    static final Numbering NONE = new Numbering();

    private final Map<String, Map<Integer, Level>> abstractLevels = new HashMap<>();   // by abstractNumId, then level
    private final Map<String, String> abstractOfList = new HashMap<>();                // numId → abstractNumId
    private final Map<String, Map<Integer, Level>> overriddenLevels = new HashMap<>(); // by numId, then level

    /** The numbering of this level of this list; null when the document does not define it. */
    Level level(String listId, int level) {
        Level overridden = overriddenLevels.getOrDefault(listId, Map.of()).get(level);
        if (overridden != null) {
            return overridden;
        }
        return abstractLevels.getOrDefault(abstractOfList.get(listId), Map.of()).get(level);
    }

    static Numbering read(XmlCursor xml) throws DocxException {
        Numbering numbering = new Numbering();
        xml.root("numbering");
        while (xml.nextChild()) {
            if (xml.isWord("abstractNum")) {
                String id = xml.wordAttribute("abstractNumId");
                Map<Integer, Level> levels = new HashMap<>();
                while (xml.nextChild()) {
                    if (xml.isWord("lvl")) {
                        readLevel(xml, levels);
                    } else {
                        xml.skip();
                    }
                }
                numbering.abstractLevels.put(id, levels);
            } else if (xml.isWord("num")) {
                String listId = xml.wordAttribute("numId");
                Map<Integer, Level> overridden = new HashMap<>();
                while (xml.nextChild()) {
                    if (xml.isWord("abstractNumId")) {
                        numbering.abstractOfList.put(listId, xml.wordAttribute("val"));
                        xml.skip();
                    } else if (xml.isWord("lvlOverride")) {
                        while (xml.nextChild()) {
                            if (xml.isWord("lvl")) {
                                readLevel(xml, overridden);
                            } else {
                                xml.skip();   // w:startOverride: where the count starts, not how it shows
                            }
                        }
                    } else {
                        xml.skip();
                    }
                }
                numbering.overriddenLevels.put(listId, overridden);
            } else {
                xml.skip();
            }
        }
        return numbering;
    }

    private static void readLevel(XmlCursor xml, Map<Integer, Level> levels) throws DocxException {
        int level = parseLevel(xml.wordAttribute("ilvl"));
        String format = null;
        String text = null;
        Formatting paragraph = Formatting.NONE;
        while (xml.nextChild()) {
            if (xml.isWord("numFmt")) {
                format = xml.wordAttribute("val");
            } else if (xml.isWord("lvlText")) {
                text = xml.wordAttribute("val");
            } else if (xml.isWord("pPr")) {
                paragraph = FormattingReader.read(xml, () -> { });   // consumed
                continue;
            }
            xml.skip();
        }
        levels.put(level, new Level(format, text, paragraph));
    }

    /** A list level as Word writes it, from 0; 0 when unreadable. */
    static int parseLevel(String level) {
        try {
            return level == null ? 0 : Integer.parseInt(level.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
