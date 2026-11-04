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

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.GraphicAttribute;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Locale;
import org.jtools.doccomparator.document.Picture;
import org.jtools.doccomparator.i18n.I18n;

/**
 * A picture in a line of text, in the place of the character standing for it (TextAttribute.CHAR_REPLACEMENT), its
 * bottom on the baseline: its image at the size given; or, when it cannot be shown, a frame saying what it is. In the
 * diff view, a small frame, a picture's sign.
 */
final class PictureGraphic extends GraphicAttribute {

    private final Picture picture;
    private final BufferedImage image;
    private final float width;
    private final float height;
    private final Font font;
    private final Palette palette;

    private PictureGraphic(Picture picture, BufferedImage image, float width, float height, Font font,
                           Palette palette) {
        super(BOTTOM_ALIGNMENT);
        this.picture = picture;
        this.image = image;
        this.width = width;
        this.height = height;
        this.font = font;
        this.palette = palette;
    }

    /** As the document shows it: its size in points at so many pixels each, no wider than the line. */
    static PictureGraphic shown(Picture picture, float pixelsPerPoint, float widest, Font font, Palette palette) {
        BufferedImage image = PictureImages.of(picture);
        float width = picture.width() > 0 ? picture.width() * pixelsPerPoint
                : image != null ? image.getWidth() * 0.75f * pixelsPerPoint : 144 * pixelsPerPoint;
        float height = picture.height() > 0 ? picture.height() * pixelsPerPoint
                : image != null ? image.getHeight() * 0.75f * pixelsPerPoint : 96 * pixelsPerPoint;
        if (width > widest && widest > 0) {
            height *= widest / width;
            width = widest;
        }
        return new PictureGraphic(picture, image, Math.max(8, width), Math.max(8, height), font, palette);
    }

    /** A picture's sign, in a line of plain text. */
    static PictureGraphic sign(Picture picture, Font font, Palette palette) {
        float size = font.getSize2D();
        return new PictureGraphic(picture, null, size * 1.3f, size * 0.95f, null, palette);
    }

    @Override
    public float getAscent() {
        return height;
    }

    @Override
    public float getDescent() {
        return 0;
    }

    @Override
    public float getAdvance() {
        return width;
    }

    @Override
    public void draw(Graphics2D g, float x, float y) {
        Graphics2D drawn = (Graphics2D) g.create();
        try {
            float top = y - height;
            if (image != null) {
                drawn.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                drawn.drawImage(image, Math.round(x), Math.round(top), Math.round(width), Math.round(height), null);
                return;
            }
            Rectangle2D frame = new Rectangle2D.Float(x + 1, top + 1, width - 2, height - 2);
            drawn.setColor(palette.noSide);
            drawn.fill(frame);
            drawn.setColor(palette.faint);
            drawn.setStroke(new BasicStroke(1));
            drawn.draw(frame);
            if (font == null) {   // a sign: a mountain in a frame
                drawn.drawLine(Math.round(x + 2), Math.round(y - 2), Math.round(x + width * 0.45f),
                        Math.round(top + height * 0.45f));
                drawn.drawLine(Math.round(x + width * 0.45f), Math.round(top + height * 0.45f),
                        Math.round(x + width - 2), Math.round(y - 2));
                return;
            }
            drawn.setFont(font);
            drawn.clip(frame);
            FontMetrics metrics = drawn.getFontMetrics();
            String[] lines = {said(), picture.description() == null ? "" : picture.description()};
            float lineY = top + height / 2 - metrics.getHeight() / 2f + metrics.getAscent();
            for (String line : lines) {
                drawn.drawString(line, x + Math.max(4, (width - metrics.stringWidth(line)) / 2), lineY);
                lineY += metrics.getHeight();
            }
        } finally {
            drawn.dispose();
        }
    }

    /** What the frame of a picture that cannot be shown says. */
    private String said() {
        return switch (picture.kind()) {
            case LINKED -> I18n.text("picture.linked");
            case TOO_LARGE -> I18n.text("picture.tooLarge");
            case UNSUPPORTED -> I18n.text("chart".equals(picture.format()) ? "picture.chart"
                    : "diagram".equals(picture.format()) ? "picture.diagram" : "picture.missing");
            case IMAGE -> picture.format() == null ? I18n.text("picture.notShown")
                    : I18n.text("picture.formatNotShown", picture.format().toUpperCase(Locale.ROOT));
        };
    }
}
