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

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.Map;
import javax.swing.JScrollPane;

/**
 * The heads of the sides, each over its side of the view, as wide as it: the one chosen, underlined, the width of its
 * column. Before the view has a width, the scroll pane's, less its scroll bar.
 */
final class HeadsLayout implements LayoutManager {

    private final ComparisonView view;
    private final JScrollPane scroll;
    private final Map<Side, DocumentHeader> heads;

    HeadsLayout(ComparisonView view, JScrollPane scroll, Map<Side, DocumentHeader> heads) {
        this.view = view;
        this.scroll = scroll;
        this.heads = heads;
    }

    @Override
    public void layoutContainer(Container parent) {
        Insets insets = parent.getInsets();
        int width = view.getWidth() > 0 ? view.getWidth() : parent.getWidth() - scroll.getVerticalScrollBar()
                .getPreferredSize().width;
        int left = Math.min(view.leftWidth(), width);
        int height = parent.getHeight() - insets.top - insets.bottom;
        heads.get(Side.LEFT).setBounds(0, insets.top, left, height);
        int right = left + ComparisonView.GUTTER;
        heads.get(Side.RIGHT).setBounds(right, insets.top, Math.max(0, width - right), height);
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        Insets insets = parent.getInsets();
        int height = heads.values().stream().mapToInt(head -> head.getPreferredSize().height).max().orElse(0);
        return new Dimension(0, height + insets.top + insets.bottom);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return preferredLayoutSize(parent);
    }

    @Override
    public void addLayoutComponent(String name, Component component) {
    }

    @Override
    public void removeLayoutComponent(Component component) {
    }
}
