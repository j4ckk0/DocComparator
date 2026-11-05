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
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;

/**
 * Draws the lines of the view, for one painting of it: a part's title across, or each side's text or table row, the
 * gutter's sign of how the line compares between them, the current line framed. The view lays the lines out and says
 * where its columns are.
 */
final class LinePainter {

    private final Palette palette;
    private final Fonts fonts;
    private final FontRenderContext context;
    /** The view's width, and its left column's: the gutter, then the right column, after it. */
    private final int width;
    private final int column;

    LinePainter(Palette palette, Fonts fonts, FontRenderContext context, int width, int column) {
        this.palette = palette;
        this.fonts = fonts;
        this.context = context;
        this.width = width;
        this.column = column;
    }

    /** A line at its place: a part's title, or its two sides and the gutter between; framed if current. */
    void paintLine(Graphics2D g, Line line, int index, boolean isCurrent) {
        if (line.isTitle()) {
            g.setColor(palette.title);
            g.fillRect(0, line.top, width, line.height);
            g.setColor(palette.titleText);
            g.setFont(fonts.heading());
            TextLayout title = new TextLayout(line.title, fonts.heading(), context);
            title.draw(g, 10, line.top + (line.height + title.getAscent() - title.getDescent()) / 2f);
        } else {
            paintLineSide(g, line.left, 0, line.top, column, line.height - 1);
            paintLineSide(g, line.right, column + ComparisonView.GUTTER, line.top,
                    width - column - ComparisonView.GUTTER, line.height - 1);
            paintGutter(g, line, index, column, line.top, line.height - 1);
            g.setColor(palette.separator);
            g.drawLine(0, line.top + line.height - 1, width, line.top + line.height - 1);
        }
        if (isCurrent) {
            g.setColor(palette.current);
            g.setStroke(new BasicStroke(2));
            g.drawRect(1, line.top + 1, width - 3, line.height - 2);
            g.setStroke(new BasicStroke(1));
        }
    }

    private void paintLineSide(Graphics2D g, LineSide side, int x, int y, int width, int height) {
        if (side == null) {
            hatch(g, x, y, width, height);
            return;
        }
        g.setColor(palette.line(side.marking));
        g.fillRect(x, y, width, height);
        if (side.marker != null) {
            g.setColor(palette.faint);
            g.setFont(fonts.small());
            // on the first line of the text: below the space a paragraph keeps before it (the document view)
            float baseline = side.text != null ? side.text.firstBaseline() : g.getFontMetrics().getAscent() + 1;
            g.drawString(side.marker, x + 6, y + LineSide.PADDING
                    + (side.caption == null ? 0 : side.caption.height() + LineSide.GAP) + baseline);
        }
        paintContent(g, side, x + ComparisonView.MARGIN, y);
    }

    /** What a side holds, top to bottom, from its top: its caption, its text or its grid, its notes, its link. */
    private void paintContent(Graphics2D g, LineSide side, int x, int y) {
        int top = y + LineSide.PADDING;
        if (side.caption != null) {
            side.caption.paint(g, x, top);
            top += side.caption.height() + LineSide.GAP;
        }
        if (side.columns != null) {
            paintGrid(g, side, x, top);
            top += side.gridHeight();
        } else {
            side.text.paint(g, x, top);
            top += side.text.height();
        }
        for (TextBlock block : new TextBlock[] {side.notes, side.link()}) {
            if (block != null) {
                top += LineSide.GAP;
                block.paint(g, x, top);
                top += block.height();
            }
        }
    }

    /** The cells of a table's row under their columns; a narrow empty place for a column this side has not got. */
    private void paintGrid(Graphics2D g, LineSide side, int x, int top) {
        int height = side.gridHeight();
        for (int c = 0; c < side.columns.size(); c++) {
            LineSide column = side.columns.get(c);
            int left = Math.round(x + side.columnX(c));
            int right = Math.round(x + side.columnX(c) + side.columnWidth(c));
            if (column == null) {
                hatch(g, left, top, right - left, height);
            } else {
                g.setColor(column.marking == LineSide.Marking.SAME && column.shading != null ? column.shading
                        : palette.line(column.marking));
                g.fillRect(left, top, right - left, height);
                paintContent(g, column, left + LineSide.PADDING, top);
            }
            g.setColor(palette.grid);
            g.drawRect(left, top, right - left - 1, height - 1);
        }
    }

    private void hatch(Graphics2D g, int x, int y, int width, int height) {
        g.setColor(palette.noSide);
        g.fillRect(x, y, width, height);
        Graphics2D hatch = (Graphics2D) g.create(x, y, width, height);
        hatch.setColor(palette.noSideHatch);
        for (int d = -height; d < width; d += 10) {
            hatch.drawLine(d, height, d + height, 0);
        }
        hatch.dispose();
    }

    /**
     * Between the sides, a sign of how the line compares: ≠ changed, ≈ the same text otherwise, arrows for the rest.
     */
    private void paintGutter(Graphics2D g, Line line, int index, int x, int y, int height) {
        LineSide.Marking marking = line.marking();
        if (marking == LineSide.Marking.SAME) {
            paintBlankGutter(g, x, y, height);
            return;
        }
        Color mark = palette.mark(marking);
        g.setColor(palette.line(marking));
        g.fillRect(x, y, ComparisonView.GUTTER, height);
        g.setColor(mark);
        g.fillRect(x, y, 3, height);
        g.fillRect(x + ComparisonView.GUTTER - 3, y, 3, height);
        int cx = x + ComparisonView.GUTTER / 2;
        int cy = y + Math.min(height / 2, 14);
        switch (marking) {
            case CHANGED, OTHERWISE -> {
                g.setFont(fonts.heading());
                String sign = marking == LineSide.Marking.CHANGED ? "≠" : "≈";
                g.drawString(sign, cx - g.getFontMetrics().stringWidth(sign) / 2,
                        cy + g.getFontMetrics().getAscent() / 2 - 1);
            }
            case MOVED -> {
                LineSide moved = line.left != null && line.left.moved() ? line.left : line.right;
                boolean down = moved != null && moved.counterpartLine > index;
                g.fill(arrow(cx, cy, down ? 1 : -1, true));
            }
            case ORPHAN -> {
                if (line.left != null && line.left.marking == LineSide.Marking.ORPHAN) {
                    g.fill(arrow(cx - 3, cy, -1, false));
                }
                if (line.right != null && line.right.marking == LineSide.Marking.ORPHAN) {
                    g.fill(arrow(cx + 3, cy, 1, false));
                }
            }
            default -> {
            }
        }
    }

    /** A small triangle at (cx, cy), pointing down or right (direction 1), up or left (-1). */
    private static Polygon arrow(int cx, int cy, int direction, boolean vertical) {
        int s = 5;
        return vertical
                ? new Polygon(new int[] {cx - s, cx + s, cx},
                        new int[] {cy - s * direction, cy - s * direction, cy + s * direction}, 3)
                : new Polygon(new int[] {cx - s * direction, cx - s * direction, cx + s * direction},
                        new int[] {cy - s, cy + s, cy}, 3);
    }

    /** The gutter where nothing is marked: its own shade between two lines, setting the sides apart. */
    void paintBlankGutter(Graphics2D g, int x, int y, int height) {
        g.setColor(palette.gutter);
        g.fillRect(x, y, ComparisonView.GUTTER, height);
        g.setColor(palette.grid);
        g.fillRect(x, y, 1, height);
        g.fillRect(x + ComparisonView.GUTTER - 1, y, 1, height);
    }
}
