package org.jtools.doccomparator.xlsx;


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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jtools.doccomparator.ooxml.OoxmlException;
import org.jtools.doccomparator.ooxml.XmlCursor;

/**
 * Excel's colours as Word's: six hexadecimal digits, "FF0000". Excel gives a colour by its value (with an alpha byte
 * first), by its place in the workbook's theme, lightened or darkened by a tint, or by its index in the legacy palette;
 * "auto" is none. A colour that cannot be resolved keeps its spec ("theme 12"), still compared, drawn as none.
 */
final class Colors {

    /** The 64 colours of Excel's legacy palette, by index, as the workbook does not change them. */
    private static final List<String> PALETTE = List.of(
            "000000", "FFFFFF", "FF0000", "00FF00", "0000FF", "FFFF00", "FF00FF", "00FFFF",
            "000000", "FFFFFF", "FF0000", "00FF00", "0000FF", "FFFF00", "FF00FF", "00FFFF",
            "800000", "008000", "000080", "808000", "800080", "008080", "C0C0C0", "808080",
            "9999FF", "993366", "FFFFCC", "CCFFFF", "660066", "FF8080", "0066CC", "CCCCFF",
            "000080", "FF00FF", "FFFF00", "00FFFF", "800080", "800000", "008080", "0000FF",
            "00CCFF", "CCFFFF", "CCFFCC", "FFFF99", "99CCFF", "FF99CC", "CC99FF", "FFCC99",
            "3366FF", "33CCCC", "99CC00", "FFCC00", "FF9900", "FF6600", "666699", "969696",
            "003366", "339966", "003300", "333300", "993300", "993366", "333399", "333333");

    /** The theme's colours in the order Excel numbers them: light 1, dark 1, light 2, dark 2, accents, links. */
    private static final List<String> THEME_ORDER = List.of("lt1", "dk1", "lt2", "dk2", "accent1", "accent2",
            "accent3", "accent4", "accent5", "accent6", "hlink", "folHlink");

    private final List<String> theme;
    private final List<String> palette;

    Colors(List<String> theme, List<String> palette) {
        this.theme = theme;
        this.palette = palette;
    }

    static final Colors NONE = new Colors(List.of(), PALETTE);

    /** These colours with the workbook's own palette (styles' indexedColors), when it has one. */
    Colors withPalette(List<String> indexed) {
        return indexed.isEmpty() ? this : new Colors(theme, indexed);
    }

    /** The colours of a theme part (xl/theme/theme1.xml): its colour scheme, in Excel's order. */
    static Colors ofTheme(XmlCursor xml) throws OoxmlException {
        xml.root("theme");
        String[] found = new String[THEME_ORDER.size()];
        readScheme(xml, found);
        List<String> theme = new ArrayList<>();
        for (String colour : found) {
            theme.add(colour == null ? "000000" : colour);
        }
        return new Colors(theme, PALETTE);
    }

    /** Down to a:clrScheme, whichever elements hold it; each of its colours into found, at its place. */
    private static void readScheme(XmlCursor xml, String[] found) throws OoxmlException {
        while (xml.nextChild()) {
            int place = THEME_ORDER.indexOf(xml.localName());
            if (place >= 0) {
                found[place] = schemeColour(xml);
            } else {
                readScheme(xml, found);
            }
        }
    }

    /** a:srgbClr val, or a:sysClr lastClr (the system's colour as last seen), consumed. */
    private static String schemeColour(XmlCursor xml) throws OoxmlException {
        String colour = null;
        while (xml.nextChild()) {
            String value = xml.localName().equals("sysClr") ? xml.plainAttribute("lastClr") : xml.plainAttribute("val");
            if (colour == null && value != null && value.length() == 6) {
                colour = value.toUpperCase(Locale.ROOT);
            }
            xml.skip();
        }
        return colour;
    }

    /** The colour of the element the cursor is on (a font's color, a fill's fgColor…); null for none or auto. */
    String of(XmlCursor xml) {
        if ("1".equals(xml.plainAttribute("auto"))) {
            return null;
        }
        String colour = base(xml.plainAttribute("rgb"), xml.plainAttribute("theme"), xml.plainAttribute("indexed"));
        String tint = xml.plainAttribute("tint");
        if (colour == null || tint == null || colour.length() != 6) {
            return colour;
        }
        try {
            return tinted(colour, Double.parseDouble(tint));
        } catch (NumberFormatException _) {
            return colour;
        }
    }

    private String base(String rgb, String themeIndex, String indexed) {
        if (rgb != null) {
            String hex = rgb.toUpperCase(Locale.ROOT);
            return hex.length() == 8 ? hex.substring(2) : hex;   // Excel writes the alpha byte first: FFFF0000
        }
        if (themeIndex != null) {
            int i = index(themeIndex);
            return i >= 0 && i < theme.size() ? theme.get(i) : "theme " + themeIndex;
        }
        if (indexed != null) {
            int i = index(indexed);
            if (i == 64) {
                return null;   // the system's foreground: automatic
            }
            return i >= 0 && i < palette.size() ? palette.get(i) : "indexed " + indexed;
        }
        return null;
    }

    private static int index(String number) {
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException _) {
            return -1;
        }
    }

    /** A colour lightened (tint above 0) or darkened (below), on its luminance, as Excel does. */
    static String tinted(String hex, double tint) {
        int rgb = Integer.parseInt(hex, 16);
        double r = (rgb >> 16 & 0xFF) / 255.0;
        double g = (rgb >> 8 & 0xFF) / 255.0;
        double b = (rgb & 0xFF) / 255.0;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double l = (max + min) / 2;
        double s = max == min ? 0 : l < 0.5 ? (max - min) / (max + min) : (max - min) / (2 - max - min);
        double h;
        if (max == min) {
            h = 0;
        } else if (max == r) {
            h = ((g - b) / (max - min) + (g < b ? 6 : 0)) / 6;
        } else if (max == g) {
            h = ((b - r) / (max - min) + 2) / 6;
        } else {
            h = ((r - g) / (max - min) + 4) / 6;
        }
        l = tint < 0 ? l * (1 + tint) : l * (1 - tint) + tint;
        double q = l < 0.5 ? l * (1 + s) : l + s - l * s;
        double p = 2 * l - q;
        return String.format(Locale.ROOT, "%02X%02X%02X", channel(p, q, h + 1.0 / 3), channel(p, q, h),
                channel(p, q, h - 1.0 / 3));
    }

    private static int channel(double p, double q, double t) {
        if (t < 0) {
            t += 1;
        }
        if (t > 1) {
            t -= 1;
        }
        double v;
        if (t < 1.0 / 6) {
            v = p + (q - p) * 6 * t;
        } else if (t < 1.0 / 2) {
            v = q;
        } else if (t < 2.0 / 3) {
            v = p + (q - p) * (2.0 / 3 - t) * 6;
        } else {
            v = p;
        }
        // rounded down, as Excel: white 25% darker is BFBFBF
        return (int) Math.floor(Math.max(0, Math.min(1, v)) * 255 + 1e-9);
    }
}
