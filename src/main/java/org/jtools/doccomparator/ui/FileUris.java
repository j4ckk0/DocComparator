package org.jtools.doccomparator.ui;

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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Files dropped from a Linux desktop arrive as a text/uri-list — "file:///home/me/My%20report.docx", one a line — rather
 * than as a list of files. Decoded here by hand: the JDK's URI classes are networking classes, which the program does
 * not use (README, "No network"). Only file URIs are kept, and of those only the ones on this machine: a file
 * URI naming another host is not a file the program may open.
 */
final class FileUris {

    private FileUris() {
    }

    static List<Path> paths(String uriList) {
        List<Path> paths = new ArrayList<>();
        for (String line : uriList.split("\r?\n")) {
            String uri = line.strip();
            if (uri.isEmpty() || uri.startsWith("#") || !uri.regionMatches(true, 0, "file:", 0, 5)) {
                continue;
            }
            String path = uri.substring(5);
            if (path.startsWith("//")) {   // file://host/path: the host empty, or this one's name, "localhost"
                int slash = path.indexOf('/', 2);
                String host = slash < 0 ? path.substring(2) : path.substring(2, slash);
                if (!host.isEmpty() && !host.equalsIgnoreCase("localhost")) {
                    continue;
                }
                path = slash < 0 ? "/" : path.substring(slash);
            }
            path = decode(path);
            if (path.matches("/[A-Za-z]:.*")) {   // Windows: /C:/Users/… is C:/Users/…
                path = path.substring(1);
            }
            try {
                paths.add(Path.of(path));
            } catch (IllegalArgumentException e) {   // not a path here: InvalidPathException
                // left out
            }
        }
        return paths;
    }

    /** The %XX escapes of a URI's path decoded, as bytes of UTF-8. */
    static String decode(String text) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '%' && i + 2 < text.length() && hex(text.charAt(i + 1)) >= 0 && hex(text.charAt(i + 2)) >= 0) {
                bytes.write(hex(text.charAt(i + 1)) * 16 + hex(text.charAt(i + 2)));
                i += 2;
            } else {
                byte[] encoded = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                bytes.write(encoded, 0, encoded.length);
            }
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static int hex(char c) {
        return Character.digit(c, 16);
    }
}
