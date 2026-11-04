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
package org.jtools.doccomparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.util.Arrays;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

class LooksTest {

    @Test
    void flatLafIsInstalledWithoutItsNativeLibraries() {
        Looks.install();
        assertEquals("false", System.getProperty("flatlaf.useNativeLibrary"), "it would unpack them in a temporary directory");
        assertTrue(Arrays.stream(UIManager.getInstalledLookAndFeels())
                .anyMatch(look -> look.getClassName().equals(FlatDarkLaf.class.getName())), "offered in View ▸ Look");
    }

    @Test
    void eachLookHasItsOtherSelf() {
        assertEquals(FlatDarkLaf.class.getName(), Looks.inMode(FlatLightLaf.class.getName(), true));
        assertEquals(FlatLightLaf.class.getName(), Looks.inMode(FlatDarkLaf.class.getName(), false));
        assertEquals(FlatDarculaLaf.class.getName(), Looks.inMode(FlatIntelliJLaf.class.getName(), true));
        assertEquals(FlatDarkLaf.class.getName(), Looks.inMode(FlatDarkLaf.class.getName(), true), "already dark");
        assertEquals(FlatDarkLaf.class.getName(), Looks.inMode("javax.swing.plaf.metal.MetalLookAndFeel", true));
    }

    @Test
    void aDarkLookIsSaidDark() throws Exception {
        LookAndFeel before = UIManager.getLookAndFeel();
        try {
            Looks.use(FlatDarkLaf.class.getName());
            assertTrue(Looks.isDark());
            Looks.use(FlatLightLaf.class.getName());
            assertFalse(Looks.isDark());
        } finally {
            UIManager.setLookAndFeel(before);
        }
    }
}
