package com.siberanka.split.util;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ExpressionEvaluator {

    /**
     * Evaluates a boolean/logical/relational expression.
     * Supported operators: &&, ||, >, <, >=, <=, ==, !=, >>, <<, <>
     *
     * @param expr   The formula string to evaluate
     * @param logger Plugin logger for error logging
     * @param debug  Whether debug logging is enabled
     * @return boolean evaluation result
     */
    public static boolean evaluate(String expr, Logger logger, boolean debug) {
        if (expr == null || expr.trim().isEmpty()) {
            return false;
        }

        try {
            String normalized = expr.trim()
                    .replace(">>", ">")
                    .replace("<<", "<")
                    .replace("<>", "!=")
                    .replace(" AND ", " && ")
                    .replace(" and ", " && ")
                    .replace(" OR ", " || ")
                    .replace(" or ", " || ");

            // 1. Evaluate OR (||) - lowest precedence
            String[] orParts = normalized.split("\\|\\|");
            if (orParts.length > 1) {
                for (String part : orParts) {
                    if (evaluate(part, logger, debug)) {
                        return true;
                    }
                }
                return false;
            }

            // 2. Evaluate AND (&&) - higher precedence
            String[] andParts = normalized.split("&&");
            if (andParts.length > 1) {
                for (String part : andParts) {
                    if (!evaluate(part, logger, debug)) {
                        return false;
                    }
                }
                return true;
            }

            // 3. Evaluate single relation condition
            return evaluateRelation(normalized, logger, debug);
        } catch (Throwable t) {
            if (debug && logger != null) {
                logger.log(Level.WARNING, "Error evaluating expression: " + expr, t);
            }
            return false;
        }
    }

    private static boolean evaluateRelation(String expr, Logger logger, boolean debug) {
        expr = expr.trim();

        String op = null;
        int opIdx = -1;

        // Operators scanned in order of size to match multi-char before single-char
        String[] operators = {">=", "<=", "==", "!=", ">", "<", "="};
        for (String possibleOp : operators) {
            int idx = expr.indexOf(possibleOp);
            if (idx != -1) {
                op = possibleOp;
                opIdx = idx;
                break;
            }
        }

        if (op == null) {
            // Treat as raw boolean check
            String val = expr.toLowerCase();
            return val.equals("true") || val.equals("yes") || val.equals("1");
        }

        String leftStr = expr.substring(0, opIdx).trim();
        String rightStr = expr.substring(opIdx + op.length()).trim();

        // Strip enclosing quotes from string values
        leftStr = stripQuotes(leftStr);
        rightStr = stripQuotes(rightStr);

        // Numeric Comparison
        try {
            double leftNum = Double.parseDouble(leftStr);
            double rightNum = Double.parseDouble(rightStr);

            switch (op) {
                case ">=": return leftNum >= rightNum;
                case "<=": return leftNum <= rightNum;
                case "==":
                case "=":  return leftNum == rightNum;
                case "!=": return leftNum != rightNum;
                case ">":  return leftNum > rightNum;
                case "<":  return leftNum < rightNum;
            }
        } catch (NumberFormatException ignored) {
            // Fallback to string comparison
        }

        // String Lexicographical Comparison
        int comp = leftStr.compareTo(rightStr);
        switch (op) {
            case "==":
            case "=":  return leftStr.equals(rightStr);
            case "!=": return !leftStr.equals(rightStr);
            case ">=": return comp >= 0;
            case "<=": return comp <= 0;
            case ">":  return comp > 0;
            case "<":  return comp < 0;
        }

        return false;
    }

    private static String stripQuotes(String str) {
        if ((str.startsWith("\"") && str.endsWith("\"")) || (str.startsWith("'") && str.endsWith("'"))) {
            return str.substring(1, str.length() - 1);
        }
        return str;
    }
}
