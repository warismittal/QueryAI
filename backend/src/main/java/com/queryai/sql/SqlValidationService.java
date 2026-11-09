package com.queryai.sql;

import com.queryai.common.ApiException;
import com.queryai.config.QueryAiProperties;
import com.queryai.datasource.DataSourceEntity;
import com.queryai.datasource.DatabaseType;
import com.queryai.history.ValidationStatus;
import com.queryai.schema.SchemaService;
import com.queryai.sql.SqlDtos.SqlValidationResult;
import com.queryai.sql.SqlSafetyValidator.Issue;
import com.queryai.sql.SqlSafetyValidator.Report;
import com.queryai.sql.SqlSafetyValidator.Severity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Two-stage validation: (1) static safety and authorisation analysis, (2) a database dry run via EXPLAIN which
 * checks syntax, column names and types without executing the query, and yields a cost estimate for resource limits.
 */
@Service
public class SqlValidationService {
    private static final Pattern PG_COST = Pattern.compile("\"Total Cost\"\\s*:\\s*([0-9.eE+]+)");
    private static final Pattern PG_ROWS = Pattern.compile("\"Plan Rows\"\\s*:\\s*([0-9.eE+]+)");
    private static final Pattern MYSQL_COST = Pattern.compile("\"query_cost\"\\s*:\\s*\"?([0-9.eE+]+)");

    private final SqlSafetyValidator validator = new SqlSafetyValidator();
    private final SchemaService schemaService;
    private final SqlExecutionService executor;
    private final QueryAiProperties.Sql limits;

    public SqlValidationService(SchemaService schemaService, SqlExecutionService executor, QueryAiProperties properties) {
        this.schemaService = schemaService;
        this.executor = executor;
        this.limits = properties.sql();
    }

    public SqlValidationResult validate(DataSourceEntity ds, String sql, Map<String, Object> params) {
        Report report = validator.validate(sql, ds.getDbType(), schemaService.allowedTables(ds), ds.effectiveSchema());
        List<Issue> issues = new ArrayList<>(report.issues());
        Double cost = null;
        Long rows = null;
        boolean dbChecked = false;

        if (report.valid()) {
            List<String> missing = report.parameters().stream()
                    .filter(p -> params == null || !params.containsKey(p) || params.get(p) == null).toList();
            if (!missing.isEmpty()) {
                issues.add(new Issue(Severity.WARNING, "PARAMETERS_MISSING",
                        "Provide values for parameters " + missing + " to run the database check"));
            } else {
                try {
                    String plan = explain(ds, report.normalizedSql(), params);
                    dbChecked = true;
                    cost = extract(ds.getDbType() == DatabaseType.MYSQL ? MYSQL_COST : PG_COST, plan);
                    Double r = ds.getDbType() == DatabaseType.POSTGRESQL ? extract(PG_ROWS, plan) : null;
                    rows = r == null ? null : r.longValue();
                    if (cost != null && cost > limits.maxCost()) {
                        issues.add(new Issue(Severity.ERROR, "RESOURCE_LIMIT", String.format(
                                "Estimated query cost %.0f exceeds the limit of %.0f. Add filters or aggregate further.", cost, limits.maxCost())));
                    } else if (cost != null && cost > limits.warnCost()) {
                        issues.add(new Issue(Severity.WARNING, "EXPENSIVE_QUERY", String.format(
                                "Estimated query cost %.0f is high; the query may be slow.", cost)));
                    }
                } catch (ApiException e) {
                    issues.add(new Issue(Severity.ERROR, "DATABASE_REJECTED", "Database check failed: " + e.getMessage()));
                }
            }
        }
        ValidationStatus status = issues.stream().anyMatch(i -> i.severity() == Severity.ERROR) ? ValidationStatus.INVALID
                : issues.stream().anyMatch(i -> i.severity() == Severity.WARNING) ? ValidationStatus.WARNING : ValidationStatus.VALID;
        return new SqlValidationResult(status, issues, report.referencedTables(), report.parameters(), report.hasLimit(),
                report.normalizedSql(), dbChecked, cost, rows);
    }

    /** Static checks only (no database round trip); used when saving queries. */
    public Report validateStatic(DataSourceEntity ds, String sql) {
        return validator.validate(sql, ds.getDbType(), schemaService.allowedTables(ds), ds.effectiveSchema());
    }

    private String explain(DataSourceEntity ds, String sql, Map<String, Object> params) {
        String explainSql = ds.getDbType() == DatabaseType.MYSQL ? "EXPLAIN FORMAT=JSON " + sql : "EXPLAIN (FORMAT JSON) " + sql;
        return executor.inReadOnlyTransaction(ds, Math.min(ds.getQueryTimeoutSec(), 15), template -> {
            List<String> lines = template.query(explainSql, new MapSqlParameterSource(ParameterCoercion.coerce(params)),
                    (rs, i) -> rs.getString(1));
            return String.join("\n", lines);
        });
    }

    private static Double extract(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text == null ? "" : text);
        if (!m.find()) {
            return null;
        }
        try {
            return Double.parseDouble(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
