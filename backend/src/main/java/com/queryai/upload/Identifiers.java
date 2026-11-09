package com.queryai.upload;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns file names and CSV headers into safe, unquoted-friendly PostgreSQL identifiers (lower snake_case, no reserved
 * words, unique, at most 60 characters) so users and the AI can write plain SQL against uploaded tables.
 */
public final class Identifiers {
    private static final int MAX = 60;
    private static final Set<String> RESERVED = Set.of("all", "analyse", "analyze", "and", "any", "array", "as", "asc",
            "asymmetric", "both", "case", "cast", "check", "collate", "column", "constraint", "create", "current_catalog",
            "current_date", "current_role", "current_time", "current_timestamp", "current_user", "default", "deferrable",
            "desc", "distinct", "do", "else", "end", "except", "false", "fetch", "for", "foreign", "from", "grant", "group",
            "having", "in", "initially", "intersect", "into", "lateral", "leading", "limit", "localtime", "localtimestamp",
            "not", "null", "offset", "on", "only", "or", "order", "placing", "primary", "references", "returning", "select",
            "session_user", "some", "symmetric", "table", "then", "to", "trailing", "true", "union", "unique", "user",
            "using", "variadic", "when", "where", "window", "with", "join", "left", "right", "inner", "outer", "full",
            "cross", "natural", "is", "isnull", "notnull", "like", "ilike", "similar", "between", "verbose", "authorization",
            "binary", "freeze", "overlaps", "tablesample", "concurrently");

    private Identifiers() {
    }

    public static String sanitize(String raw, String fallback) {
        String s = raw == null ? "" : Normalizer.normalize(raw, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        s = s.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
        s = s.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (s.isEmpty()) {
            s = fallback;
        }
        if (Character.isDigit(s.charAt(0))) {
            s = "c_" + s;
        }
        if (s.length() > MAX) {
            s = s.substring(0, MAX).replaceAll("_+$", "");
        }
        if (RESERVED.contains(s)) {
            s = s + "_value";
        }
        return s;
    }

    /** Sanitises every header and de-duplicates (name, name_2, name_3 ...). */
    public static List<String> columns(List<String> headers) {
        List<String> out = new ArrayList<>(headers.size());
        Set<String> used = new HashSet<>();
        for (int i = 0; i < headers.size(); i++) {
            String base = sanitize(headers.get(i), "column_" + (i + 1));
            String name = base;
            int k = 2;
            while (!used.add(name)) {
                String suffix = "_" + k++;
                name = (base.length() + suffix.length() > MAX ? base.substring(0, MAX - suffix.length()) : base) + suffix;
            }
            out.add(name);
        }
        return out;
    }

    public static String tableFromFileName(String fileName) {
        String base = fileName == null ? "" : fileName.replaceAll("(?i)\\.(csv|tsv|txt)$", "");
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        return sanitize(base, "uploaded_data");
    }

    public static boolean isSafe(String identifier) {
        return identifier != null && identifier.matches("[a-z_][a-z0-9_]{0,62}");
    }
}
