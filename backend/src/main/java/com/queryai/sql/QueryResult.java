package com.queryai.sql;

import java.util.List;

/**
 * Real rows returned by a target database. Values are JSON-friendly (numbers, strings, booleans, null).
 * {@code maskedColumns} names the result columns whose values were masked because they contain personal data.
 */
public record QueryResult(List<ResultColumn> columns, List<List<Object>> rows, int rowCount, boolean truncated,
                          long durationMs, int maxRows, List<String> maskedColumns) {

    public QueryResult {
        maskedColumns = maskedColumns == null ? List.of() : List.copyOf(maskedColumns);
    }

    public QueryResult(List<ResultColumn> columns, List<List<Object>> rows, int rowCount, boolean truncated,
                       long durationMs, int maxRows) {
        this(columns, rows, rowCount, truncated, durationMs, maxRows, List.of());
    }

    public enum Category { NUMBER, TEXT, DATE, BOOLEAN, OTHER }

    public record ResultColumn(String name, String sqlType, Category category) {
    }
}
