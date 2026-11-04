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
