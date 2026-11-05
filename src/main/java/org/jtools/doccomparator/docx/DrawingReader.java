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

import java.util.ArrayList;
import java.util.List;
import org.jtools.doccomparator.document.Picture;
import org.jtools.doccomparator.ooxml.OoxmlException;
import org.jtools.doccomparator.ooxml.XmlCursor;

/**
 * A drawing, a VML picture or an object (w:drawing, w:pict, w:object), all of it: its pictures (a:blip, the bitmap Word
 * keeps beside an SVG included; v:imagedata), at the size (wp:extent, a VML shape's style) and with the description
 * (wp:docPr) it gives; a chart or a diagram, which cannot be shown, as a picture saying so. Its text boxes, wherever
 * they are in it, go to the story's reader.
 */
final class DrawingReader {

    /** EMUs, DrawingML's lengths: 12700 to the point. */
    private static final float EMUS_PER_POINT = 12700;

    private final Pictures pictures;
    private final XmlCursor.ElementReader textBoxes;
    private final List<Picture> found = new ArrayList<>();
    private float width;
    private float height;
    private String description;
    /** What the drawing is when it is not a picture: "chart", "diagram"; null otherwise. */
    private String unsupported;

    private DrawingReader(Pictures pictures, XmlCursor.ElementReader textBoxes) {
        this.pictures = pictures;
        this.textBoxes = textBoxes;
    }

    /** The pictures of the element the cursor is on, consumed; each w:txbxContent in it handed to textBoxes. */
    static List<Picture> read(XmlCursor xml, Pictures pictures, XmlCursor.ElementReader textBoxes)
            throws OoxmlException {
        DrawingReader drawing = new DrawingReader(pictures, textBoxes);
        drawing.scan(xml);
        if (drawing.found.isEmpty() && drawing.unsupported != null) {
            drawing.found.add(Pictures.unsupported(drawing.unsupported, drawing.description, drawing.width,
                    drawing.height));
        }
        return drawing.found;
    }

    private void scan(XmlCursor xml) throws OoxmlException {
        xml.forChildren(child -> {
            if (child.isWord("txbxContent")) {
                textBoxes.read(child);
                return;
            }
            if (child.isMarkupCompatibility("AlternateContent")) {
                child.firstChoice(this::scan);
                return;
            }
            switch (child.localName()) {
                case "extent" -> {
                    width = emus(child.plainAttribute("cx"));
                    height = emus(child.plainAttribute("cy"));
                }
                case "docPr" -> description = first(child.plainAttribute("descr"), child.plainAttribute("title"),
                        child.plainAttribute("name"));
                case "graphicData" -> {
                    String uri = child.plainAttribute("uri");
                    if (uri != null && (uri.contains("/chart") || uri.contains("/diagram"))) {   // chartex too
                        unsupported = uri.contains("/chart") ? "chart" : "diagram";
                    }
                }
                case "blip" -> found.add(pictures.image(child.relationshipAttribute("embed"),
                        child.relationshipAttribute("link"), description, width, height));
                case "shape", "rect" -> vmlSize(child.plainAttribute("style"));
                case "imagedata" -> found.add(pictures.image(child.relationshipAttribute("id"), null, description,
                        width, height));
                default -> {
                }
            }
            scan(child);
        });
    }

    private static float emus(String value) {
        try {
            return value == null ? 0 : Long.parseLong(value.trim()) / EMUS_PER_POINT;
        } catch (NumberFormatException _) {
            return 0;
        }
    }

    /** A VML shape's size, from its style: "width:120pt;height:80pt" (in points, or inches, or pixels). */
    private void vmlSize(String style) {
        if (style == null) {
            return;
        }
        for (String declaration : style.split(";")) {
            String[] pair = declaration.split(":", 2);
            if (pair.length == 2 && (pair[0].trim().equals("width") || pair[0].trim().equals("height"))) {
                float points = vmlPoints(pair[1].trim());
                if (pair[0].trim().equals("width")) {
                    width = points;
                } else {
                    height = points;
                }
            }
        }
    }

    private static float vmlPoints(String length) {
        try {
            if (length.endsWith("pt")) {
                return Float.parseFloat(length.substring(0, length.length() - 2));
            } else if (length.endsWith("in")) {
                return Float.parseFloat(length.substring(0, length.length() - 2)) * 72;
            } else if (length.endsWith("px")) {
                return Float.parseFloat(length.substring(0, length.length() - 2)) * 0.75f;
            }
            return Float.parseFloat(length);
        } catch (NumberFormatException _) {
            return 0;
        }
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
