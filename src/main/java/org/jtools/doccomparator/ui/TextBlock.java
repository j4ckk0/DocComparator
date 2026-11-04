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
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.List;
import org.jtools.doccomparator.compare.FormatSpan;
import org.jtools.doccomparator.compare.PropertyDifference;
import org.jtools.doccomparator.compare.Span;

/**
 * A text wrapped to a width, its differences marked: behind the text that differs, a bar where the other side has
 * text this one has not, a dotted line under text formatted otherwise. Tabs and breaks are shown by a sign in their
 * place, a line break also breaks the line.
 */
final class TextBlock {

    private final String text;
    private final Font font;
    private final Color color;
    private final List<Span> spans;
    private final List<FormatSpan> formatting;
    private final String placeholder;
    private final List<TextLayout> layouts = new ArrayList<>();
    private final List<Integer> starts = new ArrayList<>();
    private int height;

    /** A text and its differences; placeholder: what is shown, faint, for an empty text. */
    TextBlock(String text, Font font, Color color, List<Span> spans, List<FormatSpan> formatting, String placeholder) {
        this.text = text;
        this.font = font;
        this.color = color;
        this.spans = spans;
        this.formatting = formatting;
        this.placeholder = placeholder;
    }

    static TextBlock plain(String text, Font font, Color color) {
        return new TextBlock(text, font, color, List.of(), List.of(), " ");
    }

    /** Wraps the text to this width: its height. */
    int layout(FontRenderContext context, float width) {
        layouts.clear();
        starts.clear();
        String shown = text.isEmpty() ? placeholder : shown(text);
        AttributedString attributed = new AttributedString(shown);
        attributed.addAttribute(TextAttribute.FONT, text.isEmpty() ? font.deriveFont(Font.ITALIC) : font);
        LineBreakMeasurer measurer = new LineBreakMeasurer(attributed.getIterator(), context);
        float total = 0;
        while (measurer.getPosition() < shown.length()) {
            int start = measurer.getPosition();
            TextLayout layout = measurer.nextLayout(Math.max(width, 20), lineEnd(shown, start), false);
            if (layout == null) {
                break;
            }
            layouts.add(layout);
            starts.add(start);
            total += layout.getAscent() + layout.getDescent() + layout.getLeading();
        }
        height = (int) Math.ceil(total);
        return height;
    }

    int height() {
        return height;
    }

    String text() {
        return text;
    }

    void paint(Graphics2D g, float x, float y) {
        float top = y;
        for (int i = 0; i < layouts.size(); i++) {
            TextLayout layout = layouts.get(i);
            int start = starts.get(i);
            int end = start + layout.getCharacterCount();
            float baseline = top + layout.getAscent();
            if (!text.isEmpty()) {
                paintBehind(g, layout, start, end, x, baseline);
            }
            g.setColor(text.isEmpty() ? Palette.FAINT : color);
            layout.draw(g, x, baseline);
            if (!text.isEmpty()) {
                paintMarks(g, layout, start, end, x, baseline, i == layouts.size() - 1);
            }
            top += layout.getAscent() + layout.getDescent() + layout.getLeading();
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
                g.setColor(behind);
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
        float top = 0;
        for (int i = 0; i < layouts.size(); i++) {
            TextLayout layout = layouts.get(i);
            float bottom = top + layout.getAscent() + layout.getDescent() + layout.getLeading();
            if (y >= top && y < bottom) {
                int index = starts.get(i) + layout.hitTestChar(x, y - top - layout.getAscent()).getCharIndex();
                for (FormatSpan span : formatting) {
                    if (index >= span.start() && index < span.end()) {
                        return span.differences();
                    }
                }
                return List.of();
            }
            top = bottom;
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
