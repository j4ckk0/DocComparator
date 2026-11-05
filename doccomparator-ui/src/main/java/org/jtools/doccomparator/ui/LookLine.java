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

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import javax.swing.border.MatteBorder;

/** A line on some sides of a component, in the look's separator colour of the time. */
final class LookLine extends MatteBorder {

    LookLine(int top, int left, int bottom, int right) {
        super(top, left, bottom, right, Color.GRAY);
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        color = Palette.ofLook("Separator.foreground", Color.GRAY);
        super.paintBorder(c, g, x, y, width, height);
    }
}
