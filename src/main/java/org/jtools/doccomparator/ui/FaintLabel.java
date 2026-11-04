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
import javax.swing.JLabel;

/** A label in the look's greyed text colour, which it takes again when the look changes; a size smaller if asked. */
final class FaintLabel extends JLabel {

    FaintLabel(String text, int alignment, boolean smaller) {
        super(text, alignment);
        if (smaller) {
            setFont(getFont().deriveFont(getFont().getSize2D() - 1));
        }
    }

    @Override
    public void updateUI() {
        super.updateUI();
        setForeground(Palette.ofLook("Label.disabledForeground", Color.GRAY));
    }
}
