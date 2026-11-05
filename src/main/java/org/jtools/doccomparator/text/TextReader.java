package org.jtools.doccomparator.text;

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
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jtools.doccomparator.document.Block;
import org.jtools.doccomparator.document.Formatting;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.Run;
import org.jtools.doccomparator.document.Story;
import org.jtools.doccomparator.document.StyleSheet;
import org.jtools.doccomparator.document.Template;
import org.jtools.doccomparator.document.WordDocument;

/**
 * Reads plain text — pasted, dropped, or a text file — into a WordDocument, so it compares as a document does: a body
 * alone, a paragraph a line, without style nor formatting, an empty line an empty paragraph. Markdown is read as the
 * text it is, its marks compared as words.
 */
public final class TextReader {

    /** The files read as text, by their extension, rather than as Word documents. */
    public static final List<String> EXTENSIONS = List.of("txt", "text", "md", "markdown");

    /** What text written on Windows before UTF-8 most likely is, when it is not UTF-8. */
    private static final Charset WINDOWS_LATIN = Charset.forName("windows-1252");

    private TextReader() {
    }

    /** Whether this file is read as text: its extension is one of EXTENSIONS. */
    public static boolean reads(Path path) {
        String name = String.valueOf(path.getFileName()).toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot >= 0 && EXTENSIONS.contains(name.substring(dot + 1));
    }

    /** A text file, named by its file name. */
    public static WordDocument read(Path path) throws IOException {
        return read(String.valueOf(path.getFileName()), decode(Files.readAllBytes(path)));
    }

    /**
     * A text file's characters: in the encoding its byte order mark names; without one, UTF-8, or Windows-1252 where
     * the bytes are not UTF-8 (a file an older Windows program wrote).
     */
    static String decode(byte[] bytes) {
        if (starts(bytes, 0xEF, 0xBB, 0xBF)) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        if (starts(bytes, 0xFF, 0xFE)) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        if (starts(bytes, 0xFE, 0xFF)) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException _) {
            return new String(bytes, WINDOWS_LATIN);
        }
    }

    private static boolean starts(byte[] bytes, int... mark) {
        if (bytes.length < mark.length) {
            return false;
        }
        for (int i = 0; i < mark.length; i++) {
            if ((bytes[i] & 0xFF) != mark[i]) {
                return false;
            }
        }
        return true;
    }

    /** The text as a document named so; its last line break ends its last paragraph, it does not start one. */
    public static WordDocument read(String name, String text) {
        String[] lines = text.split("\r\n|\r|\n", -1);
        int count = lines.length > 1 && lines[lines.length - 1].isEmpty() ? lines.length - 1 : lines.length;
        List<Block> paragraphs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            List<Run> runs = lines[i].isEmpty() ? List.of() : List.of(new Run(lines[i], null, Formatting.NONE));
            paragraphs.add(new Paragraph(null, Formatting.NONE, 0, null, false, runs));
        }
        return new WordDocument(name, new Story(Story.Name.BODY, paragraphs), List.of(),
                List.of(), StyleSheet.NONE, Template.NONE, false);
    }
}
