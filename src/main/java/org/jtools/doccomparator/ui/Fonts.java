/*
 * Copyright 2026 j4ckk0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jtools.doccomparator.ui;

import java.awt.Font;

/** The fonts of the view: the documents' text, headings in bold, and a smaller one for what is said about them. */
record Fonts(Font text, Font heading, Font small, Font notes) {

    static Fonts of(Font base) {
        Font text = base.deriveFont(Font.PLAIN, base.getSize2D() + 1);
        Font small = base.deriveFont(Font.PLAIN, base.getSize2D() - 1);
        return new Fonts(text, text.deriveFont(Font.BOLD), small, small.deriveFont(Font.ITALIC));
    }
}
