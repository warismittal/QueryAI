package com.queryai.sql;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Converts JSON parameter values into JDBC-friendly Java types (ISO dates become temporal values). */
public final class ParameterCoercion {
    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern DATE_TIME = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?$");

    private ParameterCoercion() {
    }

    public static Map<String, Object> coerce(Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        if (params == null) {
            return result;
        }
        params.forEach((k, v) -> result.put(k, coerceValue(v)));
        return result;
    }

    static Object coerceValue(Object v) {
        if (v instanceof String s) {
            String t = s.trim();
            if (DATE.matcher(t).matches()) {
                return LocalDate.parse(t);
            }
            if (DATE_TIME.matcher(t).matches()) {
                return LocalDateTime.parse(t);
            }
        }
        return v;
    }
}
