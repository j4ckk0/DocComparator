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

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A number as Excel shows it through a number format ("0.00", "dd/mm/yyyy", "[$€-40C] #,##0.00"…), but in the same
 * conventions on every machine, so that a comparison does not depend on where it runs: a point for decimals, a comma
 * between thousands, English month and day names, format 14 as 2026-11-05. Fractions and conditions are not rendered.
 */
final class NumberFormats {

    /** Excel's built-in formats, by id, as a workbook names them without writing them. */
    private static final Map<Integer, String> BUILT_IN = Map.ofEntries(
            Map.entry(0, "General"), Map.entry(1, "0"), Map.entry(2, "0.00"), Map.entry(3, "#,##0"),
            Map.entry(4, "#,##0.00"), Map.entry(5, "#,##0;-#,##0"), Map.entry(6, "#,##0;[Red]-#,##0"),
            Map.entry(7, "#,##0.00;-#,##0.00"), Map.entry(8, "#,##0.00;[Red]-#,##0.00"), Map.entry(9, "0%"),
            Map.entry(10, "0.00%"), Map.entry(11, "0.00E+00"), Map.entry(12, "# ?/?"), Map.entry(13, "# ??/??"),
            Map.entry(14, "yyyy-mm-dd"), Map.entry(15, "d-mmm-yy"), Map.entry(16, "d-mmm"), Map.entry(17, "mmm-yy"),
            Map.entry(18, "h:mm AM/PM"), Map.entry(19, "h:mm:ss AM/PM"), Map.entry(20, "h:mm"),
            Map.entry(21, "h:mm:ss"), Map.entry(22, "yyyy-mm-dd h:mm"), Map.entry(37, "#,##0 ;(#,##0)"),
            Map.entry(38, "#,##0 ;[Red](#,##0)"), Map.entry(39, "#,##0.00;(#,##0.00)"),
            Map.entry(40, "#,##0.00;[Red](#,##0.00)"), Map.entry(45, "mm:ss"), Map.entry(46, "[h]:mm:ss"),
            Map.entry(47, "mm:ss.0"), Map.entry(48, "##0.0E+0"), Map.entry(49, "@"));

    static final String GENERAL = "General";

    private NumberFormats() {
    }

    /** The code of a built-in format; General for an id Excel does not define. */
    static String builtIn(int id) {
        return BUILT_IN.getOrDefault(id, GENERAL);
    }

    /** The value through the format's code, its dates counted from 1904 when the workbook says so. */
    static String format(double value, String code, boolean date1904) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return String.valueOf(value);
        }
        List<String> sections = sections(code);
        String section;
        boolean signed = false;
        if (value < 0 && sections.size() > 1) {
            section = sections.get(1);   // the negative section writes its own sign or parentheses
        } else if (value == 0 && sections.size() > 2) {
            section = sections.get(2);
        } else {
            section = sections.getFirst();
            signed = value < 0;
        }
        List<Token> tokens = tokens(section);
        try {
            if (tokens.stream().anyMatch(t -> t.kind == Kind.DATE || t.kind == Kind.ELAPSED)) {
                return date(value, tokens, date1904);
            }
            if (tokens.stream().anyMatch(t -> t.kind == Kind.NUMBER)) {
                return number(sections.size() > 1 && value < 0 ? -value : value, signed, tokens);
            }
        } catch (RuntimeException _) {
            return general(value);   // a format this subset misreads: the value as it is
        }
        if (section.isEmpty() || tokens.stream().allMatch(t -> t.text.equals("@"))) {
            return general(value);   // no format, or text's: the value as it is
        }
        StringBuilder out = new StringBuilder();
        for (Token token : tokens) {
            out.append(token.text.equals(GENERAL) ? general(value) : token.text);   // General" kg": the value, then kg
        }
        return out.toString();
    }

    /** General: the value to the 15 significant digits Excel keeps, without trailing zeros; scientific when large. */
    static String general(double value) {
        if (value == 0) {
            return "0";
        }
        BigDecimal rounded = new BigDecimal(value).round(new MathContext(15, RoundingMode.HALF_EVEN))
                .stripTrailingZeros();
        int exponent = rounded.precision() - rounded.scale() - 1;
        if (exponent >= 15 || exponent < -9) {
            String unscaled = rounded.unscaledValue().abs().toString();
            String mantissa = unscaled.length() > 1 ? unscaled.charAt(0) + "." + unscaled.substring(1) : unscaled;
            return (value < 0 ? "-" : "") + mantissa + "E" + (exponent < 0 ? "-" : "+")
                    + String.format(Locale.ROOT, "%02d", Math.abs(exponent));
        }
        return rounded.toPlainString();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The code, in tokens

    private enum Kind { LITERAL, NUMBER, DATE, ELAPSED, AMPM }

    private record Token(Kind kind, String text) {
    }

    /** The code's sections, split on the semicolons outside quotes, brackets and escapes. */
    private static List<String> sections(String code) {
        List<String> sections = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        int brackets = 0;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '\\' && !quoted && i + 1 < code.length()) {
                current.append(c).append(code.charAt(++i));
                continue;
            }
            if (c == '"') {
                quoted = !quoted;
            } else if (!quoted && c == '[') {
                brackets++;
            } else if (!quoted && c == ']') {
                brackets--;
            } else if (!quoted && brackets == 0 && c == ';') {
                sections.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        sections.add(current.toString());
        return sections;
    }

    /** A section's tokens: literals, the number's placeholders, the date's parts. */
    private static List<Token> tokens(String section) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        while (i < section.length()) {
            char c = section.charAt(i);
            if (c == '"') {
                int end = section.indexOf('"', i + 1);
                end = end < 0 ? section.length() : end;
                tokens.add(new Token(Kind.LITERAL, section.substring(i + 1, end)));
                i = end + 1;
            } else if (c == '\\' && i + 1 < section.length()) {
                tokens.add(new Token(Kind.LITERAL, String.valueOf(section.charAt(i + 1))));
                i += 2;
            } else if (c == '_' && i + 1 < section.length()) {
                tokens.add(new Token(Kind.LITERAL, " "));   // a space as wide as the next character
                i += 2;
            } else if (c == '*' && i + 1 < section.length()) {
                i += 2;   // the column filled with the next character: nothing in a comparison
            } else if (c == '[') {
                int end = section.indexOf(']', i);
                end = end < 0 ? section.length() - 1 : end;
                String inside = section.substring(i + 1, end);
                String lower = inside.toLowerCase(Locale.ROOT);
                if (lower.matches("h+|m+|s+")) {
                    tokens.add(new Token(Kind.ELAPSED, lower));
                } else if (inside.startsWith("$")) {
                    int dash = inside.indexOf('-');
                    tokens.add(new Token(Kind.LITERAL, inside.substring(1, dash < 0 ? inside.length() : dash)));
                }
                i = end + 1;   // a colour, a condition, a locale: not shown
            } else if ("0#?.,%".indexOf(c) >= 0 || (c == 'E' || c == 'e') && i + 1 < section.length()
                    && (section.charAt(i + 1) == '+' || section.charAt(i + 1) == '-')) {
                int start = i;
                while (i < section.length()) {
                    char d = section.charAt(i);
                    if ("0#?.,%".indexOf(d) >= 0) {
                        i++;
                    } else if ((d == 'E' || d == 'e') && i + 1 < section.length()
                            && (section.charAt(i + 1) == '+' || section.charAt(i + 1) == '-')) {
                        i += 2;
                    } else {
                        break;
                    }
                }
                String text = section.substring(start, i);
                tokens.add(new Token(text.chars().anyMatch(d -> "0#?".indexOf(d) >= 0) ? Kind.NUMBER : Kind.LITERAL,
                        text));
            } else if (section.regionMatches(true, i, "AM/PM", 0, 5)) {
                tokens.add(new Token(Kind.AMPM, "AM/PM"));
                i += 5;
            } else if (section.regionMatches(true, i, "A/P", 0, 3)) {
                tokens.add(new Token(Kind.AMPM, "A/P"));
                i += 3;
            } else if ("yYmMdDhHsS".indexOf(c) >= 0) {
                int start = i;
                char lower = Character.toLowerCase(c);
                while (i < section.length() && Character.toLowerCase(section.charAt(i)) == lower) {
                    i++;
                }
                tokens.add(new Token(Kind.DATE, section.substring(start, i).toLowerCase(Locale.ROOT)));
            } else if (section.regionMatches(true, i, "General", 0, 7)) {
                tokens.add(new Token(Kind.LITERAL, GENERAL));
                i += 7;
            } else {
                tokens.add(new Token(Kind.LITERAL, String.valueOf(c)));
                i++;
            }
        }
        return tokens;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Numbers

    /** The value through the section's number placeholders; the literals around them kept in place. */
    private static String number(double value, boolean signed, List<Token> tokens) {
        StringBuilder spec = new StringBuilder();
        for (Token token : tokens) {
            if (token.kind == Kind.NUMBER || token.kind == Kind.LITERAL && token.text.matches("[.,%]+")) {
                spec.append(token.text);
            }
        }
        String placeholders = spec.toString();
        int percents = (int) placeholders.chars().filter(c -> c == '%').count();
        int exponentAt = Math.max(placeholders.indexOf('E'), placeholders.indexOf('e'));
        String mantissa = exponentAt >= 0 ? placeholders.substring(0, exponentAt) : placeholders;
        String digits = mantissa.replace("%", "");
        int point = digits.indexOf('.');
        String integer = point >= 0 ? digits.substring(0, point) : digits;
        String fraction = point >= 0 ? digits.substring(point + 1).replace(",", "") : "";
        int scaleDown = 0;   // commas after the last digit: thousands
        while (integer.endsWith(",")) {
            integer = integer.substring(0, integer.length() - 1);
            scaleDown++;
        }
        boolean grouped = integer.contains(",");
        int minInteger = (int) integer.chars().filter(c -> c == '0').count();
        int minFraction = (int) fraction.chars().filter(c -> c == '0').count();
        int maxFraction = (int) fraction.chars().filter(c -> c == '0' || c == '#' || c == '?').count();

        BigDecimal number = new BigDecimal(Math.abs(value)).movePointRight(2 * percents).movePointLeft(3 * scaleDown);
        String shown;
        if (exponentAt >= 0) {
            int minExponent = (int) placeholders.substring(exponentAt + 2).chars().filter(c -> c == '0').count();
            int exponent = number.signum() == 0 ? 0 : number.precision() - number.scale() - 1;
            BigDecimal scaled = number.movePointLeft(exponent);
            shown = digits(scaled, Math.max(1, minInteger), minFraction, maxFraction, false) + "E"
                    + (exponent < 0 ? "-" : "+") + pad(Math.abs(exponent), Math.max(1, minExponent));
        } else {
            shown = digits(number, minInteger, minFraction, maxFraction, grouped);
        }
        StringBuilder out = new StringBuilder(signed && !shown.matches("[0.,E+-]*") ? "-" : "");
        boolean placed = false;
        for (Token token : tokens) {
            if (token.kind == Kind.NUMBER || token.kind == Kind.LITERAL && token.text.matches("[.,%]+")) {
                if (!placed) {
                    out.append(shown);
                    placed = true;
                }
                out.append("%".repeat((int) token.text.chars().filter(c -> c == '%').count()));
            } else if (token.kind == Kind.LITERAL && !token.text.equals(GENERAL)) {
                out.append(token.text);
            }
        }
        return out.toString();
    }

    private static String digits(BigDecimal number, int minInteger, int minFraction, int maxFraction,
            boolean grouped) {
        BigDecimal rounded = number.setScale(maxFraction, RoundingMode.HALF_UP);
        String plain = rounded.toPlainString();
        int point = plain.indexOf('.');
        String integer = point >= 0 ? plain.substring(0, point) : plain;
        String fraction = point >= 0 ? plain.substring(point + 1) : "";
        while (fraction.length() > minFraction && fraction.endsWith("0")) {
            fraction = fraction.substring(0, fraction.length() - 1);
        }
        if (integer.equals("0") && minInteger == 0) {
            integer = "";
        }
        while (integer.length() < minInteger) {
            integer = "0" + integer;
        }
        if (grouped) {
            StringBuilder withCommas = new StringBuilder();
            for (int i = 0; i < integer.length(); i++) {
                if (i > 0 && (integer.length() - i) % 3 == 0) {
                    withCommas.append(',');
                }
                withCommas.append(integer.charAt(i));
            }
            integer = withCommas.toString();
        }
        return fraction.isEmpty() ? integer : integer + "." + fraction;
    }

    private static String pad(int n, int width) {
        String text = String.valueOf(n);
        return "0".repeat(Math.max(0, width - text.length())) + text;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Dates and times

    /** A date and time from Excel's serial number: days since 1900 (or 1904), the time as the fraction of a day. */
    static LocalDateTime dateTime(double serial, boolean date1904) {
        long days = (long) Math.floor(serial);
        long millis = Math.round((serial - days) * 86_400_000L);
        LocalDate date;
        if (date1904) {
            date = LocalDate.of(1904, 1, 1).plusDays(days);
        } else if (days < 60) {
            date = LocalDate.of(1899, 12, 31).plusDays(days);
        } else {
            date = LocalDate.of(1899, 12, 30).plusDays(days);   // past Excel's 29 February 1900, which never was
        }
        return date.atStartOfDay().plusNanos(millis * 1_000_000L);
    }

    private static String date(double value, List<Token> tokens, boolean date1904) {
        boolean twelveHours = tokens.stream().anyMatch(t -> t.kind == Kind.AMPM);
        int fractionDigits = 0;
        for (int i = 0; i < tokens.size() - 1; i++) {
            if (tokens.get(i).kind == Kind.DATE && tokens.get(i).text.startsWith("s")
                    && tokens.get(i + 1).kind == Kind.NUMBER && tokens.get(i + 1).text.startsWith(".")) {
                fractionDigits = (int) tokens.get(i + 1).text.chars().filter(c -> c == '0').count();
            }
        }
        double rounding = fractionDigits > 0 ? 0 : 0.5 / 86_400;   // shown to the second: rounded to it
        LocalDateTime when = dateTime(value + rounding, date1904);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            switch (token.kind) {
                case DATE -> out.append(datePart(token.text, when, minutes(tokens, i), twelveHours));
                case ELAPSED -> out.append(elapsed(token.text, value));
                case AMPM -> out.append(when.getHour() < 12
                        ? (token.text.equals("A/P") ? "A" : "AM") : (token.text.equals("A/P") ? "P" : "PM"));
                case NUMBER -> {
                    if (token.text.startsWith(".") && fractionDigits > 0) {
                        long nanos = when.getNano();
                        String fraction = pad((int) (nanos / 1_000_000), 3);
                        out.append('.').append(fraction, 0, Math.min(3, fractionDigits));
                    } else {
                        out.append(token.text);
                    }
                }
                default -> {
                    if (!token.text.equals(GENERAL)) {
                        out.append(token.text);
                    }
                }
            }
        }
        return out.toString();
    }

    /** Whether the m at this place is minutes: after hours, or before seconds, literals between. */
    private static boolean minutes(List<Token> tokens, int at) {
        if (!tokens.get(at).text.startsWith("m")) {
            return false;
        }
        for (int i = at - 1; i >= 0; i--) {
            Token before = tokens.get(i);
            if (before.kind == Kind.DATE || before.kind == Kind.ELAPSED) {
                if (before.text.startsWith("h")) {
                    return true;
                }
                break;
            }
        }
        for (int i = at + 1; i < tokens.size(); i++) {
            Token after = tokens.get(i);
            if (after.kind == Kind.DATE || after.kind == Kind.ELAPSED) {
                return after.text.startsWith("s");
            }
        }
        return false;
    }

    private static String datePart(String part, LocalDateTime when, boolean minutes, boolean twelveHours) {
        int hour12 = when.getHour() % 12 == 0 ? 12 : when.getHour() % 12;
        return switch (part.charAt(0)) {
            case 'y' -> part.length() <= 2 ? pad(when.getYear() % 100, 2) : pad(when.getYear(), 4);
            case 'd' -> switch (part.length()) {
                case 1 -> String.valueOf(when.getDayOfMonth());
                case 2 -> pad(when.getDayOfMonth(), 2);
                case 3 -> when.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
                default -> when.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            };
            case 'h' -> {
                int hour = twelveHours ? hour12 : when.getHour();
                yield part.length() >= 2 ? pad(hour, 2) : String.valueOf(hour);
            }
            case 's' -> part.length() >= 2 ? pad(when.getSecond(), 2) : String.valueOf(when.getSecond());
            default -> minutes
                    ? (part.length() >= 2 ? pad(when.getMinute(), 2) : String.valueOf(when.getMinute()))
                    : switch (part.length()) {
                        case 1 -> String.valueOf(when.getMonthValue());
                        case 2 -> pad(when.getMonthValue(), 2);
                        case 3 -> when.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
                        case 5 -> when.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).substring(0, 1);
                        default -> when.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
                    };
        };
    }

    /** [h], [mm], [ss]: the whole duration in that unit, not wrapped at a day. */
    private static String elapsed(String unit, double days) {
        long seconds = Math.round(days * 86_400);
        long amount = switch (unit.charAt(0)) {
            case 'h' -> seconds / 3600;
            case 'm' -> seconds / 60;
            default -> seconds;
        };
        return pad((int) amount, unit.length());
    }
}
