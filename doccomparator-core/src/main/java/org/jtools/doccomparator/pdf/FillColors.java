package org.jtools.doccomparator.pdf;

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
import java.util.Map;
import org.jtools.doccomparator.pdf.PdfObjects.Bytes;
import org.jtools.doccomparator.pdf.PdfObjects.Name;
import org.jtools.doccomparator.pdf.PdfObjects.Stream;

/**
 * The colour text is drawn in, from a colour space and its operands, as Word writes a colour (RRGGBB): gray, RGB and
 * CMYK, whether named or ICC-based, and indexed ones over them; null for a pattern or a separation, whose colour is not
 * known here.
 */
final class FillColors {

    private final PdfFile file;

    FillColors(PdfFile file) {
        this.file = file;
    }

    /** The colour these operands give in this space, as RRGGBB; null for one not known. */
    String of(Object space, List<Object> operands, Map<String, Object> resources) {
        List<Double> values = new ArrayList<>();
        for (Object operand : operands) {
            if (operand instanceof Number number) {
                values.add(number.doubleValue());
            }
        }
        Object resolved = resolveSpace(space, resources);
        List<?> array = PdfObjects.array(resolved);
        if (array != null && !array.isEmpty() && "Indexed".equals(PdfObjects.name(file.resolve(array.getFirst())))
                && array.size() >= 4 && !values.isEmpty()) {
            Object base = file.resolve(array.get(1));
            int n = components(base, resources);
            Object lookup = file.resolve(array.get(3));
            byte[] table = lookup instanceof Bytes bytes ? bytes.value() : null;
            if (lookup instanceof Stream stream) {
                try {
                    table = file.decoded(stream);
                } catch (PdfException _) {
                    table = null;
                }
            }
            int index = (int) Math.round(values.getFirst());
            if (table == null || n <= 0 || (index + 1) * n > table.length || index < 0) {
                return null;
            }
            List<Double> entry = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                entry.add((table[index * n + i] & 0xFF) / 255.0);
            }
            return hex(entry, n);
        }
        int n = components(resolved, resources);
        if (values.size() < n || n == 0) {
            return null;
        }
        return hex(values.subList(0, n), n);
    }

    private static String hex(List<Double> values, int n) {
        double r;
        double g;
        double b;
        if (n == 1) {
            r = g = b = values.getFirst();
        } else if (n == 3) {
            r = values.get(0);
            g = values.get(1);
            b = values.get(2);
        } else if (n == 4) {
            double k = values.get(3);
            r = (1 - values.get(0)) * (1 - k);
            g = (1 - values.get(1)) * (1 - k);
            b = (1 - values.get(2)) * (1 - k);
        } else {
            return null;
        }
        return String.format(Locale.ROOT, "%02X%02X%02X", channel(r), channel(g), channel(b));
    }

    private static int channel(double value) {
        return (int) Math.round(Math.max(0, Math.min(1, value)) * 255);
    }

    private Object resolveSpace(Object space, Map<String, Object> resources) {
        if (space instanceof Name name && !name.value().startsWith("Device")) {
            Map<String, Object> spaces = PdfObjects.dictionary(file.resolve(resources.get("ColorSpace")));
            Object found = spaces == null ? null : file.resolve(spaces.get(name.value()));
            return found != null ? found : space;
        }
        return file.resolve(space);
    }

    /** The components of a colour space whose colours are known here: gray, RGB, CMYK; 0 for the others. */
    private int components(Object space, Map<String, Object> resources) {
        Object resolved = resolveSpace(space, resources);
        String name = PdfObjects.name(resolved);
        List<?> array = PdfObjects.array(resolved);
        if (array != null && !array.isEmpty()) {
            name = PdfObjects.name(file.resolve(array.getFirst()));
            if ("ICCBased".equals(name) && array.size() > 1
                    && PdfObjects.dictionary(file.resolve(array.get(1))) instanceof Map<String, Object> icc) {
                return PdfObjects.integer(file.resolve(icc.get("N")), 0);
            }
        }
        return switch (name == null ? "" : name) {
            case "DeviceGray", "CalGray", "G" -> 1;
            case "DeviceRGB", "CalRGB", "RGB" -> 3;
            case "DeviceCMYK", "CMYK" -> 4;
            default -> 0;
        };
    }
}
