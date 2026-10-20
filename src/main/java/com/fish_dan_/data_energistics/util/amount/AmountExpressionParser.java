package com.fish_dan_.data_energistics.util.amount;

import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** Shared numeric-input parser; integer quantities remain exact while decimal settings use DECIMAL128 division. */
@NullMarked
public final class AmountExpressionParser {

    private AmountExpressionParser() {}

    /** Accepts a positive integral amount, including unit suffixes and arithmetic; invalid input returns empty. */
    public static OptionalLong parse(String input) {
        return parseLong(input, 1, Long.MAX_VALUE);
    }

    /**
     * Stock settings allow zero for clearing and retain fractional resource units only when they represent whole base
     * units. The unit size must be positive; invalid input returns empty.
     */
    public static OptionalLong parseStockAmount(String input, long amountPerUnit) {
        if (amountPerUnit <= 0) throw new IllegalArgumentException("Amount per unit must be positive");
        var result = evaluate(input, MathContext.UNLIMITED);
        return result.isPresent() ? boundedLong(result.get().multiply(BigDecimal.valueOf(amountPerUnit)), 0, Long.MAX_VALUE) : OptionalLong.empty();
    }

    /** Evaluates a signed integral expression within inclusive bounds; reversed bounds are a caller error. */
    public static OptionalLong parseLong(String input, long minimum, long maximum) {
        if (minimum > maximum) throw new IllegalArgumentException("Minimum amount exceeds maximum");
        var result = evaluate(input, MathContext.UNLIMITED);
        return result.isPresent() ? boundedLong(result.get(), minimum, maximum) : OptionalLong.empty();
    }

    /** Evaluates a signed integral expression within the caller's inclusive int bounds. */
    public static OptionalInt parseInt(String input, int minimum, int maximum) {
        var value = parseLong(input, minimum, maximum);
        return value.isPresent() ? OptionalInt.of((int) value.getAsLong()) : OptionalInt.empty();
    }

    /** Evaluates a decimal expression and rejects non-finite double results. */
    public static OptionalDouble parseDouble(String input) {
        var result = evaluate(input, MathContext.DECIMAL128);
        if (result.isEmpty()) return OptionalDouble.empty();
        double value = result.get().doubleValue();
        return Double.isFinite(value) ? OptionalDouble.of(value) : OptionalDouble.empty();
    }

    /** Evaluates a decimal expression within the finite float range. */
    public static Optional<Float> parseFloat(String input) {
        var result = evaluate(input, MathContext.DECIMAL128);
        if (result.isEmpty()) return Optional.empty();
        float value = result.get().floatValue();
        return Float.isFinite(value) ? Optional.of(value) : Optional.empty();
    }

    private static Optional<BigDecimal> evaluate(String input, MathContext divisionContext) {
        try {
            return Optional.of(new Parser(input, divisionContext).parse());
        } catch (IllegalArgumentException | ArithmeticException invalidExpression) {
            return Optional.empty();
        }
    }

    private static OptionalLong boundedLong(BigDecimal value, long minimum, long maximum) {
        if (value.compareTo(BigDecimal.valueOf(minimum)) < 0 || value.compareTo(BigDecimal.valueOf(maximum)) > 0 ||
                value.stripTrailingZeros().scale() > 0)
            return OptionalLong.empty();
        return OptionalLong.of(value.longValueExact());
    }

    private static final class Parser {

        private final String s;
        private final MathContext divisionContext;
        private int p;

        Parser(String input, MathContext divisionContext) {
            this.divisionContext = divisionContext;
            var normalized = input.trim();
            if (normalized.startsWith("=")) {
                normalized = normalized.substring(1);
            }
            s = normalized.replace(" ", "").toLowerCase(Locale.ROOT);
        }

        BigDecimal parse() {
            BigDecimal v = expression();
            if (p != s.length()) throw new IllegalArgumentException();
            return v;
        }

        BigDecimal expression() {
            BigDecimal v = term();
            while (p < s.length()) {
                char c = s.charAt(p);
                if (c == '+') {
                    p++;
                    v = v.add(term());
                } else if (c == '-') {
                    p++;
                    v = v.subtract(term());
                } else break;
            }
            return v;
        }

        BigDecimal term() {
            BigDecimal v = unary();
            while (p < s.length()) {
                char c = s.charAt(p);
                if (c == '*') {
                    p++;
                    v = v.multiply(unary());
                } else if (c == '/') {
                    p++;
                    BigDecimal d = unary();
                    if (d.signum() == 0) throw new ArithmeticException();
                    v = v.divide(d, divisionContext);
                } else if (c == '(' || Character.isDigit(c) || c == '.') {
                    v = v.multiply(unary());
                } else break;
            }
            return v;
        }

        BigDecimal unary() {
            if (p < s.length() && (s.charAt(p) == '+' || s.charAt(p) == '-')) {
                boolean negative = s.charAt(p++) == '-';
                BigDecimal value = unary();
                return negative ? value.negate() : value;
            }
            return power();
        }

        BigDecimal power() {
            BigDecimal v = primary();
            if (p < s.length() && s.charAt(p) == '^') {
                p++;
                int n = unary().intValueExact();
                v = v.pow(n);
            }
            return v;
        }

        BigDecimal primary() {
            if (p < s.length() && s.charAt(p) == '(') {
                p++;
                BigDecimal v = expression();
                if (p >= s.length() || s.charAt(p++) != ')') throw new IllegalArgumentException();
                return v;
            }
            int st = p;
            while (p < s.length() && (Character.isDigit(s.charAt(p)) || s.charAt(p) == '.')) p++;
            if (st == p) throw new IllegalArgumentException();
            if (p < s.length() && s.charAt(p) == 'e' && p + 1 < s.length() && (Character.isDigit(s.charAt(p + 1)) || s.charAt(p + 1) == '+' || s.charAt(p + 1) == '-')) {
                p++;
                if (p < s.length() && (s.charAt(p) == '+' || s.charAt(p) == '-')) p++;
                int es = p;
                while (p < s.length() && Character.isDigit(s.charAt(p))) p++;
                if (es == p) throw new IllegalArgumentException();
            }
            BigDecimal v = new BigDecimal(s.substring(st, p));
            if (p < s.length()) {
                char u = s.charAt(p);
                long m = switch (u) {
                    case 'k' -> 1_000L;
                    case 'm' -> 1_000_000L;
                    case 'g' -> 1_000_000_000L;
                    case 't' -> 1_000_000_000_000L;
                    case 'p' -> 1_000_000_000_000_000L;
                    case 'e' -> 1_000_000_000_000_000_000L;
                    default -> 0L;
                };
                if (m != 0) {
                    p++;
                    v = v.multiply(BigDecimal.valueOf(m));
                }
            }
            return v;
        }
    }
}
