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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import org.jtools.doccomparator.i18n.I18nLabels;

/**
 * The whole comparison at a glance, beside the view: a mark for each line that differs, at its height in the documents,
 * in its colour; the part in sight framed. A click, or a drag, goes there.
 */
final class DifferenceMap extends JComponent {

    private static final int WIDTH = 16;
    private static final int INSET = 2;

    private final ComparisonView view;
    private final JViewport viewport;

    /** The map of this view's differences, scrolled with scroll; its tooltip bound with labels. */
    DifferenceMap(ComparisonView view, JScrollPane scroll, I18nLabels labels) {
        this.view = view;
        this.viewport = scroll.getViewport();
        labels.tip(this, "map.tip");
        viewport.addChangeListener(_ -> repaint());
        view.addPropertyChangeListener(ComparisonView.LINES, _ -> repaint());
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                goTo(e.getY());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                goTo(e.getY());
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(WIDTH, 100);
    }

    private double scale() {
        int total = view.totalHeight();
        return total <= 0 ? 0 : (double) (getHeight() - 2 * INSET) / Math.max(total, viewport.getHeight());
    }

    private void goTo(int y) {
        double scale = scale();
        if (scale <= 0) {
            return;
        }
        int viewY = (int) ((y - INSET) / scale) - viewport.getHeight() / 2;
        int most = Math.max(0, view.getHeight() - viewport.getHeight());
        viewport.setViewPosition(new Point(0, Math.clamp(viewY, 0, most)));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics;
        Palette palette = view.palette();
        g.setColor(palette.noSide);
        g.fillRect(0, 0, getWidth(), getHeight());
        double scale = scale();
        if (scale <= 0) {
            return;
        }
        for (Line line : view.lines()) {
            if (line.differs()) {
                g.setColor(palette.mark(line.marking()));
                int y = INSET + (int) (line.top * scale);
                g.fillRect(INSET + 1, y, getWidth() - 2 * INSET - 2, Math.max(2, (int) Math.ceil(line.height * scale)));
            }
        }
        Rectangle visible = viewport.getViewRect();
        g.setColor(palette.current);
        g.drawRect(0, INSET + (int) (visible.y * scale), getWidth() - 1,
                Math.max(4, (int) (visible.height * scale)));
    }
}
