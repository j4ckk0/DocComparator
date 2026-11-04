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

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import java.util.Map;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 * The window's looks: FlatLaf's, light and dark, beside those Java has. FlatLaf is told to do without its native
 * libraries — which it would unpack in a temporary directory, and which the program does not ship (pom.xml): window
 * decorations of the system, nothing the program needs.
 */
final class Looks {

    /** The look until one is chosen. */
    static final String DEFAULT = FlatLightLaf.class.getName();

    /** Each FlatLaf look's other self: light to dark, dark to light. */
    private static final Map<String, String> COUNTERPARTS = Map.of(
            FlatLightLaf.class.getName(), FlatDarkLaf.class.getName(),
            FlatDarkLaf.class.getName(), FlatLightLaf.class.getName(),
            FlatIntelliJLaf.class.getName(), FlatDarculaLaf.class.getName(),
            FlatDarculaLaf.class.getName(), FlatIntelliJLaf.class.getName(),
            FlatMacLightLaf.class.getName(), FlatMacDarkLaf.class.getName(),
            FlatMacDarkLaf.class.getName(), FlatMacLightLaf.class.getName());

    private static boolean installed;

    private Looks() {
    }

    /** FlatLaf's looks among those Java offers (View ▸ Look), its native libraries off: once, before any is used. */
    static synchronized void install() {
        if (installed) {
            return;
        }
        System.setProperty("flatlaf.useNativeLibrary", "false");
        FlatLightLaf.installLafInfo();
        FlatDarkLaf.installLafInfo();
        FlatIntelliJLaf.installLafInfo();
        FlatDarculaLaf.installLafInfo();
        FlatMacLightLaf.installLafInfo();
        FlatMacDarkLaf.installLafInfo();
        installed = true;
    }

    /** A look, by its class; FlatLaf Light when that one cannot be had, Swing's own when neither: all work alike. */
    static void use(String className) {
        install();
        for (String look : new String[] {className, DEFAULT}) {
            try {
                UIManager.setLookAndFeel(look);
                return;
            } catch (ReflectiveOperationException | UnsupportedLookAndFeelException | RuntimeException e) {
                // the next one
            }
        }
    }

    /** Whether the look in use is dark. */
    static boolean isDark() {
        return FlatLaf.isLafDark();
    }

    /** The same look, dark or light: FlatLaf's counterpart; FlatLaf Dark or Light for a look that has none. */
    static String inMode(String className, boolean dark) {
        boolean isDark = className.equals(FlatDarkLaf.class.getName()) || className.equals(FlatDarculaLaf.class.getName())
                || className.equals(FlatMacDarkLaf.class.getName());
        if (isDark == dark && COUNTERPARTS.containsKey(className)) {
            return className;
        }
        return COUNTERPARTS.getOrDefault(className,
                dark ? FlatDarkLaf.class.getName() : FlatLightLaf.class.getName());
    }
}
