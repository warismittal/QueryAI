package com.queryai.evaluation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Execution-accuracy comparison used by the evaluation harness: the generated query passes when it returns the same
 * number of rows and every column of the expected result appears (as a multiset of values) among the generated
 * columns. This tolerates column order, extra columns, aliases and row order, which are irrelevant to correctness.
 */
public final class ResultComparator {

    public record Comparison(boolean match, String reason) {
    }

    private ResultComparator() {
    }

    public static Comparison compare(List<List<Object>> expected, List<List<Object>> actual) {
        if (expected.size() != actual.size()) {
            return new Comparison(false, "Row count differs: expected " + expected.size() + ", got " + actual.size());
        }
        if (expected.isEmpty()) {
            return new Comparison(true, "Both results are empty");
        }
        int expectedCols = expected.get(0).size();
        int actualCols = actual.get(0).size();
        List<Map<String, Integer>> actualColumns = new ArrayList<>();
        for (int c = 0; c < actualCols; c++) {
            actualColumns.add(multiset(actual, c));
        }
        boolean[] used = new boolean[actualCols];
        for (int c = 0; c < expectedCols; c++) {
            Map<String, Integer> exp = multiset(expected, c);
            boolean found = false;
            for (int a = 0; a < actualCols; a++) {
                if (!used[a] && actualColumns.get(a).equals(exp)) {
                    used[a] = true;
                    found = true;
                    break;
                }
            }
            if (!found) {
                return new Comparison(false, "Expected column #" + (c + 1) + " has no matching column in the generated result");
            }
        }
        return new Comparison(true, "All " + expectedCols + " expected columns matched across " + expected.size() + " rows");
    }

    private static Map<String, Integer> multiset(List<List<Object>> rows, int col) {
        Map<String, Integer> counts = new HashMap<>();
        for (List<Object> row : rows) {
            counts.merge(normalize(row.get(col)), 1, Integer::sum);
        }
        return counts;
    }

    static String normalize(Object v) {
        if (v == null) {
            return "∅";
        }
        if (v instanceof Number n) {
            try {
                return new BigDecimal(n.toString()).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
            } catch (NumberFormatException e) {
                return n.toString();
            }
        }
        String s = v.toString().trim();
        try {
            return new BigDecimal(s).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return s.toLowerCase(Locale.ROOT);
        }
    }
}
