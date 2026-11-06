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
import org.jtools.doccomparator.document.Block;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.Document;
import org.jtools.doccomparator.i18n.I18n;
import org.jtools.doccomparator.reader.AbstractDocumentReader;

/**
 * Reads plain text — pasted, dropped, or a text file — into a Document, so it compares as a document does: a body
 * alone, a paragraph a line, without style nor formatting, an empty line an empty paragraph. Markdown is read as the
 * text it is, its marks compared as words.
 */
public final class TextReader extends AbstractDocumentReader {


    /** What text written on Windows before UTF-8 most likely is, when it is not UTF-8. */
    private static final Charset WINDOWS_LATIN = Charset.forName("windows-1252");

    /** The reader of .txt, .text, .md, .markdown files, found as a service (DocumentReaders). */
    public TextReader() {
        super(40, "txt", "text", "md", "markdown");
    }

    @Override
    public String formatName() {
        return I18n.text("format.text");
    }

    @Override
    public String readingNote() {
        return I18n.text("note.textFile");
    }


    /** A text file, named by its file name. */
    @Override
    protected Document readFile(Path path) throws IOException {
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
    public static Document read(String name, String text) {
        String[] lines = text.split("\r\n|\r|\n", -1);
        int count = lines.length > 1 && lines[lines.length - 1].isEmpty() ? lines.length - 1 : lines.length;
        List<Block> paragraphs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            paragraphs.add(Paragraph.of(lines[i]));
        }
        return Document.of(name, paragraphs);
    }
}
