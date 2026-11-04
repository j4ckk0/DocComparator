package org.jtools.doccomparator.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextHitInfo;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jtools.doccomparator.compare.FormatSpan;
import org.jtools.doccomparator.compare.PropertyDifference;
import org.jtools.doccomparator.compare.Span;

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
    private final List<Span> spans;
    private final List<FormatSpan> formatting;
    private final String placeholder;
    private final DocumentLook look;

    /** The lines laid out: each its layout, its first character, where it starts across, where its top is. */
    private final List<TextLayout> layouts = new ArrayList<>();
    private final List<Integer> starts = new ArrayList<>();
    private final List<Float> xs = new ArrayList<>();
    private final List<Float> tops = new ArrayList<>();
    private int height;
    private float shadingLeft;
    private float shadingRight;

    /** A text and its differences; placeholder: what is shown, faint, for an empty text. */
    TextBlock(String text, Font font, Color color, List<Span> spans, List<FormatSpan> formatting, String placeholder) {
        this(text, font, color, spans, formatting, placeholder, null);
    }

    /** The same, as the document lays it out (look), or plainly (null). */
    TextBlock(String text, Font font, Color color, List<Span> spans, List<FormatSpan> formatting, String placeholder,
              DocumentLook look) {
        this.text = text;
        this.font = font;
        this.color = color;
        this.spans = spans;
        this.formatting = formatting;
        this.placeholder = placeholder;
        this.look = look;
    }

    static TextBlock plain(String text, Font font, Color color) {
        return new TextBlock(text, font, color, List.of(), List.of(), " ");
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
        String shown = text.isEmpty() ? placeholder : shown(text);
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
                        attributed.addAttribute(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON, start, end);
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

        LineBreakMeasurer measurer = new LineBreakMeasurer(attributed.getIterator(), context);
        float top = before;
        boolean firstOne = true;
        while (measurer.getPosition() < shown.length()) {
            int start = measurer.getPosition();
            float lineLeft = Math.max(0, left + (firstOne ? firstLine : 0));
            float available = Math.max(20, width - lineLeft - right);
            int end = lineEnd(shown, start);
            TextLayout layout = measurer.nextLayout(available, end, false);
            if (layout == null) {
                break;
            }
            boolean lastOfParagraph = measurer.getPosition() >= shown.length()
                    || shown.charAt(measurer.getPosition() - 1) == '↵';
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
            layouts.add(layout);
            starts.add(start);
            xs.add(x);
            tops.add(top);
            top += lineHeight(layout, pixels, laidOut);
            firstOne = false;
        }
        height = (int) Math.ceil(top + after);
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
        Font font = new Font(FontFamilies.resolve(run.family()), style, 1).deriveFont(Math.max(4f, run.size() * pixels));
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

    /** Where the first line's baseline is, from the block's top. */
    float firstBaseline() {
        return layouts.isEmpty() ? 0 : tops.getFirst() + layouts.getFirst().getAscent();
    }

    void paint(Graphics2D g, float x, float y) {
        if (look != null && look.shading() != null && !layouts.isEmpty()) {
            g.setColor(look.shading());
            g.fill(new Rectangle2D.Float(x + shadingLeft, y, shadingRight - shadingLeft, height));
        }
        for (int i = 0; i < layouts.size(); i++) {
            TextLayout layout = layouts.get(i);
            int start = starts.get(i);
            int end = start + layout.getCharacterCount();
            float lineX = x + xs.get(i);
            float baseline = y + tops.get(i) + layout.getAscent();
            if (!text.isEmpty()) {
                paintHighlights(g, layout, start, end, lineX, baseline);
                paintBehind(g, layout, start, end, lineX, baseline);
            }
            g.setColor(text.isEmpty() ? Palette.FAINT : color);
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
        for (Span span : spans) {
            Color behind = switch (span.kind()) {
                case CHANGED, ONLY_HERE -> Palette.CHANGED_TEXT;
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
        for (Span span : spans) {
            int at0 = span.start();
            if (span.kind() == Span.Kind.ABSENT && at0 >= start && (at0 < end || last && at0 == end)) {
                Rectangle caret = at.createTransformedShape(layout.getCaretShape(TextHitInfo.leading(at0 - start)))
                        .getBounds();
                g.setColor(Palette.ABSENT_MARK);
                g.fillRect(caret.x - 1, (int) (baseline - layout.getAscent()), 3, lineHeight);
            }
        }
        for (FormatSpan span : formatting) {
            if (span.start() < end && span.end() > start) {
                Rectangle under = at.createTransformedShape(layout.getLogicalHighlightShape(
                        Math.max(span.start(), start) - start, Math.min(span.end(), end) - start)).getBounds();
                g.setColor(Palette.FORMATTING_MARK);
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

    /** Where the line starting there must end at the latest: after the next break. */
    private static int lineEnd(String shown, int start) {
        int arrow = shown.indexOf('↵', start);
        return arrow < 0 ? shown.length() : arrow + 1;
    }
}
