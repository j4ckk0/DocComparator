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

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.FontRenderContext;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import org.jtools.doccomparator.compare.PropertyDifference;
import org.jtools.doccomparator.i18n.I18n;
import org.jtools.doccomparator.report.ComparisonWords;

/**
 * The two documents side by side, line by line: each line as high as its taller side, so that what goes together
 * stays together whatever the scrolling. Draws only the lines in sight, laid out again when the width changes.
 *
 * <p>A gutter between the sides says how each line compares; a click on a moved paragraph goes to its match. The
 * current line, the one the previous and next differences start from, is framed.
 */
final class ComparisonView extends JComponent implements Scrollable {

    static final String CURRENT_LINE = "currentLine";
    static final String LINES = "lines";
    /** Told when another side is clicked: the one text pasted goes to (chosenSide). */
    static final String CHOSEN_SIDE = "chosenSide";

    static final int GUTTER = 26;
    /** Between a side's edge and its text: room for a heading's level, a list's mark. */
    static final int MARGIN = 28;
    private static final int RIGHT_PADDING = 8;
    private static final int TITLE_HEIGHT = 26;
    /** The width of a page's text, in pixels at a scale of 1: A4, 21 cm, less an inch on each side. */
    private static final float PAGE_TEXT_WIDTH = 9026 / 15f;

    private Fonts fonts;
    private final FontRenderContext context = new FontRenderContext(null, true, true);
    private List<Line> lines = List.of();
    /** The colours the lines were built in, which the view draws around them. */
    private Palette palette = Palette.LIGHT;
    private final Map<Side, EmptySide> emptySides = new EnumMap<>(Map.of(Side.LEFT, new EmptySide(false, "", List.of()),
            Side.RIGHT, new EmptySide(false, "", List.of())));
    private int laidOutWidth = -1;
    private int totalHeight;
    private int current = -1;
    /** The side last clicked, where text pasted goes: the left until one is. */
    private Side chosen = Side.LEFT;

    ComparisonView() {
        fonts = lookFonts();
        setOpaque(true);
        setBackground(Palette.LIGHT.background);
        setFocusable(true);
        setToolTipText("");   // tooltips on: their text depends on the point
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                Side clicked = e.getX() < getWidth() / 2 ? Side.LEFT : Side.RIGHT;
                if (clicked != chosen) {
                    chosen = clicked;
                    repaint();
                    firePropertyChange(CHOSEN_SIDE, clicked.other(), clicked);
                }
                clicked(e);
            }
        };
        addMouseListener(mouse);
    }

    Fonts fonts() {
        return fonts;
    }

    /** The fonts of the look of the time, its labels' font their base. */
    private static Fonts lookFonts() {
        Font base = UIManager.getFont("Label.font");
        return Fonts.of(base == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 12) : base);
    }

    /** Another look: its fonts; the lines, laid out again (the panel makes them anew, in these). */
    @Override
    public void updateUI() {
        super.updateUI();
        if (fonts != null) {
            fonts = lookFonts();
            laidOutWidth = -1;
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // The lines

    /** Shows these lines, built in this palette; the current one this (-1 for none). */
    void setLines(List<Line> lines, int current, Palette palette) {
        List<Line> old = this.lines;
        this.lines = List.copyOf(lines);
        this.palette = palette;
        setBackground(palette.background);
        this.current = Math.min(current, lines.size() - 1);
        laidOutWidth = -1;
        revalidate();
        repaint();
        firePropertyChange(LINES, old, this.lines);
        firePropertyChange(CURRENT_LINE, -2, this.current);
    }

    /** The side last clicked, left or right-clicked: the one text pasted goes to. */
    Side chosenSide() {
        return chosen;
    }

    List<Line> lines() {
        return lines;
    }

    Palette palette() {
        return palette;
    }

    /**
     * What each side says while there are no lines — a title, then lines of text: which document goes there, how to
     * open it, or the one opened, waiting for the other.
     */
    void setEmptySides(EmptySide left, EmptySide right) {
        emptySides.put(Side.LEFT, left);
        emptySides.put(Side.RIGHT, right);
        repaint();
    }

    /** The side waiting for its document while the other's is shown; null for none. */
    private Side waitingSide;

    void setWaitingSide(Side side) {
        waitingSide = side;
        if (getParent() instanceof JViewport viewport) {
            // the waiting side stays where it is as the other scrolls: the view drawn anew, not shifted, at each step
            viewport.setScrollMode(side != null ? JViewport.SIMPLE_SCROLL_MODE : JViewport.BLIT_SCROLL_MODE);
        }
        repaint();
    }

    int current() {
        return current;
    }

    /** Makes a line the current one, and scrolls to it, in the middle of the view, if asked. */
    void setCurrent(int line, boolean scroll) {
        int old = current;
        current = line;
        if (scroll && line >= 0) {
            layoutLines(width());
            Line l = lines.get(line);
            Rectangle visible = getVisibleRect();
            int y = Math.max(0, l.top + Math.min(l.height, visible.height) / 2 - visible.height / 2);
            scrollRectToVisible(new Rectangle(0, y, 1, visible.height));
        }
        repaint();
        firePropertyChange(CURRENT_LINE, old, line);
    }

    /** The next line that differs after the current one, or the previous one before it; -1 if none. */
    int difference(boolean next) {
        int from = current < 0 ? (next ? -1 : lines.size()) : current;
        for (int i = next ? from + 1 : from - 1; i >= 0 && i < lines.size(); i += next ? 1 : -1) {
            if (lines.get(i).differs()) {
                return i;
            }
        }
        return -1;
    }

    /** How many lines differ, and which of them the current one is, from 1 (0 if it is none of them). */
    int[] differenceCount() {
        int count = 0;
        int index = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).differs()) {
                count++;
                if (i == current) {
                    index = count;
                }
            }
        }
        return new int[] {count, index};
    }

    int totalHeight() {
        layoutLines(width());
        return totalHeight;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Layout

    private int width() {
        return getParent() instanceof JViewport viewport ? viewport.getWidth() : getWidth();
    }

    private int columnWidth(int width) {
        return Math.max(60, (width - GUTTER) / 2);
    }

    /** The width of the left side as the view is laid out, the gutter then the right side after it. */
    int leftWidth() {
        return columnWidth(width());
    }

    /** Whether this point is on the side waiting for its document, the gutter next to it included: no line there. */
    boolean onWaitingSide(int x) {
        int column = columnWidth(getWidth());
        if (waitingSide == null) {
            return false;
        }
        return waitingSide == Side.LEFT ? x < column + GUTTER : x >= column;
    }

    private void layoutLines(int width) {
        if (width == laidOutWidth || width <= 0) {
            return;
        }
        laidOutWidth = width;
        float textWidth = columnWidth(width) - MARGIN - RIGHT_PADDING;
        // the document view: a page's width of text (A4, margins of an inch) fills a side
        float scale = Math.clamp(textWidth / PAGE_TEXT_WIDTH, 0.5f, 1.6f);
        int y = 0;
        for (Line line : lines) {
            line.top = y;
            if (line.isTitle()) {
                line.height = TITLE_HEIGHT;
            } else {
                int left = line.left == null ? 0 : line.left.layout(context, textWidth, scale);
                int right = line.right == null ? 0 : line.right.layout(context, textWidth, scale);
                line.height = Math.max(left, right) + 1;   // and the line between lines
            }
            y += line.height;
        }
        totalHeight = y;
    }

    @Override
    public Dimension getPreferredSize() {
        layoutLines(width());
        return new Dimension(400, totalHeight);
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        int before = totalHeight;
        layoutLines(width);
        super.setBounds(x, y, width, height);
        if (totalHeight != before) {
            revalidate();   // the height follows the width
        }
    }

    /** The line at this height, -1 for none. */
    int lineAt(int y) {
        int low = 0;
        int high = lines.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            Line line = lines.get(middle);
            if (y < line.top) {
                high = middle - 1;
            } else if (y >= line.top + line.height) {
                low = middle + 1;
            } else {
                return middle;
            }
        }
        return -1;
    }

    /** The side at this point of a line, null for none: the left side, the right side, not the gutter. */
    private LineSide sideAt(Line line, int x) {
        int column = columnWidth(getWidth());
        if (x < column) {
            return line.left;
        }
        return x >= column + GUTTER ? line.right : null;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Painting

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Rectangle clip = g.getClipBounds() == null ? new Rectangle(0, 0, getWidth(), getHeight())
                    : g.getClipBounds();
            g.setColor(palette.background);
            g.fill(clip);
            layoutLines(getWidth());
            if (lines.isEmpty()) {
                paintEmpty(g);
                return;
            }
            LinePainter painter = new LinePainter(palette, fonts, context, getWidth(), columnWidth(getWidth()));
            int first = Math.max(0, lineAt(clip.y));
            for (int i = first; i < lines.size() && lines.get(i).top < clip.y + clip.height; i++) {
                painter.paintLine(g, lines.get(i), i, i == current);
            }
            paintGutterBelow(g, clip, painter);
            if (waitingSide != null) {
                paintSide(g, waitingSide, true);   // over its empty half, where it stays as the other scrolls
            }
        } finally {
            g.dispose();
        }
    }

    /**
     * No lines yet: the two sides, each a place framed with dashes where its document goes, saying which one and how
     * to open it — two documents, side by side, as they will be compared.
     */
    private void paintEmpty(Graphics2D g) {
        paintSide(g, Side.LEFT, false);
        paintSide(g, Side.RIGHT, false);
    }

    /** The gutter under the last line, down to the bottom: the two sides kept apart where they have no lines. */
    private void paintGutterBelow(Graphics2D g, Rectangle clip, LinePainter painter) {
        Line last = lines.getLast();
        int top = Math.max(clip.y, last.top + last.height);
        if (top < clip.y + clip.height) {
            painter.paintBlankGutter(g, columnWidth(getWidth()), top, clip.y + clip.height - top);
        }
    }

    /** A side waiting for its document, or opened (EmptySide); over the lines, the gutter beside it covered too. */
    private void paintSide(Graphics2D g, Side side, boolean overLines) {
        Rectangle visible = getVisibleRect();
        int column = columnWidth(getWidth());
        int left = side.of(0, column + GUTTER);
        int sideWidth = side.of(column, getWidth() - column - GUTTER);
        if (overLines) {   // the gutter too: the side shown ends where its column does
            g.setColor(palette.background);
            g.fillRect(side.of(0, column), visible.y, sideWidth + GUTTER, visible.height);
        }
        emptySides.get(side).paint(g, new Rectangle(left + 16, visible.y + 16, sideWidth - 32,
                Math.max(120, visible.height - 32)), side == chosen, palette, fonts);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // The mouse

    private void clicked(MouseEvent e) {
        int index = lineAt(e.getY());
        if (index < 0 || onWaitingSide(e.getX())) {
            return;
        }
        LineSide side = sideAt(lines.get(index), e.getX());
        if (side != null && side.moved() && !e.isPopupTrigger() && e.getButton() == MouseEvent.BUTTON1) {
            setCurrent(side.counterpartLine, true);
        } else {
            setCurrent(index, false);
        }
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        int index = lineAt(e.getY());
        if (index < 0 || lines.get(index).isTitle()) {
            return null;
        }
        Line line = lines.get(index);
        LineSide side = sideAt(line, e.getX());
        if (side == null) {
            return line.left == null && line.right == null ? null : describe(line.marking());
        }
        if (side.columns != null) {
            return side.moved() ? I18n.text("view.moved.tip") : describe(side.marking);
        }
        int column = columnWidth(getWidth());
        int x = e.getX() - (side == line.left ? 0 : column + GUTTER) - MARGIN;
        int y = e.getY() - line.top - LineSide.PADDING
                - (side.caption == null ? 0 : side.caption.height() + LineSide.GAP);
        List<PropertyDifference> formatting = side.text.formattingAt(x, y);
        if (!formatting.isEmpty()) {
            return "<html>" + escape(I18n.text("view.formatting.tip")) + "<br>" + formatting.stream()
                    .map(d -> escape(ComparisonWords.change(d)))
                    .collect(Collectors.joining("<br>")) + "</html>";
        }
        return side.moved() ? I18n.text("view.moved.tip") : describe(side.marking);
    }

    private static String describe(LineSide.Marking marking) {
        return switch (marking) {
            case SAME -> null;
            case CHANGED -> I18n.text("view.changed");
            case OTHERWISE -> I18n.text("view.otherwise");
            case MOVED -> I18n.text("view.moved");
            case ORPHAN -> I18n.text("view.orphan");
        };
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Scrollable

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(900, 600);
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL ? 24 : 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL ? Math.max(24, visible.height - 48) : visible.width;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof JViewport viewport && viewport.getHeight() > totalHeight;
    }
}
