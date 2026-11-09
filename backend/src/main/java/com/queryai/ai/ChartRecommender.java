package com.queryai.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Deterministic chart recommendation from result metadata (column categories, cardinality and value ranges).
 * Rule-based on purpose: the choice is explainable and never depends on an LLM guess.
 */
public final class ChartRecommender {

    public enum ChartType { TABLE, BAR, LINE, PIE, SCATTER, METRIC }

    public enum Kind { NUMBER, TEXT, DATE, BOOLEAN, OTHER }

    public record Column(String name, Kind kind) {
    }

    public record Recommendation(ChartType type, String xColumn, List<String> yColumns, String reason,
                                 List<ChartType> alternatives) {
    }

    private static final Pattern TIME_NAME = Pattern.compile(".*(date|day|week|month|year|quarter|period|time|_at)$");
    private static final Pattern DATE_VALUE = Pattern.compile("^\\d{4}(-\\d{2})?(-\\d{2})?([ T].*)?$");

    private ChartRecommender() {
    }

    public static Recommendation recommend(List<Column> columns, List<List<Object>> rows) {
        if (rows == null || rows.isEmpty() || columns.isEmpty()) {
            return new Recommendation(ChartType.TABLE, null, List.of(), "No rows to visualise", List.of());
        }
        List<Integer> measures = new ArrayList<>();
        List<Integer> timeDims = new ArrayList<>();
        List<Integer> textDims = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            Column c = columns.get(i);
            String name = c.name().toLowerCase(Locale.ROOT);
            boolean idLike = name.equals("id") || name.endsWith("_id");
            if (c.kind() == Kind.DATE || (TIME_NAME.matcher(name).matches() && looksTemporal(rows, i, c.kind()))) {
                timeDims.add(i);
            } else if (c.kind() == Kind.NUMBER && !idLike) {
                measures.add(i);
            } else if (c.kind() == Kind.TEXT || c.kind() == Kind.BOOLEAN || idLike) {
                textDims.add(i);
            }
        }
        if (measures.isEmpty()) {
            return new Recommendation(ChartType.TABLE, null, List.of(), "No numeric measure to plot", List.of());
        }
        List<String> y = measures.stream().limit(3).map(i -> columns.get(i).name()).toList();

        if (rows.size() == 1 && timeDims.isEmpty() && textDims.isEmpty()) {
            return new Recommendation(ChartType.METRIC, null, y, "Single-row numeric result", List.of(ChartType.TABLE));
        }
        if (!timeDims.isEmpty() && rows.size() >= 2) {
            return new Recommendation(ChartType.LINE, columns.get(timeDims.get(0)).name(), y,
                    "Time dimension '" + columns.get(timeDims.get(0)).name() + "' with numeric measures shows a trend",
                    List.of(ChartType.BAR, ChartType.TABLE));
        }
        if (!textDims.isEmpty()) {
            String x = columns.get(textDims.get(0)).name();
            boolean allNonNegative = rows.stream().allMatch(r -> r.get(measures.get(0)) instanceof Number n && n.doubleValue() >= 0);
            if (rows.size() <= 8 && measures.size() == 1 && allNonNegative) {
                return new Recommendation(ChartType.PIE, x, y, "Few categories with one non-negative measure: share of total",
                        List.of(ChartType.BAR, ChartType.TABLE));
            }
            return new Recommendation(ChartType.BAR, x, y, rows.size() > 30
                    ? "Categorical comparison (showing first 30 categories)" : "Compare a measure across categories",
                    List.of(ChartType.TABLE, ChartType.PIE));
        }
        if (measures.size() >= 2 && rows.size() >= 5) {
            return new Recommendation(ChartType.SCATTER, columns.get(measures.get(0)).name(),
                    List.of(columns.get(measures.get(1)).name()), "Two numeric measures: look for correlation",
                    List.of(ChartType.TABLE));
        }
        return new Recommendation(ChartType.TABLE, null, y, "Tabular view is the clearest for this shape", List.of(ChartType.BAR));
    }

    private static boolean looksTemporal(List<List<Object>> rows, int col, Kind kind) {
        Object v = rows.get(0).get(col);
        if (v == null) {
            return false;
        }
        if (kind == Kind.NUMBER && v instanceof Number n) {
            double d = n.doubleValue();
            return (d >= 1900 && d <= 2200) || (d >= 1 && d <= 12 && col == 0);
        }
        return DATE_VALUE.matcher(v.toString()).matches();
    }
}
