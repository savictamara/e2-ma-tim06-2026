package rs.ac.uns.ftn.slagalica.util;

import android.util.Log;

public class ExpressionEvaluator {
    private static final String TAG = "ExpressionEvaluator";
    private String input;
    private int pos;

    public double evaluate(String expression) {
        try {
            input = expression.replaceAll("\\s+", "");
            pos = 0;
            double value = parseExpression();
            if (pos != input.length()) {
                throw new IllegalArgumentException("Neispravan izraz");
            }
            Log.d(TAG, "Expression evaluated, expression=" + expression + ", result=" + value);
            return value;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Expression invalid, expression=" + expression + ", error=" + e.getMessage());
            throw e;
        }
    }

    private double parseExpression() {
        double value = parseTerm();
        while (pos < input.length()) {
            char op = input.charAt(pos);
            if (op != '+' && op != '-') {
                break;
            }
            pos++;
            double right = parseTerm();
            value = op == '+' ? value + right : value - right;
        }
        return value;
    }

    private double parseTerm() {
        double value = parseFactor();
        while (pos < input.length()) {
            char op = input.charAt(pos);
            if (op != '*' && op != '/') {
                break;
            }
            pos++;
            double right = parseFactor();
            if (op == '/' && Math.abs(right) < 0.000001) {
                throw new IllegalArgumentException("Deljenje nulom nije dozvoljeno");
            }
            value = op == '*' ? value * right : value / right;
        }
        return value;
    }

    private double parseFactor() {
        if (pos >= input.length()) {
            throw new IllegalArgumentException("Nepotpun izraz");
        }
        char c = input.charAt(pos);
        if (c == '(') {
            pos++;
            double value = parseExpression();
            if (pos >= input.length() || input.charAt(pos) != ')') {
                throw new IllegalArgumentException("Nedostaje zatvorena zagrada");
            }
            pos++;
            return value;
        }
        if (c == '-') {
            pos++;
            return -parseFactor();
        }
        if (!Character.isDigit(c)) {
            throw new IllegalArgumentException("Dozvoljeni su samo brojevi i operatori");
        }
        int start = pos;
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            pos++;
        }
        return Double.parseDouble(input.substring(start, pos));
    }
}
