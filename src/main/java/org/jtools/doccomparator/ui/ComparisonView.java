package org.jtools.doccomparator.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import org.jtools.doccomparator.compare.PropertyDifference;

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

    private static final int GUTTER = 26;
    private static final int MARGIN = 28;
    private static final int RIGHT_PADDING = 8;
    private static final int TITLE_HEIGHT = 26;
    /** The width of a page's text, in pixels at a scale of 1: A4, 21 cm, less an inch on each side. */
    private static final float PAGE_TEXT_WIDTH = 9026 / 15f;

    private Fonts fonts;
    private final FontRenderContext context = new FontRenderContext(null, true, true);
    private List<Line> lines = List.of();
    /** The colours the lines were built in, which the view draws around them. */
    private Palette palette = Palette.LIGHT;
    /** What a side says while there are no lines: whether its document is open, a title, then lines of text. */
    record EmptySide(boolean opened, String title, List<String> lines) {
    }

    private EmptySide[] emptySides = {new EmptySide(false, "", List.of()), new EmptySide(false, "", List.of())};
    private int laidOutWidth = -1;
    private int totalHeight;
    private int current = -1;

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
        emptySides = new EmptySide[] {left, right};
        repaint();
    }

    /** The side waiting for its document while the other's is shown (0 left, 1 right); -1 for none. */
    private int waitingSide = -1;

    void setWaitingSide(int side) {
        waitingSide = side;
        if (getParent() instanceof JViewport viewport) {
            // the waiting side stays where it is as the other scrolls: the view drawn anew, not shifted, at each step
            viewport.setScrollMode(side >= 0 ? JViewport.SIMPLE_SCROLL_MODE : JViewport.BLIT_SCROLL_MODE);
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
            Rectangle clip = g.getClipBounds() == null ? new Rectangle(0, 0, getWidth(), getHeight()) : g.getClipBounds();
            g.setColor(palette.background);
            g.fill(clip);
            layoutLines(getWidth());
            if (lines.isEmpty()) {
                paintEmpty(g);
                return;
            }
            int first = Math.max(0, lineAt(clip.y));
            for (int i = first; i < lines.size() && lines.get(i).top < clip.y + clip.height; i++) {
                paintLine(g, lines.get(i), i == current);
            }
            if (waitingSide >= 0) {
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
        paintSide(g, 0, false);
        paintSide(g, 1, false);
    }

    /** A side waiting for its document, or opened: a place framed with dashes, saying so. */
    private void paintSide(Graphics2D g, int side, boolean overLines) {
        Rectangle visible = getVisibleRect();
        int column = columnWidth(getWidth());
        int left = side == 0 ? 0 : column + GUTTER;
        int sideWidth = side == 0 ? column : getWidth() - column - GUTTER;
        if (overLines) {
            g.setColor(palette.background);
            g.fillRect(left, visible.y, sideWidth, visible.height);
        }
        int x = left + 16;
        int y = visible.y + 16;
        int width = sideWidth - 32;
        int height = Math.max(120, visible.height - 32);
        EmptySide says = emptySides[side];
        boolean opened = says.opened();
        g.setColor(opened ? palette.background : palette.noSide);
        g.fillRoundRect(x, y, width, height, 16, 16);
        g.setColor(opened ? palette.grid : palette.faint);
        g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1, new float[] {8, 6}, 0));
        g.drawRoundRect(x, y, width, height, 16, 16);
        g.setStroke(new BasicStroke(1));
        Font title = fonts.heading().deriveFont(fonts.heading().getSize2D() + 4);
        int lineY = y + height / 2 - ((says.lines().size() + 1) * g.getFontMetrics(fonts.text()).getHeight()) / 2;
        g.setFont(title);
        g.setColor(palette.text);
        g.drawString(says.title(), x + Math.max(8, (width - g.getFontMetrics().stringWidth(says.title())) / 2), lineY);
        lineY += g.getFontMetrics().getHeight() + 8;
        g.setFont(fonts.text());
        g.setColor(palette.faint);
        for (String line : says.lines()) {
            g.drawString(line, x + Math.max(8, (width - g.getFontMetrics().stringWidth(line)) / 2), lineY);
            lineY += g.getFontMetrics().getHeight();
        }
    }

    private void paintLine(Graphics2D g, Line line, boolean isCurrent) {
        int width = getWidth();
        if (line.isTitle()) {
            g.setColor(palette.title);
            g.fillRect(0, line.top, width, line.height);
            g.setColor(palette.titleText);
            g.setFont(fonts.heading());
            TextLayout title = new TextLayout(line.title, fonts.heading(), context);
            title.draw(g, 10, line.top + (line.height + title.getAscent() - title.getDescent()) / 2f);
        } else {
            int column = columnWidth(width);
            paintLineSide(g, line.left, 0, line.top, column, line.height - 1);
            paintLineSide(g, line.right, column + GUTTER, line.top, width - column - GUTTER, line.height - 1);
            paintGutter(g, line, column, line.top, line.height - 1);
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
        g.setColor(palette.line(side.look));
        g.fillRect(x, y, width, height);
        if (side.marker != null) {
            g.setColor(palette.faint);
            g.setFont(fonts.small());
            // on the first line of the text: below the space a paragraph keeps before it (the document view)
            float baseline = side.text != null ? side.text.firstBaseline() : g.getFontMetrics().getAscent() + 1;
            g.drawString(side.marker, x + 6, y + LineSide.PADDING
                    + (side.caption == null ? 0 : side.caption.height() + LineSide.GAP) + baseline);
        }
        paintContent(g, side, x + MARGIN, y);
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
                g.setColor(column.look == LineSide.Look.SAME && column.shading != null ? column.shading
                        : palette.line(column.look));
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

    /** Between the sides, a sign of how the line compares: ≠ changed, ≈ the same text otherwise, arrows for the rest. */
    private void paintGutter(Graphics2D g, Line line, int x, int y, int height) {
        LineSide.Look look = line.look();
        if (look == LineSide.Look.SAME) {
            g.setColor(palette.background);
            g.fillRect(x, y, GUTTER, height);
            return;
        }
        Color mark = palette.mark(look);
        g.setColor(palette.line(look));
        g.fillRect(x, y, GUTTER, height);
        g.setColor(mark);
        g.fillRect(x, y, 3, height);
        g.fillRect(x + GUTTER - 3, y, 3, height);
        int cx = x + GUTTER / 2;
        int cy = y + Math.min(height / 2, 14);
        switch (look) {
            case CHANGED, OTHERWISE -> {
                g.setFont(fonts.heading());
                String sign = look == LineSide.Look.CHANGED ? "≠" : "≈";
                g.drawString(sign, cx - g.getFontMetrics().stringWidth(sign) / 2, cy + g.getFontMetrics().getAscent() / 2 - 1);
            }
            case MOVED -> {
                LineSide moved = line.left != null && line.left.moved() ? line.left : line.right;
                boolean down = moved != null && moved.counterpartLine > lines.indexOf(line);
                g.fill(arrow(cx, cy, down ? 1 : -1, true));
            }
            case ORPHAN -> {
                if (line.left != null && line.left.look == LineSide.Look.ORPHAN) {
                    g.fill(arrow(cx - 3, cy, -1, false));
                }
                if (line.right != null && line.right.look == LineSide.Look.ORPHAN) {
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
                ? new Polygon(new int[] {cx - s, cx + s, cx}, new int[] {cy - s * direction, cy - s * direction, cy + s * direction}, 3)
                : new Polygon(new int[] {cx - s * direction, cx - s * direction, cx + s * direction}, new int[] {cy - s, cy + s, cy}, 3);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // The mouse

    private void clicked(MouseEvent e) {
        int index = lineAt(e.getY());
        if (index < 0) {
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
            return line.left == null && line.right == null ? null : describe(line.look());
        }
        if (side.columns != null) {
            return side.moved() ? "Moved: click to go to its match" : describe(side.look);
        }
        int column = columnWidth(getWidth());
        int x = e.getX() - (side == line.left ? 0 : column + GUTTER) - MARGIN;
        int y = e.getY() - line.top - LineSide.PADDING - (side.caption == null ? 0 : side.caption.height() + LineSide.GAP);
        List<PropertyDifference> formatting = side.text.formattingAt(x, y);
        if (!formatting.isEmpty()) {
            return "<html>Formatted otherwise:<br>" + formatting.stream().map(d -> escape(d.toString()))
                    .collect(Collectors.joining("<br>")) + "</html>";
        }
        return side.moved() ? "Moved: click to go to its match" : describe(side.look);
    }

    private static String describe(LineSide.Look look) {
        return switch (look) {
            case SAME -> null;
            case CHANGED -> "The text differs";
            case OTHERWISE -> "The same text; its structure or style differs";
            case MOVED -> "Moved";
            case ORPHAN -> "Only on this side";
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
