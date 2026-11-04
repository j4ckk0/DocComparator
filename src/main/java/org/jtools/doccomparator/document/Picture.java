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
package org.jtools.doccomparator.document;

import java.util.Objects;

/**
 * A picture of a paragraph, in a run of its own whose text is one object replacement character (U+FFFC): what it is,
 * the size it is shown at, its description, and its image — the file's bytes, kept as they are, never decoded here.
 *
 * @param kind        an image of the document; one linked, outside it (never fetched); one the program cannot show
 *                    (a chart, a diagram…); one too large to keep
 * @param description its alternative text, or its title, or its name; null for none
 * @param width       its width as shown, in points; 0 when the document does not say
 * @param height      its height as shown, in points; 0 when the document does not say
 * @param format      what its file says it is: its extension ("png", "emf"…); null when unknown
 * @param digest      its SHA-256, in hexadecimal: the same picture has the same; for a picture without bytes, what
 *                    identifies it (the link, the kind)
 * @param bytes       the image file, for an image kept; null otherwise
 */
public record Picture(Kind kind, String description, float width, float height, String format, String digest,
                      byte[] bytes) {

    public enum Kind {
        IMAGE, LINKED, UNSUPPORTED, TOO_LARGE
    }

    /** The character that stands for a picture in its paragraph's text. */
    public static final char IN_TEXT = '￼';

    /** Two pictures are the same when their images are: the same digest, the same kind, the same size. */
    @Override
    public boolean equals(Object other) {
        return other instanceof Picture that && kind == that.kind && digest.equals(that.digest)
                && width == that.width && height == that.height;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, digest, width, height);
    }

    @Override
    public String toString() {
        return "Picture[" + kind + ", " + format + ", " + width + "x" + height + ", " + description + "]";
    }
}
