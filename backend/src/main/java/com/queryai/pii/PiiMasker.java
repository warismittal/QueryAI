package com.queryai.pii;

import com.queryai.sql.QueryResult;
import com.queryai.sql.QueryResult.Category;
import com.queryai.sql.QueryResult.ResultColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure masking functions. Column masking keeps just enough shape to stay useful in analysis (an email keeps its domain,
 * a card its last four digits, a date of birth its year); value scrubbing catches personal data that appears in other
 * text columns (e.g. an email inside a notes field or a column renamed with an alias).
 */
public final class PiiMasker {
    public static final String DOTS = "••••";

    private static final Pattern EMAIL = Pattern.compile("([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})");
    private static final Pattern DIGIT_RUN = Pattern.compile("\\+?[\\d(][\\d\\s().-]{8,}\\d");
    private static final Pattern DATE_LIKE = Pattern.compile("\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}.*|\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4}.*");
    private static final Pattern SSN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern YEAR_PREFIX = Pattern.compile("^(\\d{4})[-/.].*");

    private PiiMasker() {
    }

    /**
     * Masks whole columns ({@code columnTypes}: result column label, lower-case, to its PII type) and, when
     * {@code scrubValues} is set, personal data patterns in every other text cell.
     */
    public static QueryResult apply(QueryResult result, Map<String, PiiType> columnTypes, boolean scrubValues) {
        int n = result.columns().size();
        PiiType[] types = new PiiType[n];
        List<ResultColumn> columns = new ArrayList<>(n);
        List<String> masked = new ArrayList<>(result.maskedColumns());
        for (int i = 0; i < n; i++) {
            ResultColumn c = result.columns().get(i);
            types[i] = columnTypes.get(c.name().toLowerCase(Locale.ROOT));
            if (types[i] != null) {
                columns.add(new ResultColumn(c.name(), "masked " + types[i].name().toLowerCase(Locale.ROOT), Category.TEXT));
                if (!masked.contains(c.name())) {
                    masked.add(c.name());
                }
            } else {
                columns.add(c);
            }
        }
        boolean anyColumn = masked.size() > result.maskedColumns().size();
        if (!anyColumn && !scrubValues) {
            return result;
        }
        boolean scrubbedSomething = false;
        List<List<Object>> rows = new ArrayList<>(result.rows().size());
        for (List<Object> row : result.rows()) {
            List<Object> out = new ArrayList<>(row.size());
            for (int i = 0; i < row.size(); i++) {
                Object v = row.get(i);
                if (i < n && types[i] != null) {
                    out.add(maskValue(v, types[i]));
                } else if (scrubValues && v instanceof String s && i < n && result.columns().get(i).category() != Category.DATE) {
                    String scrubbed = scrubText(s);
                    if (!scrubbed.equals(s)) {
                        scrubbedSomething = true;
                        String name = result.columns().get(i).name();
                        if (!masked.contains(name)) {
                            masked.add(name);
                        }
                    }
                    out.add(scrubbed);
                } else {
                    out.add(v);
                }
            }
            rows.add(out);
        }
        if (!anyColumn && !scrubbedSomething) {
            return result;
        }
        return new QueryResult(columns, rows, result.rowCount(), result.truncated(), result.durationMs(), result.maxRows(), masked);
    }

    public static Object maskValue(Object value, PiiType type) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        if (s.isEmpty()) {
            return s;
        }
        return switch (type) {
            case EMAIL -> {
                Matcher m = EMAIL.matcher(s);
                yield m.matches() ? m.group(1) + DOTS + "@" + m.group(2) : DOTS;
            }
            case PERSON_NAME -> s.substring(0, 1).toUpperCase(Locale.ROOT) + DOTS;
            case PHONE, PAYMENT_CARD, BANK_ACCOUNT -> {
                String digits = s.replaceAll("\\D", "");
                int keep = type == PiiType.PHONE ? 2 : 4;
                yield digits.length() > keep + 3 ? DOTS + " " + digits.substring(digits.length() - keep) : DOTS;
            }
            case POSTAL_CODE -> s.length() > 3 ? s.substring(0, 2) + DOTS : DOTS;
            case DATE_OF_BIRTH -> {
                Matcher m = YEAR_PREFIX.matcher(s);
                yield m.matches() ? m.group(1) + "-••-••" : DOTS;
            }
            default -> DOTS;
        };
    }

    /** Replaces emails, card/phone-like digit runs and SSN-like patterns inside free text. */
    public static String scrubText(String text) {
        if (text == null || text.length() < 6) {
            return text;
        }
        String out = text;
        if (out.indexOf('@') >= 0) {
            out = EMAIL.matcher(out).replaceAll(mr -> Matcher.quoteReplacement(mr.group(1) + DOTS + "@" + mr.group(2)));
        }
        out = SSN.matcher(out).replaceAll(DOTS);
        Matcher m = DIGIT_RUN.matcher(out);
        StringBuilder sb = new StringBuilder();
        boolean changed = false;
        while (m.find()) {
            String candidate = m.group();
            long digits = candidate.chars().filter(Character::isDigit).count();
            boolean looksPersonal = digits >= 10 && digits <= 19 && !DATE_LIKE.matcher(candidate).matches();
            if (looksPersonal) {
                String d = candidate.replaceAll("\\D", "");
                m.appendReplacement(sb, Matcher.quoteReplacement(DOTS + " " + d.substring(d.length() - 2)));
                changed = true;
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(candidate));
            }
        }
        m.appendTail(sb);
        return changed ? sb.toString() : out;
    }
}
