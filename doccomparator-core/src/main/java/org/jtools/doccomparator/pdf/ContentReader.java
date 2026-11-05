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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jtools.doccomparator.i18n.I18n;
import org.jtools.doccomparator.pdf.PdfObjects.Bytes;
import org.jtools.doccomparator.pdf.PdfObjects.Name;
import org.jtools.doccomparator.pdf.PdfObjects.Operator;
import org.jtools.doccomparator.pdf.PdfObjects.Stream;

/**
 * Runs a page's content streams as far as its text goes (ISO 32000-2, 9.4): the glyphs shown, in the order they are
 * drawn. Paths, images and shadings are passed over; form XObjects are entered.
 */
final class ContentReader {

    /** The deepest form XObjects are entered within one another. */
    private static final int DEEPEST_FORM = 12;

    /** Where a running header or footer is said to be, by a tagged PDF's /Artifact marks. */
    enum Artifact {
        NONE, HEADER, FOOTER, PAGINATION
    }

    /**
     * A glyph shown, in page space: its characters (Encodings.UNKNOWN when its font does not say), its size in points,
     * its colour (RRGGBB, null for black or one not known), whether the page rather than the font makes it bold or
     * slanted, and whether its characters are unknown because its font cannot be read.
     */
    record Glyph(String text, double x, double y, double endX, double endY, double dx, double dy, double size,
                 PdfFont font, String color, boolean drawnBold, boolean slanted, Artifact artifact,
                 boolean unreadableFont) {
    }

    private static final class State {
        double[] ctm = {1, 0, 0, 1, 0, 0};
        double charSpacing;
        double wordSpacing;
        double horizontalScale = 1;
        double leading;
        PdfFont font;
        double fontSize = 1;
        double rise;
        int render;
        String fill;
        String stroke;
        Object fillSpace = new Name("DeviceGray");
        Object strokeSpace = new Name("DeviceGray");

        State copy() {
            State copy = new State();
            copy.ctm = ctm.clone();
            copy.charSpacing = charSpacing;
            copy.wordSpacing = wordSpacing;
            copy.horizontalScale = horizontalScale;
            copy.leading = leading;
            copy.font = font;
            copy.fontSize = fontSize;
            copy.rise = rise;
            copy.render = render;
            copy.fill = fill;
            copy.stroke = stroke;
            copy.fillSpace = fillSpace;
            copy.strokeSpace = strokeSpace;
            return copy;
        }
    }

    private final PdfFile file;
    private final Map<Map<String, Object>, PdfFont> fonts;
    private final List<Glyph> glyphs = new ArrayList<>();
    private final Set<Stream> entered = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Deque<Artifact> marked = new ArrayDeque<>();
    private final long[] glyphsLeft;
    private State state = new State();
    private final Deque<State> saved = new ArrayDeque<>();
    private double[] textMatrix = {1, 0, 0, 1, 0, 0};
    private double[] lineMatrix = {1, 0, 0, 1, 0, 0};

    private ContentReader(PdfFile file, Map<Map<String, Object>, PdfFont> fonts, long[] glyphsLeft) {
        this.file = file;
        this.fonts = fonts;
        this.glyphsLeft = glyphsLeft;
    }

    /**
     * The glyphs of a page's content, its resources those it names; fonts read once for the file, glyphsLeft counted
     * down for the whole file.
     */
    static List<Glyph> read(PdfFile file, byte[] content, Map<String, Object> resources,
                            Map<Map<String, Object>, PdfFont> fonts, long[] glyphsLeft) throws PdfException {
        ContentReader reader = new ContentReader(file, fonts, glyphsLeft);
        reader.run(content, resources, 0);
        return reader.glyphs;
    }

    private void run(byte[] content, Map<String, Object> resources, int depth) throws PdfException {
        Lexer lexer = new Lexer(content, 0);
        List<Object> operands = new ArrayList<>();
        while (true) {
            Object value = lexer.next();
            if (value == Lexer.END) {
                return;
            }
            if (!(value instanceof Operator operator)) {
                if (operands.size() < 64) {
                    operands.add(value);
                }
                continue;
            }
            String op = operator.name();
            if (op.equals("BI")) {
                skipInlineImage(lexer);
            } else {
                operate(op, operands, resources, depth);
            }
            operands.clear();
        }
    }

    private void operate(String op, List<Object> operands, Map<String, Object> resources, int depth)
            throws PdfException {
        switch (op) {
            case "q" -> {
                if (saved.size() < 256) {
                    saved.push(state.copy());
                }
            }
            case "Q" -> {
                if (!saved.isEmpty()) {
                    state = saved.pop();
                }
            }
            case "cm" -> {
                double[] m = matrix(operands);
                if (m != null) {
                    state.ctm = multiply(m, state.ctm);
                }
            }
            case "BT" -> {
                textMatrix = new double[] {1, 0, 0, 1, 0, 0};
                lineMatrix = textMatrix.clone();
            }
            case "Tc" -> state.charSpacing = number(operands, 0);
            case "Tw" -> state.wordSpacing = number(operands, 0);
            case "Tz" -> state.horizontalScale = number(operands, 100) / 100;
            case "TL" -> state.leading = number(operands, 0);
            case "Ts" -> state.rise = number(operands, 0);
            case "Tr" -> state.render = (int) number(operands, 0);
            case "Tf" -> {
                if (operands.size() >= 2) {
                    state.font = font(resources, PdfObjects.name(operands.get(0)));
                    state.fontSize = PdfObjects.number(operands.get(1), 1);
                }
            }
            case "Td" -> {
                if (operands.size() >= 2) {
                    moveLine(PdfObjects.number(operands.get(0), 0), PdfObjects.number(operands.get(1), 0));
                }
            }
            case "TD" -> {
                if (operands.size() >= 2) {
                    state.leading = -PdfObjects.number(operands.get(1), 0);
                    moveLine(PdfObjects.number(operands.get(0), 0), PdfObjects.number(operands.get(1), 0));
                }
            }
            case "Tm" -> {
                double[] m = matrix(operands);
                if (m != null) {
                    textMatrix = m;
                    lineMatrix = m.clone();
                }
            }
            case "T*" -> moveLine(0, -state.leading);
            case "Tj" -> {
                if (!operands.isEmpty() && operands.getLast() instanceof Bytes bytes) {
                    show(bytes.value());
                }
            }
            case "'" -> {
                moveLine(0, -state.leading);
                if (!operands.isEmpty() && operands.getLast() instanceof Bytes bytes) {
                    show(bytes.value());
                }
            }
            case "\"" -> {
                if (operands.size() >= 3) {
                    state.wordSpacing = PdfObjects.number(operands.get(0), 0);
                    state.charSpacing = PdfObjects.number(operands.get(1), 0);
                }
                moveLine(0, -state.leading);
                if (!operands.isEmpty() && operands.getLast() instanceof Bytes bytes) {
                    show(bytes.value());
                }
            }
            case "TJ" -> {
                if (!operands.isEmpty() && operands.getLast() instanceof List<?> items) {
                    for (Object item : items) {
                        if (item instanceof Bytes bytes) {
                            show(bytes.value());
                        } else if (item instanceof Number adjustment) {
                            advance(-adjustment.doubleValue() / 1000 * state.fontSize * state.horizontalScale);
                        }
                    }
                }
            }
            case "g" -> {
                state.fillSpace = new Name("DeviceGray");
                state.fill = color(state.fillSpace, operands, resources);
            }
            case "G" -> {
                state.strokeSpace = new Name("DeviceGray");
                state.stroke = color(state.strokeSpace, operands, resources);
            }
            case "rg" -> {
                state.fillSpace = new Name("DeviceRGB");
                state.fill = color(state.fillSpace, operands, resources);
            }
            case "RG" -> {
                state.strokeSpace = new Name("DeviceRGB");
                state.stroke = color(state.strokeSpace, operands, resources);
            }
            case "k" -> {
                state.fillSpace = new Name("DeviceCMYK");
                state.fill = color(state.fillSpace, operands, resources);
            }
            case "K" -> {
                state.strokeSpace = new Name("DeviceCMYK");
                state.stroke = color(state.strokeSpace, operands, resources);
            }
            case "cs" -> {
                if (!operands.isEmpty()) {
                    state.fillSpace = operands.getFirst();
                    state.fill = null;   // a space's first colour: black, or one not known
                }
            }
            case "CS" -> {
                if (!operands.isEmpty()) {
                    state.strokeSpace = operands.getFirst();
                    state.stroke = null;
                }
            }
            case "sc", "scn" -> state.fill = color(state.fillSpace, operands, resources);
            case "SC", "SCN" -> state.stroke = color(state.strokeSpace, operands, resources);
            case "gs" -> graphicsState(resources, operands);
            case "Do" -> {
                if (!operands.isEmpty()) {
                    form(resources, PdfObjects.name(operands.getFirst()), depth);
                }
            }
            case "BMC" -> marked.push(marked.isEmpty() ? Artifact.NONE : marked.peek());
            case "BDC" -> marked.push(artifact(operands, resources));
            case "EMC" -> {
                if (!marked.isEmpty()) {
                    marked.pop();
                }
            }
            default -> {
                // paths, clipping, shadings, images, compatibility sections: no text in them
            }
        }
    }

    private Artifact artifact(List<Object> operands, Map<String, Object> resources) {
        Artifact outer = marked.isEmpty() ? Artifact.NONE : marked.peek();
        if (operands.isEmpty() || !"Artifact".equals(PdfObjects.name(operands.getFirst()))) {
            return outer;
        }
        Object properties = operands.size() > 1 ? operands.get(1) : null;
        if (properties instanceof Name name) {
            Map<String, Object> all = PdfObjects.dictionary(file.resolve(resources.get("Properties")));
            properties = all == null ? null : file.resolve(all.get(name.value()));
        }
        Map<String, Object> dictionary = PdfObjects.dictionary(properties);
        if (dictionary == null || !"Pagination".equals(PdfObjects.name(file.resolve(dictionary.get("Type"))))) {
            return outer;
        }
        String subtype = PdfObjects.name(file.resolve(dictionary.get("Subtype")));
        return "Header".equals(subtype) ? Artifact.HEADER
                : "Footer".equals(subtype) ? Artifact.FOOTER : Artifact.PAGINATION;
    }

    private void graphicsState(Map<String, Object> resources, List<Object> operands) {
        if (operands.isEmpty()) {
            return;
        }
        Map<String, Object> states = PdfObjects.dictionary(file.resolve(resources.get("ExtGState")));
        Map<String, Object> gs = states == null ? null
                : PdfObjects.dictionary(file.resolve(states.get(PdfObjects.name(operands.getFirst()))));
        List<?> font = gs == null ? null : PdfObjects.array(file.resolve(gs.get("Font")));
        if (font != null && font.size() >= 2) {
            Map<String, Object> dictionary = PdfObjects.dictionary(file.resolve(font.get(0)));
            if (dictionary != null) {
                state.font = fonts.computeIfAbsent(dictionary, d -> PdfFont.of(d, file));
                state.fontSize = PdfObjects.number(file.resolve(font.get(1)), 1);
            }
        }
    }

    private PdfFont font(Map<String, Object> resources, String name) {
        Map<String, Object> all = PdfObjects.dictionary(file.resolve(resources.get("Font")));
        Map<String, Object> dictionary = all == null || name == null ? null
                : PdfObjects.dictionary(file.resolve(all.get(name)));
        return dictionary == null ? null : fonts.computeIfAbsent(dictionary, d -> PdfFont.of(d, file));
    }

    private void form(Map<String, Object> resources, String name, int depth) throws PdfException {
        Map<String, Object> objects = PdfObjects.dictionary(file.resolve(resources.get("XObject")));
        if (objects == null || name == null || depth >= DEEPEST_FORM) {
            return;
        }
        if (!(file.resolve(objects.get(name)) instanceof Stream stream)
                || !"Form".equals(PdfObjects.name(file.resolve(stream.dictionary().get("Subtype"))))
                || !entered.add(stream)) {
            return;
        }
        try {
            byte[] content = file.decoded(stream);
            if (content == null) {
                return;
            }
            Map<String, Object> own = PdfObjects.dictionary(file.resolve(stream.dictionary().get("Resources")));
            State outer = state.copy();
            double[] matrix = matrix(PdfObjects.array(file.resolve(stream.dictionary().get("Matrix"))));
            if (matrix != null) {
                state.ctm = multiply(matrix, state.ctm);
            }
            double[] text = textMatrix;
            double[] line = lineMatrix;
            int savedDepth = saved.size();
            run(content, own != null ? own : resources, depth + 1);
            while (saved.size() > savedDepth) {
                saved.pop();
            }
            state = outer;
            textMatrix = text;
            lineMatrix = line;
        } finally {
            entered.remove(stream);
        }
    }

    private void moveLine(double tx, double ty) {
        lineMatrix = multiply(new double[] {1, 0, 0, 1, tx, ty}, lineMatrix);
        textMatrix = lineMatrix.clone();
    }

    private void advance(double tx) {
        textMatrix = multiply(new double[] {1, 0, 0, 1, tx, 0}, textMatrix);
    }

    private void show(byte[] bytes) throws PdfException {
        PdfFont font = state.font;
        if (font == null) {
            return;
        }
        double size = state.fontSize;
        double scale = state.horizontalScale;
        for (PdfFont.Code code : font.codes(bytes)) {
            double[] m = multiply(textMatrix, state.ctm);
            double width = code.width() * size * scale;
            double x = state.rise * m[2] + m[4];
            double y = state.rise * m[3] + m[5];
            double endX = width * m[0] + state.rise * m[2] + m[4];
            double endY = width * m[1] + state.rise * m[3] + m[5];
            double length = Math.hypot(m[0], m[1]);
            double height = Math.hypot(m[2], m[3]);
            double points = Math.abs(size) * height;
            if (length > 0 && height > 0 && points > 0.5) {
                double dx = m[0] / length;
                double dy = m[1] / length;
                boolean slanted = Math.abs((m[0] * m[2] + m[1] * m[3]) / (length * height)) > 0.15;
                String color = state.render == 1 || state.render == 5 ? state.stroke : state.fill;
                String characters = code.characters() == null ? Encodings.UNKNOWN : code.characters();
                if (--glyphsLeft[0] < 0) {
                    throw new PdfException(file.error(I18n.text("error.pdfTooLarge", PdfReader.LARGEST_TEXT)));
                }
                glyphs.add(new Glyph(characters, x, y, endX, endY, dx, dy, points, font,
                        "000000".equals(color) ? null : color, state.render == 2 || state.render == 6, slanted,
                        marked.isEmpty() ? Artifact.NONE : marked.peek(),
                        font.unreadable && code.characters() == null));
            }
            advance((code.width() * size + state.charSpacing + (code.singleByteSpace() ? state.wordSpacing : 0))
                    * scale);
        }
    }

    private String color(Object space, List<Object> operands, Map<String, Object> resources) {
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

    private static double number(List<Object> operands, double otherwise) {
        return operands.isEmpty() ? otherwise : PdfObjects.number(operands.getLast(), otherwise);
    }

    private static double[] matrix(List<?> operands) {
        if (operands == null || operands.size() < 6) {
            return null;
        }
        double[] m = new double[6];
        int from = operands.size() - 6;
        for (int i = 0; i < 6; i++) {
            if (!(operands.get(from + i) instanceof Number number)) {
                return null;
            }
            m[i] = number.doubleValue();
        }
        return m;
    }

    static double[] multiply(double[] m1, double[] m2) {
        return new double[] {
            m1[0] * m2[0] + m1[1] * m2[2],
            m1[0] * m2[1] + m1[1] * m2[3],
            m1[2] * m2[0] + m1[3] * m2[2],
            m1[2] * m2[1] + m1[3] * m2[3],
            m1[4] * m2[0] + m1[5] * m2[2] + m2[4],
            m1[4] * m2[1] + m1[5] * m2[3] + m2[5]};
    }

    /** Past an inline image's data, to the "EI" that ends it, on whitespace on both sides. */
    private static void skipInlineImage(Lexer lexer) {
        while (true) {
            Object value = lexer.next();
            if (value == Lexer.END) {
                return;
            }
            if (value instanceof Operator operator && operator.name().equals("ID")) {
                break;
            }
        }
        byte[] data = lexer.data();
        int i = lexer.position() + 1;
        while (i + 2 <= data.length) {
            if (data[i] == 'E' && i + 1 < data.length && data[i + 1] == 'I' && Lexer.isWhitespace(data[i - 1] & 0xFF)
                    && (i + 2 == data.length || Lexer.isWhitespace(data[i + 2] & 0xFF))) {
                lexer.position(i + 2);
                return;
            }
            i++;
        }
        lexer.position(data.length);
    }
}
