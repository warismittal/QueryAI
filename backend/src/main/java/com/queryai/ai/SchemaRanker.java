package com.queryai.ai;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Lexical relevance ranking of tables for a question (schema retrieval). Scores table names, column names and
 * descriptions against question tokens with light stemming, then pulls in foreign-key neighbours so joins remain
 * possible. Dependency-free so it is fast and deterministic.
 */
public final class SchemaRanker {

    public record TableInfo(String name, List<String> columns, String description, List<String> columnDescriptions,
                            Set<String> neighbours) {
    }

    private static final Set<String> STOP = Set.of("the", "a", "an", "of", "by", "for", "and", "or", "to", "in", "on", "with",
            "show", "me", "list", "what", "which", "who", "how", "many", "much", "is", "are", "was", "were", "each", "per",
            "top", "this", "that", "last", "year", "month", "week", "day", "their", "them", "than", "more", "less", "compare",
            "all", "give", "find", "get", "between", "from", "into", "as", "at", "be", "do", "does", "did", "most", "least");

    private SchemaRanker() {
    }

    public static List<String> rank(String question, List<String> extraKeywords, List<TableInfo> tables, int maxTables) {
        return rank(question, extraKeywords, tables, maxTables, Map.of());
    }

    /**
     * Hybrid ranking: lexical score plus a semantic bonus from embedding similarity ({@code semantic}: table name to
     * cosine similarity). Similarities are min-max normalised so the bonus is comparable across embedding models.
     * When every table fits, all are returned, most relevant first.
     */
    public static List<String> rank(String question, List<String> extraKeywords, List<TableInfo> tables, int maxTables,
                                    Map<String, Double> semantic) {
        Map<String, Double> bonus = semanticBonus(semantic);
        if (tables.size() <= maxTables) {
            if (bonus.isEmpty()) {
                return tables.stream().map(TableInfo::name).toList();
            }
            return tables.stream().map(TableInfo::name)
                    .sorted(Comparator.comparingDouble((String n) -> -bonus.getOrDefault(n, 0.0)).thenComparing(n -> n)).toList();
        }
        Set<String> terms = new HashSet<>(tokens(question));
        if (extraKeywords != null) {
            extraKeywords.forEach(k -> terms.addAll(tokens(k)));
        }
        Map<String, Double> scores = new HashMap<>();
        for (TableInfo t : tables) {
            double score = 0;
            Set<String> nameTokens = new HashSet<>(tokens(t.name().replace('_', ' ')));
            for (String term : terms) {
                if (nameTokens.contains(term)) {
                    score += 5;
                }
                for (String col : t.columns()) {
                    if (tokens(col.replace('_', ' ')).contains(term)) {
                        score += 2;
                    }
                }
                if (t.description() != null && tokens(t.description()).contains(term)) {
                    score += 1.5;
                }
                for (String d : t.columnDescriptions()) {
                    if (d != null && tokens(d).contains(term)) {
                        score += 0.5;
                    }
                }
            }
            scores.put(t.name(), score + bonus.getOrDefault(t.name(), 0.0));
        }
        List<String> ordered = tables.stream().map(TableInfo::name)
                .sorted(Comparator.comparingDouble((String n) -> -scores.get(n)).thenComparing(n -> n)).toList();

        LinkedHashSet<String> selected = new LinkedHashSet<>();
        int primary = Math.max(1, maxTables * 2 / 3);
        for (String name : ordered) {
            if (selected.size() >= primary || scores.get(name) <= 0) {
                break;
            }
            selected.add(name);
        }
        if (selected.isEmpty()) {
            ordered.stream().limit(maxTables).forEach(selected::add);
            return new ArrayList<>(selected);
        }
        // add FK neighbours of the selected tables (join paths), highest scoring first
        Map<String, TableInfo> byName = tables.stream().collect(Collectors.toMap(TableInfo::name, t -> t));
        List<String> neighbours = selected.stream().flatMap(n -> byName.get(n).neighbours().stream())
                .filter(byName::containsKey).distinct()
                .sorted(Comparator.comparingDouble((String n) -> -scores.getOrDefault(n, 0.0))).toList();
        for (String n : neighbours) {
            if (selected.size() >= maxTables) {
                break;
            }
            selected.add(n);
        }
        return new ArrayList<>(selected);
    }

    /** Maps cosine similarities to a 0..6 bonus; spreads under 0.02 carry no signal and are ignored. */
    static Map<String, Double> semanticBonus(Map<String, Double> semantic) {
        if (semantic == null || semantic.isEmpty()) {
            return Map.of();
        }
        double min = semantic.values().stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = semantic.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        if (max - min < 0.02) {
            return Map.of();
        }
        Map<String, Double> out = new HashMap<>();
        semantic.forEach((table, sim) -> out.put(table, 6.0 * (sim - min) / (max - min)));
        return out;
    }

    static List<String> tokens(String text) {
        if (text == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (raw.length() < 2 || STOP.contains(raw)) {
                continue;
            }
            out.add(stem(raw));
        }
        return out;
    }

    static String stem(String w) {
        if (w.endsWith("ies") && w.length() > 4) {
            return w.substring(0, w.length() - 3) + "y";
        }
        if (w.endsWith("ing") && w.length() > 5) {
            return w.substring(0, w.length() - 3);
        }
        if (w.endsWith("es") && w.length() > 4 && (w.endsWith("ses") || w.endsWith("xes") || w.endsWith("ches"))) {
            return w.substring(0, w.length() - 2);
        }
        if (w.endsWith("s") && !w.endsWith("ss") && w.length() > 3) {
            return w.substring(0, w.length() - 1);
        }
        return w;
    }
}
