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

import java.nio.file.Path;

/** What a side's head, a drop and a paste ask of the comparison: documents opened, and why one could not be. */
interface DocumentOpener {

    /** Opens this document on its side, the other side kept. */
    void open(Side side, Path path);

    /** Opens two documents, left and right. */
    void open(Path left, Path right);

    /** Compares this text on its side, as a document of a paragraph a line, the other side kept. */
    void paste(Side side, String text);

    /** The side chosen, where text pasted goes: the side last clicked. */
    Side chosenSide();

    /** Says, in the status line, why what was asked was not done. */
    void refuse(String why);
}
