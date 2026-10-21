package de.hugosmp.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Wandelt Kürzel wie 150k, 1.5m, 2kk, 3mio, 1b in volle Zahlen um. */
public final class NumberParser {
    private static final Pattern TOKEN = Pattern.compile(
            "(?<![\\w.,])(\\d+(?:[.,]\\d+)?)(mio|mrd|kk|k|m|b)(?!\\w)", Pattern.CASE_INSENSITIVE);
    private static final Pattern THOUSANDS = Pattern.compile("^\\d{1,3}([.,]\\d{3})+$");

    private NumberParser() {}

    public static BigDecimal multiplier(String suffix) {
        if (suffix == null) return BigDecimal.ONE;
        switch (suffix.toLowerCase(Locale.ROOT)) {
            case "k": return BigDecimal.valueOf(1_000L);
            case "kk":
            case "m":
            case "mio": return BigDecimal.valueOf(1_000_000L);
            case "b":
            case "mrd": return BigDecimal.valueOf(1_000_000_000L);
            default: return BigDecimal.ONE;
        }
    }

    /** Ersetzt alle Kürzel in einem beliebigen Text. "pay Steve 150k" -> "pay Steve 150000". */
    public static String expand(String input) {
        if (input == null || input.isEmpty()) return input;
        Matcher m = TOKEN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            BigDecimal v = new BigDecimal(m.group(1).replace(',', '.')).multiply(multiplier(m.group(2)));
            m.appendReplacement(sb, Matcher.quoteReplacement(v.stripTrailingZeros().toPlainString()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Parst einen Betrag (mit oder ohne Kürzel). Leer, wenn ungültig. */
    public static OptionalLong parseAmount(String text) {
        if (text == null) return OptionalLong.empty();
        String t = text.trim().replace("$", "").replace("_", "").replace(" ", "");
        if (t.isEmpty()) return OptionalLong.empty();
        if (THOUSANDS.matcher(t).matches()) t = t.replace(".", "").replace(",", "");
        t = expand(t);
        try {
            BigDecimal bd = new BigDecimal(t.replace(',', '.')).setScale(0, RoundingMode.DOWN);
            if (bd.toBigInteger().bitLength() > 62) return OptionalLong.empty();
            return OptionalLong.of(bd.longValue());
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    public static String pretty(long v) {
        return NumberFormat.getIntegerInstance(Locale.GERMANY).format(v);
    }

    public static String pretty(BigDecimal v) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.GERMANY);
        nf.setMaximumFractionDigits(2);
        return nf.format(v.stripTrailingZeros());
    }

    /** Kleiner Taschenrechner: + - * / ( ) und Kürzel, z. B. "150k*3+2m". */
    public static BigDecimal eval(String expr) {
        return new Calc(expand(expr)).parse();
    }

    private static final class Calc {
        private final String s;
        private int p = 0;

        Calc(String s) {
            this.s = s;
        }

        BigDecimal parse() {
            BigDecimal v = expr();
            skip();
            if (p < s.length()) throw new IllegalArgumentException("Unerwartetes Zeichen: " + s.charAt(p));
            return v;
        }

        private void skip() {
            while (p < s.length() && Character.isWhitespace(s.charAt(p))) p++;
        }

        private boolean eat(char c) {
            skip();
            if (p < s.length() && s.charAt(p) == c) {
                p++;
                return true;
            }
            return false;
        }

        private BigDecimal expr() {
            BigDecimal v = term();
            while (true) {
                if (eat('+')) v = v.add(term());
                else if (eat('-')) v = v.subtract(term());
                else return v;
            }
        }

        private BigDecimal term() {
            BigDecimal v = factor();
            while (true) {
                if (eat('*') || eat('x')) v = v.multiply(factor());
                else if (eat('/')) {
                    BigDecimal d = factor();
                    if (d.signum() == 0) throw new IllegalArgumentException("Division durch 0");
                    v = v.divide(d, 6, RoundingMode.HALF_UP);
                } else return v;
            }
        }

        private BigDecimal factor() {
            skip();
            if (eat('-')) return factor().negate();
            if (eat('(')) {
                BigDecimal v = expr();
                if (!eat(')')) throw new IllegalArgumentException("Klammer nicht geschlossen");
                return v;
            }
            int st = p;
            while (p < s.length() && (Character.isDigit(s.charAt(p)) || s.charAt(p) == '.')) p++;
            if (st == p) throw new IllegalArgumentException("Zahl erwartet");
            return new BigDecimal(s.substring(st, p));
        }
    }
}
