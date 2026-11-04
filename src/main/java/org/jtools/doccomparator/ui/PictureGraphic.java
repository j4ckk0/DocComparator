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

    private PictureGraphic(Picture picture, BufferedImage image, float width, float height, Font font) {
        super(BOTTOM_ALIGNMENT);
        this.picture = picture;
        this.image = image;
        this.width = width;
        this.height = height;
        this.font = font;
    }

    /** As the document shows it: its size in points at so many pixels each, no wider than the line. */
    static PictureGraphic shown(Picture picture, float pixelsPerPoint, float widest, Font font) {
        BufferedImage image = PictureImages.of(picture);
        float width = picture.width() > 0 ? picture.width() * pixelsPerPoint
                : image != null ? image.getWidth() * 0.75f * pixelsPerPoint : 144 * pixelsPerPoint;
        float height = picture.height() > 0 ? picture.height() * pixelsPerPoint
                : image != null ? image.getHeight() * 0.75f * pixelsPerPoint : 96 * pixelsPerPoint;
        if (width > widest && widest > 0) {
            height *= widest / width;
            width = widest;
        }
        return new PictureGraphic(picture, image, Math.max(8, width), Math.max(8, height), font);
    }

    /** A picture's sign, in a line of plain text. */
    static PictureGraphic sign(Picture picture, Font font) {
        float size = font.getSize2D();
        return new PictureGraphic(picture, null, size * 1.3f, size * 0.95f, null);
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
            drawn.setColor(Palette.NO_SIDE);
            drawn.fill(frame);
            drawn.setColor(Palette.FAINT);
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
            case LINKED -> "Linked picture, not fetched";
            case TOO_LARGE -> "Picture too large to show";
            case UNSUPPORTED -> "chart".equals(picture.format()) ? "Chart"
                    : "diagram".equals(picture.format()) ? "Diagram" : "Picture not found";
            case IMAGE -> (picture.format() == null ? "Picture" : picture.format().toUpperCase(Locale.ROOT)
                    + " picture") + ", not shown";
        };
    }
}
