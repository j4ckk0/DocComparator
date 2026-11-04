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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.datatransfer.StringSelection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.TransferHandler.TransferSupport;
import org.junit.jupiter.api.Test;

/** Text dropped or pasted: compared on its side, or typed in the path field it is dropped on. */
class DropHandlerTest {

    /** What was asked of the comparison, said: "paste left: …", "refused: …". */
    private final List<String> asked = new ArrayList<>();
    private boolean leftOpened;
    private final DocumentOpener opener = new DocumentOpener() {
        @Override
        public void open(boolean onLeft, Path path) {
            asked.add("open " + (onLeft ? "left" : "right") + ": " + path);
        }

        @Override
        public void open(Path left, Path right) {
            asked.add("open both: " + left + ", " + right);
        }

        @Override
        public void paste(boolean onLeft, String text) {
            asked.add("paste " + (onLeft ? "left" : "right") + ": " + text);
        }

        @Override
        public boolean leftOpened() {
            return leftOpened;
        }

        @Override
        public void refuse(String why) {
            asked.add("refused");
        }
    };

    @Test
    void textGoesToTheSideGiven() {
        JPanel area = new JPanel();
        assertTrue(new DropHandler(opener, area, false).importData(new TransferSupport(area, new StringSelection("a\nb"))));
        assertEquals(List.of("paste right: a\nb"), asked);
    }

    @Test
    void textPastedWithoutASideGoesWhereNothingIsYet() {
        JPanel area = new JPanel();
        DropHandler handler = new DropHandler(opener, area, null);
        handler.importData(new TransferSupport(area, new StringSelection("first")));
        leftOpened = true;
        handler.importData(new TransferSupport(area, new StringSelection("second")));
        assertEquals(List.of("paste left: first", "paste right: second"), asked);
    }

    @Test
    void blanksAreRefused() {
        JPanel area = new JPanel();
        assertFalse(new DropHandler(opener, area, true).importData(new TransferSupport(area, new StringSelection(" \n\t"))));
        assertEquals(List.of("refused"), asked);
    }

    @Test
    void textOnThePathFieldIsTypedInIt() {
        JTextField path = new JTextField();
        DropHandler handler = new DropHandler(opener, new JPanel(), true, path.getTransferHandler());
        assertTrue(handler.importData(new TransferSupport(path, new StringSelection("C:\\docs\\a.docx"))));
        assertEquals("C:\\docs\\a.docx", path.getText());
        assertTrue(asked.isEmpty(), "nothing compared");
    }
}
