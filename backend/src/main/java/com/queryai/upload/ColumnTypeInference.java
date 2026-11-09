package com.queryai.upload;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Infers a PostgreSQL column type for every CSV column from all of its values (not a sample), choosing the most
 * specific type every non-empty value fits, and converts raw strings into JDBC-ready Java values.
 */
public final class ColumnTypeInference {

    public enum SqlType {
        BOOLEAN("BOOLEAN"), BIGINT("BIGINT"), NUMERIC("NUMERIC"), DATE("DATE"), TIMESTAMP("TIMESTAMP"), TEXT("TEXT");

        private final String ddl;

        SqlType(String ddl) {
            this.ddl = ddl;
        }

        public String ddl() {
            return ddl;
        }
    }

    private static final Set<String> NULLS = Set.of("", "null", "na", "n/a", "nan", "none", "-", "—", "#n/a", "nil");
    private static final Set<String> TRUE = Set.of("true", "yes");
    private static final Set<String> FALSE = Set.of("false", "no");
    private static final Pattern INTEGER = Pattern.compile("[-+]?\\d{1,18}");
    private static final Pattern DECIMAL = Pattern.compile("[-+]?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d{1,3})?");
    private static final Pattern DATE = Pattern.compile("\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}");
    private static final Pattern TIMESTAMP = Pattern.compile(
            "\\d{4}-\\d{1,2}-\\d{1,2}[T ]\\d{1,2}:\\d{2}(:\\d{2}(\\.\\d{1,9})?)?(Z|[+-]\\d{2}:?\\d{2})?");
    private static final DateTimeFormatter SLASH_DATE = DateTimeFormatter.ofPattern("yyyy/M/d");
    private static final DateTimeFormatter DASH_DATE = DateTimeFormatter.ofPattern("yyyy-M-d");

    private ColumnTypeInference() {
    }

    public static boolean isNull(String raw) {
        return raw == null || NULLS.contains(raw.trim().toLowerCase(Locale.ROOT));
    }

    public static SqlType infer(List<String[]> rows, int column) {
        EnumSet<SqlType> possible = EnumSet.of(SqlType.BOOLEAN, SqlType.BIGINT, SqlType.NUMERIC, SqlType.DATE, SqlType.TIMESTAMP);
        boolean any = false;
        for (String[] row : rows) {
            String raw = column < row.length ? row[column] : null;
            if (isNull(raw)) {
                continue;
            }
            any = true;
            String v = raw.trim();
            String lower = v.toLowerCase(Locale.ROOT);
            if (possible.contains(SqlType.BOOLEAN) && !(TRUE.contains(lower) || FALSE.contains(lower))) {
                possible.remove(SqlType.BOOLEAN);
            }
            if (possible.contains(SqlType.BIGINT) && !(INTEGER.matcher(v).matches() && !hasLeadingZero(v))) {
                possible.remove(SqlType.BIGINT);
            }
            if (possible.contains(SqlType.NUMERIC) && !(DECIMAL.matcher(v).matches() && !hasLeadingZero(v))) {
                possible.remove(SqlType.NUMERIC);
            }
            boolean date = possible.contains(SqlType.DATE) || possible.contains(SqlType.TIMESTAMP) ? parseDate(v) != null : false;
            if (possible.contains(SqlType.DATE) && !date) {
                possible.remove(SqlType.DATE);
            }
            if (possible.contains(SqlType.TIMESTAMP) && !(date || parseTimestamp(v) != null)) {
                possible.remove(SqlType.TIMESTAMP);
            }
            if (possible.isEmpty()) {
                return SqlType.TEXT;
            }
        }
        if (!any) {
            return SqlType.TEXT;
        }
        for (SqlType t : List.of(SqlType.BOOLEAN, SqlType.BIGINT, SqlType.NUMERIC, SqlType.DATE, SqlType.TIMESTAMP)) {
            if (possible.contains(t)) {
                return t;
            }
        }
        return SqlType.TEXT;
    }

    /** "007" or "01234" are codes (zip, ids) and must keep their leading zeros, so they stay text. */
    private static boolean hasLeadingZero(String v) {
        String digits = v.startsWith("-") || v.startsWith("+") ? v.substring(1) : v;
        return digits.length() > 1 && digits.charAt(0) == '0' && digits.charAt(1) != '.';
    }

    public static Object convert(String raw, SqlType type) {
        if (isNull(raw)) {
            return null;
        }
        String v = raw.trim();
        return switch (type) {
            case BOOLEAN -> TRUE.contains(v.toLowerCase(Locale.ROOT));
            case BIGINT -> Long.parseLong(v.startsWith("+") ? v.substring(1) : v);
            case NUMERIC -> new BigDecimal(v.startsWith("+") ? v.substring(1) : v);
            case DATE -> parseDate(v);
            case TIMESTAMP -> {
                LocalDate d = parseDate(v);
                yield d != null ? d.atStartOfDay() : parseTimestamp(v);
            }
            case TEXT -> raw;
        };
    }

    static LocalDate parseDate(String v) {
        if (!DATE.matcher(v).matches()) {
            return null;
        }
        try {
            return LocalDate.parse(v, v.indexOf('/') > 0 ? SLASH_DATE : DASH_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static LocalDateTime parseTimestamp(String v) {
        if (!TIMESTAMP.matcher(v).matches()) {
            return null;
        }
        String iso = v.replace(' ', 'T');
        try {
            if (iso.endsWith("Z") || iso.matches(".*[+-]\\d{2}:?\\d{2}$")) {
                String normalized = iso.matches(".*[+-]\\d{4}$") ? iso.substring(0, iso.length() - 2) + ":" + iso.substring(iso.length() - 2) : iso;
                return OffsetDateTime.parse(normalized).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
            }
            return LocalDateTime.parse(padTime(iso));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** LocalDateTime.parse needs zero-padded fields: 2025-1-5T9:05 → 2025-01-05T09:05. */
    private static String padTime(String iso) {
        String[] dt = iso.split("T", 2);
        String[] d = dt[0].split("-");
        String date = String.format("%s-%02d-%02d", d[0], Integer.parseInt(d[1]), Integer.parseInt(d[2]));
        String time = dt[1];
        int colon = time.indexOf(':');
        if (colon == 1) {
            time = "0" + time;
        }
        return date + "T" + time;
    }
}
