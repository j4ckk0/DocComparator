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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextHitInfo;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jtools.doccomparator.compare.FormatSpan;
import org.jtools.doccomparator.compare.PropertyDifference;
import org.jtools.doccomparator.compare.TextSpan;
import org.jtools.doccomparator.document.Picture;

/**
 * A text wrapped to a width, its differences marked: behind the text that differs, a bar where the other side has
 * text this one has not, a dotted line under text formatted otherwise. Tabs and breaks are shown by a sign in their
 * place, a line break also breaks the line.
 *
 * <p>With a look (the document view), the text is laid out as Word lays it out: each run in its font, size, weight
 * and colours, highlighted as it is; the lines aligned, indented, justified, spaced — at the scale given, which turns
 * the document's points into the view's pixels.
 */
final class TextBlock {

    /** Pixels in a point, at a scale of 1: the screen's 96 to the inch, the point's 72. */
    private static final float PIXELS_PER_POINT = 96f / 72f;

    private final String text;
    private final Font font;
    private final Color color;
    private final List<TextSpan> spans;
    private final List<FormatSpan> formatting;
    private final String placeholder;
    private final DocumentLook look;
    /** The colours it is drawn in: those of the lines it is part of. */
    private final Palette palette;
    /** Its pictures, by their offset in its text. */
    private Map<Integer, Picture> pictures = Map.of();

    /** The lines laid out: each its layout, its first character, where it starts across, where its top is. */
    private final List<TextLayout> layouts = new ArrayList<>();
    private final List<Integer> starts = new ArrayList<>();
    private final List<Float> xs = new ArrayList<>();
    private final List<Float> tops = new ArrayList<>();
    private int height;
    private float shadingLeft;
    private float shadingRight;
    /** The breaks across the block (the document view): where, from its top, and their names. */
    private record Break(float y, String label) {
    }

    /** The height a break takes. */
    private static final float BREAK_HEIGHT = 22;

    private final List<Break> breaks = new ArrayList<>();
    private float blockWidth;
    /** A list item's number, laid out, and where it starts across; null for none. */
    private TextLayout number;
    private float numberX;

    /** A text and its differences; placeholder: what is shown, faint, for an empty text. */
    TextBlock(String text, Font font, Color color, Palette palette, List<TextSpan> spans, List<FormatSpan> formatting,
              String placeholder) {
        this(text, font, color, palette, spans, formatting, placeholder, null);
    }

    /** The same, as the document lays it out (look), or plainly (null). */
    TextBlock(String text, Font font, Color color, Palette palette, List<TextSpan> spans, List<FormatSpan> formatting,
              String placeholder, DocumentLook look) {
        this.text = text;
        this.font = font;
        this.color = color;
        this.spans = spans;
        this.formatting = formatting;
        this.placeholder = placeholder;
        this.look = look;
        this.palette = palette;
    }

    /** The same, with its pictures, by their offset in its text. */
    TextBlock withPictures(Map<Integer, Picture> found) {
        pictures = Map.copyOf(found);
        return this;
    }

    static TextBlock plain(String text, Font font, Color color, Palette palette) {
        return new TextBlock(text, font, color, palette, List.of(), List.of(), " ");
    }

    int layout(FontRenderContext context, float width) {
        return layout(context, width, 1);
    }

    /** Wraps the text to this width, the document's points at this scale: its height. */
    int layout(FontRenderContext context, float width, float scale) {
        layouts.clear();
        starts.clear();
        xs.clear();
        tops.clear();
        boolean laidOut = look != null && !text.isEmpty();
        float pixels = PIXELS_PER_POINT * scale;
        String shown = text.isEmpty() ? placeholder : laidOut ? asWordShowsIt(text) : shown(text);
        AttributedString attributed = new AttributedString(shown);
        attributed.addAttribute(TextAttribute.FONT, text.isEmpty() ? font.deriveFont(Font.ITALIC) : font);
        if (laidOut) {
            for (DocumentLook.StyledRun run : look.runs()) {
                int start = Math.min(run.start(), shown.length());
                int end = Math.min(run.end(), shown.length());
                if (start < end) {
                    attributed.addAttribute(TextAttribute.FONT, font(run, pixels), start, end);
                    if (run.underline()) {
                        attributed.addAttribute(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON, start, end);
                    }
                    if (run.strike()) {
                        attributed.addAttribute(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON, start,
                                end);
                    }
                    if (run.color() != null) {
                        attributed.addAttribute(TextAttribute.FOREGROUND, run.color(), start, end);
                    }
                }
            }
        }
        float left = laidOut ? look.left() * pixels : 0;
        float right = laidOut ? look.right() * pixels : 0;
        float firstLine = laidOut ? look.firstLine() * pixels : 0;
        float before = laidOut ? look.before() * pixels : 0;
        float after = laidOut ? look.after() * pixels : 0;
        shadingLeft = left;
        shadingRight = width - right;
        blockWidth = width;
        breaks.clear();
        float breakHeight = BREAK_HEIGHT * Math.max(0.8f, scale);

        // a list item's number where its first line starts, the text after it at the indent — Word's tab — or further
        number = null;
        float textAfterNumber = 0;
        if (laidOut && look.number() != null && !look.runs().isEmpty()) {
            number = new TextLayout(look.number(), font(look.runs().getFirst(), pixels), context);
            numberX = Math.max(0, left + firstLine);
            float end = numberX + number.getAdvance();
            textAfterNumber = end + pixels * 2 <= left ? left : end + pixels * 6;
        }

        // pictures in the place of the characters standing for them: as shown in the document, or a sign
        if (!text.isEmpty()) {
            Font labels = font.deriveFont(Font.PLAIN, Math.max(9, font.getSize2D() - 2));
            for (Map.Entry<Integer, Picture> picture : pictures.entrySet()) {
                int at = picture.getKey();
                if (at < shown.length()) {
                    attributed.addAttribute(TextAttribute.CHAR_REPLACEMENT, laidOut
                            ? PictureGraphic.shown(picture.getValue(), pixels, width - left - right, labels, palette)
                            : PictureGraphic.sign(picture.getValue(), font, palette), at, at + 1);
                }
            }
        }

        LineBreakMeasurer measurer = new LineBreakMeasurer(attributed.getIterator(), context);
        List<DocumentLook.PageMark> marks = look == null ? List.of() : look.marks();
        int mark = 0;
        float top = 0;
        for (; mark < marks.size() && marks.get(mark).offset() <= 0; mark++) {   // before the paragraph
            breaks.add(new Break(top + breakHeight / 2, marks.get(mark).label()));
            top += breakHeight;
        }
        top += before;
        boolean firstOne = true;
        while (measurer.getPosition() < shown.length()) {
            int start = measurer.getPosition();
            float lineLeft = firstOne && number != null ? textAfterNumber
                    : Math.max(0, left + (firstOne ? firstLine : 0));
            float available = Math.max(20, width - lineLeft - right);
            int end = text.isEmpty() ? shown.length() : lineEnd(text, start);
            TextLayout layout = measurer.nextLayout(available, end, false);
            if (layout == null) {
                break;
            }
            boolean lastOfParagraph = measurer.getPosition() >= shown.length()
                    || !text.isEmpty() && isBreak(text.charAt(measurer.getPosition() - 1));
            float x = lineLeft;
            if (laidOut) {
                switch (look.alignment()) {
                    case JUSTIFIED -> {
                        if (!lastOfParagraph) {
                            layout = layout.getJustifiedLayout(available);
                        }
                    }
                    case CENTER -> x += (available - layout.getVisibleAdvance()) / 2;
                    case RIGHT -> x += available - layout.getVisibleAdvance();
                    default -> {
                    }
                }
            }
            // a page beginning in this line: before it — Word's lines are not quite ours, its page began about there
            for (; mark < marks.size() && marks.get(mark).offset() < measurer.getPosition()
                    && marks.get(mark).offset() < shown.length(); mark++) {
                breaks.add(new Break(top + breakHeight / 2, marks.get(mark).label()));
                top += breakHeight;
            }
            layouts.add(layout);
            starts.add(start);
            xs.add(x);
            tops.add(top);
            top += lineHeight(layout, pixels, laidOut);
            firstOne = false;
        }
        top += after;
        for (; mark < marks.size(); mark++) {   // after the paragraph: a page break ending it, the section ending
            breaks.add(new Break(top + breakHeight / 2, marks.get(mark).label()));
            top += breakHeight;
        }
        height = (int) Math.ceil(top);
        return height;
    }

    /** A line's height: its font's, or as the paragraph says — so many times that, or so many points exactly. */
    private float lineHeight(TextLayout layout, float pixels, boolean laidOut) {
        float natural = layout.getAscent() + layout.getDescent() + layout.getLeading();
        if (!laidOut) {
            return natural;
        }
        if (look.exactLine() > 0) {
            float exact = look.exactLine() * pixels;
            return look.atLeast() ? Math.max(natural, exact) : exact;
        }
        return natural * look.lineFactor();
    }

    private static Font font(DocumentLook.StyledRun run, float pixels) {
        int style = (run.bold() ? Font.BOLD : Font.PLAIN) | (run.italic() ? Font.ITALIC : Font.PLAIN);
        Font font = new Font(FontFamilies.resolve(run.family()), style, 1)
                .deriveFont(Math.max(4f, run.size() * pixels));
        if (run.script() != 0) {
            font = font.deriveFont(Map.of(TextAttribute.SUPERSCRIPT,
                    run.script() > 0 ? TextAttribute.SUPERSCRIPT_SUPER : TextAttribute.SUPERSCRIPT_SUB));
        }
        return font;
    }

    int height() {
        return height;
    }

    String text() {
        return text;
    }

    /** The pages beginning in the text, and the section ending (the document view): their names, in order. */
    List<String> pageMarks() {
        return look == null ? List.of() : look.marks().stream().map(DocumentLook.PageMark::label).toList();
    }

    /** A list item's number, shown in the text (the document view); null for none. */
    String number() {
        return look == null ? null : look.number();
    }

    /** Where the first line's baseline is, from the block's top. */
    float firstBaseline() {
        return layouts.isEmpty() ? 0 : tops.getFirst() + layouts.getFirst().getAscent();
    }

    void paint(Graphics2D g, float x, float y) {
        if (look != null && look.shading() != null && !layouts.isEmpty()) {
            g.setColor(look.shading());
            g.fill(new Rectangle2D.Float(x + shadingLeft, y, shadingRight - shadingLeft, height));
        }
        for (Break pageBreak : breaks) {
            paintBreak(g, x, y + pageBreak.y(), pageBreak.label());
        }
        for (int i = 0; i < layouts.size(); i++) {
            TextLayout layout = layouts.get(i);
            int start = starts.get(i);
            int end = start + layout.getCharacterCount();
            float lineX = x + xs.get(i);
            float baseline = y + tops.get(i) + layout.getAscent();
            if (i == 0 && number != null) {
                g.setColor(look.runs().getFirst().color() != null ? look.runs().getFirst().color() : color);
                number.draw(g, x + numberX, baseline);
            }
            if (!text.isEmpty()) {
                paintHighlights(g, layout, start, end, lineX, baseline);
                paintBehind(g, layout, start, end, lineX, baseline);
            }
            g.setColor(text.isEmpty() ? palette.faint : color);
            layout.draw(g, lineX, baseline);
            if (!text.isEmpty()) {
                paintMarks(g, layout, start, end, lineX, baseline, i == layouts.size() - 1);
            }
        }
    }

    /** The document's own highlighting, behind its runs (the document view). */
    private void paintHighlights(Graphics2D g, TextLayout layout, int start, int end, float x, float baseline) {
        if (look == null) {
            return;
        }
        AffineTransform at = AffineTransform.getTranslateInstance(x, baseline);
        for (DocumentLook.StyledRun run : look.runs()) {
            if (run.highlight() != null && run.start() < end && run.end() > start) {
                g.setColor(run.highlight());
                g.fill(at.createTransformedShape(layout.getLogicalHighlightShape(Math.max(run.start(), start) - start,
                        Math.min(run.end(), end) - start)));
            }
        }
    }

    private void paintBehind(Graphics2D g, TextLayout layout, int start, int end, float x, float baseline) {
        AffineTransform at = AffineTransform.getTranslateInstance(x, baseline);
        for (TextSpan span : spans) {
            Color behind = switch (span.kind()) {
                case CHANGED, ONLY_HERE -> palette.changedText;
                default -> null;
            };
            if (behind != null && span.start() < end && span.end() > start) {
                Shape shape = layout.getLogicalHighlightShape(Math.max(span.start(), start) - start,
                        Math.min(span.end(), end) - start);
                g.setColor(look == null ? behind : Palette.translucent(behind));
                g.fill(at.createTransformedShape(shape));
            }
        }
    }

    private void paintMarks(Graphics2D g, TextLayout layout, int start, int end, float x, float baseline,
                            boolean last) {
        AffineTransform at = AffineTransform.getTranslateInstance(x, baseline);
        int lineHeight = (int) Math.ceil(layout.getAscent() + layout.getDescent());
        for (TextSpan span : spans) {
            int at0 = span.start();
            if (span.kind() == TextSpan.Kind.ABSENT && at0 >= start && (at0 < end || last && at0 == end)) {
                Rectangle caret = at.createTransformedShape(layout.getCaretShape(TextHitInfo.leading(at0 - start)))
                        .getBounds();
                g.setColor(palette.absentMark);
                g.fillRect(caret.x - 1, (int) (baseline - layout.getAscent()), 3, lineHeight);
            }
        }
        for (TextSpan span : spans) {   // a picture that differs: framed in red, its image covering what is behind it
            if (span.differs()) {
                for (int offset : pictures.keySet()) {
                    if (offset >= span.start() && offset < span.end() && offset >= start && offset < end) {
                        Rectangle frame = at.createTransformedShape(
                                layout.getLogicalHighlightShape(offset - start, offset - start + 1)).getBounds();
                        g.setColor(Palette.CHANGED_MARK);
                        g.setStroke(new BasicStroke(3));
                        g.drawRect(frame.x, frame.y, frame.width, frame.height);
                        g.setStroke(new BasicStroke(1));
                    }
                }
            }
        }
        for (FormatSpan span : formatting) {
            if (span.start() < end && span.end() > start) {
                Rectangle under = at.createTransformedShape(layout.getLogicalHighlightShape(
                        Math.max(span.start(), start) - start, Math.min(span.end(), end) - start)).getBounds();
                g.setColor(palette.formattingMark);
                int lineY = (int) (baseline + Math.max(2, layout.getDescent() - 1));
                for (int dot = under.x; dot < under.x + under.width; dot += 3) {
                    g.fillRect(dot, lineY, 2, 2);
                }
            }
        }
    }

    /** The formatting differences of the text at this point, relative to the block; empty if none. */
    List<PropertyDifference> formattingAt(float x, float y) {
        for (int i = 0; i < layouts.size(); i++) {
            TextLayout layout = layouts.get(i);
            float top = tops.get(i);
            float bottom = i + 1 < tops.size() ? tops.get(i + 1)
                    : top + layout.getAscent() + layout.getDescent() + layout.getLeading();
            if (y >= top && y < bottom) {
                int index = starts.get(i)
                        + layout.hitTestChar(x - xs.get(i), y - top - layout.getAscent()).getCharIndex();
                for (FormatSpan span : formatting) {
                    if (index >= span.start() && index < span.end()) {
                        return span.differences();
                    }
                }
                return List.of();
            }
        }
        return List.of();
    }

    /** A break across the page, dashed, its name in the middle: "Page break", "Section break". */
    private void paintBreak(Graphics2D g, float x, float y, String label) {
        Graphics2D dashed = (Graphics2D) g.create();
        dashed.setColor(palette.faint);
        dashed.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[] {6, 4}, 0));
        dashed.draw(new Line2D.Float(x, y, x + blockWidth, y));
        dashed.setFont(font.deriveFont(Font.PLAIN, Math.max(9, font.getSize2D() - 2)));
        FontMetrics metrics = dashed.getFontMetrics();
        int width = metrics.stringWidth(label) + 12;
        float left = x + (blockWidth - width) / 2;
        dashed.setColor(palette.background);
        dashed.fill(new Rectangle2D.Float(left, y - metrics.getHeight() / 2f, width, metrics.getHeight()));
        dashed.setColor(palette.faint);
        dashed.drawString(label, left + 6, y + metrics.getAscent() / 2f - 1);
        dashed.dispose();
    }

    /**
     * The text as Word shows it (the document view): a tab as a wide space, a break as a space ending its line — in
     * their places, so that the offsets stay the same.
     */
    static String asWordShowsIt(String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            chars[i] = chars[i] == '\t' ? '\u2003' : isBreak(chars[i]) ? ' ' : chars[i];
        }
        return new String(chars);
    }

    private static boolean isBreak(char c) {
        return c == '\n' || c == '\r' || c == '\u000B' || c == '\f';
    }

    /** The text as shown: a sign for each tab and break, in its place, so that the offsets stay the same. */
    static String shown(String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            chars[i] = switch (chars[i]) {
                case '\t' -> '→';
                case '\n', '\r', '\u000B', '\f' -> '↵';
                default -> chars[i];
            };
        }
        return new String(chars);
    }

    /** Where the line starting there must end at the latest: after the next break of the text. */
    private static int lineEnd(String text, int start) {
        for (int i = start; i < text.length(); i++) {
            if (isBreak(text.charAt(i))) {
                return i + 1;
            }
        }
        return text.length();
    }
}
