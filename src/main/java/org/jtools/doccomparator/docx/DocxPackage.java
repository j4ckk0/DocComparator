package org.jtools.doccomparator.docx;

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
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;

import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import org.jtools.doccomparator.i18n.I18n;

/**
 * A .docx as what it is: a zip of XML parts, linked by relationships (Open Packaging Conventions). Gives a part's
 * relationships and opens a part; the file itself is never changed.
 */
final class DocxPackage implements AutoCloseable {

    /**
     * The most one part may hold once unzipped: far beyond any document, short of what a crafted zip could inflate to.
     */
    static final long LARGEST_PART = 512L << 20;

    private final String fileName;
    private final ZipFile zip;
    private final Map<String, ZipEntry> entries = new HashMap<>();   // by name in lower case: part names ignore case

    private DocxPackage(String fileName, ZipFile zip) {
        this.fileName = fileName;
        this.zip = zip;
        zip.stream().forEach(entry -> entries.put(entry.getName().toLowerCase(Locale.ROOT), entry));
    }

    static DocxPackage open(Path file) throws IOException {
        String fileName = String.valueOf(file.getFileName());
        try {
            return new DocxPackage(fileName, new ZipFile(file.toFile()));
        } catch (ZipException e) {
            throw new DocxException(I18n.text("error.notZip", fileName));
        }
    }

    /** The main document part ("word/document.xml" as Word writes it), from the package's relationships. */
    String mainDocument() throws DocxException {
        Optional<String> main = target(relationships(""), "officeDocument");
        if (main.isPresent() && has(main.get())) {
            return main.get();
        }
        if (has("word/document.xml")) {
            return "word/document.xml";
        }
        throw new DocxException(I18n.text("error.noMainDocument", fileName));
    }

    /** The relationships of a part ("" for the package's own), none when it has no relationship part. */
    List<Relationship> relationships(String part) throws DocxException {
        String relationshipsPart = relationshipsPart(part);
        List<Relationship> relationships = new ArrayList<>();
        if (!has(relationshipsPart)) {
            return relationships;
        }
        try (XmlCursor xml = open(relationshipsPart)) {
            xml.root("Relationships");
            while (xml.nextChild()) {
                if (xml.localName().equals("Relationship")) {
                    String id = xml.plainAttribute("Id");
                    String type = xml.plainAttribute("Type");
                    String target = xml.plainAttribute("Target");
                    boolean external = "External".equals(xml.plainAttribute("TargetMode"));
                    if (id != null && type != null && target != null) {
                        relationships.add(new Relationship(id, type, external ? target : resolve(part, target),
                                external));
                    }
                }
                xml.skip();
            }
        }
        return relationships;
    }

    /** The part the first relationship of one of these kinds targets, when it is a part of the file. */
    static Optional<String> target(List<Relationship> relationships, String... kinds) {
        return relationships.stream()
                .filter(r -> r.isPart() && Arrays.stream(kinds).anyMatch(r::isA))
                .map(Relationship::target).findFirst();
    }

    boolean has(String part) {
        return entries.containsKey(part.toLowerCase(Locale.ROOT));
    }

    /** A cursor on the part, before its root. */
    XmlCursor open(String part) throws DocxException {
        ZipEntry entry = entries.get(part.toLowerCase(Locale.ROOT));
        if (entry == null) {
            throw new DocxException(fileName, I18n.text("error.noPart", part));
        }
        try {
            return XmlCursor.open(new LimitedInputStream(zip.getInputStream(entry), part), part);
        } catch (IOException e) {
            throw new DocxException(fileName, I18n.text("error.unreadable", part, e.getMessage()));
        }
    }

    /** The binary parts read so far, by name in lower case. */
    private final Map<String, Binary> binaries = new HashMap<>();

    /** A binary part read: its SHA-256 in hexadecimal, and its bytes, null when larger than the size kept. */
    record Binary(String digest, byte[] bytes) {
    }

    /**
     * A binary part — a picture — read once: its digest computed as it goes, its bytes kept up to keptBytes, the rest
     * only counted; past LARGEST_PART, refused as any part.
     */
    Binary binary(String part, int keptBytes) throws DocxException {
        Binary known = binaries.get(part.toLowerCase(Locale.ROOT));
        if (known != null) {
            return known;   // an image used again — an icon in every step — read and kept once
        }
        ZipEntry entry = entries.get(part.toLowerCase(Locale.ROOT));
        if (entry == null) {
            throw new DocxException(fileName, I18n.text("error.noPart", part));
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every JDK has SHA-256", e);
        }
        ByteArrayOutputStream kept = new ByteArrayOutputStream();
        boolean keeping = true;
        try (InputStream in = new LimitedInputStream(zip.getInputStream(entry), part)) {
            byte[] buffer = new byte[64 * 1024];
            for (int n = in.read(buffer); n >= 0; n = in.read(buffer)) {
                digest.update(buffer, 0, n);
                if (keeping) {
                    if (kept.size() + n > keptBytes) {
                        keeping = false;
                        kept = null;
                    } else {
                        kept.write(buffer, 0, n);
                    }
                }
            }
        } catch (IOException e) {
            throw new DocxException(fileName, I18n.text("error.unreadable", part, e.getMessage()));
        }
        Binary binary = new Binary(HexFormat.of().formatHex(digest.digest()), keeping ? kept.toByteArray() : null);
        binaries.put(part.toLowerCase(Locale.ROOT), binary);
        return binary;
    }

    /** "word/document.xml" → "word/_rels/document.xml.rels"; "" (the package) → "_rels/.rels". */
    private static String relationshipsPart(String part) {
        int slash = part.lastIndexOf('/');
        return part.substring(0, slash + 1) + "_rels/" + part.substring(slash + 1) + ".rels";
    }

    /** A target relative to the part that links to it, or absolute ("/word/styles.xml"), as a part name. */
    static String resolve(String sourcePart, String target) {
        String path = target.startsWith("/") ? target.substring(1)
                : sourcePart.substring(0, sourcePart.lastIndexOf('/') + 1) + target;
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : path.split("/")) {
            if (segment.equals("..")) {
                segments.pollLast();
            } else if (!segment.isEmpty() && !segment.equals(".")) {
                segments.addLast(segment);
            }
        }
        return String.join("/", segments);
    }

    @Override
    public void close() throws IOException {
        zip.close();
    }

    /** Stops reading a part past LARGEST_PART. */
    private static final class LimitedInputStream extends FilterInputStream {

        private final String part;
        private long left = LARGEST_PART;

        LimitedInputStream(InputStream in, String part) {
            super(in);
            this.part = part;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = super.read(buffer, offset, length);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(int bytes) throws DocxException {
            left -= bytes;
            if (left < 0) {
                throw new DocxException(part, I18n.text("error.tooLarge", LARGEST_PART >> 20));
            }
        }
    }
}
