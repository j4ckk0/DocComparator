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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jtools.doccomparator.document.Picture;

/**
 * The pictures of the part being read — the body, a header, the footnotes —, through that part's own relationships:
 * read from the package. A picture whose relationship is external (a file on a share, a web address) is never fetched,
 * whether it is linked or said to be embedded: a picture of its own kind stands for it (README, "No network").
 */
final class Pictures {

    /** The largest picture whose bytes are kept, to be shown; past it, only its digest. */
    static final int KEPT_BYTES = 32 << 20;

    /** No picture to be had: a part read without relationships. */
    static final Pictures NONE = new Pictures(null, List.of());

    private final DocxPackage docx;
    private final Map<String, Relationship> relationships = new HashMap<>();

    Pictures(DocxPackage docx, List<Relationship> relationships) {
        this.docx = docx;
        for (Relationship relationship : relationships) {
            this.relationships.put(relationship.id(), relationship);
        }
    }

    /**
     * The picture a drawing names — by r:embed (in the package) or r:link (outside it), or a VML r:id — at the size
     * and with the description it gives.
     */
    Picture image(String embedId, String linkId, String description, float width, float height) throws DocxException {
        String id = embedId != null ? embedId : linkId;
        Relationship relationship = id == null ? null : relationships.get(id);
        if (relationship == null || docx == null) {
            return new Picture(Picture.Kind.UNSUPPORTED, description, width, height, null, "missing:" + id, null);
        }
        String format = extension(relationship.target());
        if (!relationship.isPart()) {   // outside the document: never read
            return new Picture(Picture.Kind.LINKED, description, width, height, format,
                    "link:" + relationship.target(), null);
        }
        if (!docx.has(relationship.target())) {
            return new Picture(Picture.Kind.UNSUPPORTED, description, width, height, format,
                    "missing:" + relationship.target(), null);
        }
        DocxPackage.Binary binary = docx.binary(relationship.target(), KEPT_BYTES);
        return binary.bytes() == null
                ? new Picture(Picture.Kind.TOO_LARGE, description, width, height, format, binary.digest(), null)
                : new Picture(Picture.Kind.IMAGE, description, width, height, format, binary.digest(), binary.bytes());
    }

    /** What the program cannot show — a chart, a diagram —: a picture saying so, its size kept. */
    static Picture unsupported(String what, String description, float width, float height) {
        return new Picture(Picture.Kind.UNSUPPORTED, description, width, height, what,
                what + ":" + description + ":" + width + "x" + height, null);
    }

    private static String extension(String target) {
        int dot = target.lastIndexOf('.');
        return dot < 0 || dot < target.lastIndexOf('/') ? null : target.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
