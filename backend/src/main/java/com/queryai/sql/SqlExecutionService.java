package com.queryai.sql;

import com.queryai.common.ApiException;
import com.queryai.datasource.DataSourceEntity;
import com.queryai.datasource.DatabaseType;
import com.queryai.datasource.TargetConnectionRegistry;
import com.queryai.sql.QueryResult.Category;
import com.queryai.sql.QueryResult.ResultColumn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Array;
import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Executes already-validated SQL against a target database through {@link NamedParameterJdbcTemplate}.
 * Every execution runs in an enforced read-only transaction with a statement timeout and a hard row cap.
 */
@Service
public class SqlExecutionService {
    private static final int MAX_CELL_CHARS = 4000;
    private static final Logger log = LoggerFactory.getLogger(SqlExecutionService.class);

    private final TargetConnectionRegistry registry;

    public SqlExecutionService(TargetConnectionRegistry registry) {
        this.registry = registry;
    }

    public QueryResult execute(DataSourceEntity ds, String sql, Map<String, Object> params, int maxRows, int timeoutSeconds) {
        long started = System.currentTimeMillis();
        int cap = Math.max(1, maxRows);
        QueryResult partial = inReadOnlyTransaction(ds, timeoutSeconds, template -> {
            template.getJdbcTemplate().setMaxRows(cap + 1); // one extra row tells us whether the result was truncated
            return template.query(sql, new MapSqlParameterSource(ParameterCoercion.coerce(params)), extractor(cap));
        });
        return new QueryResult(partial.columns(), partial.rows(), partial.rowCount(), partial.truncated(),
                System.currentTimeMillis() - started, cap);
    }

    /**
     * Runs a function inside an explicit read-only transaction on one pooled connection, then always rolls back.
     * Used for EXPLAIN-based validation as well.
     * <p>
     * PostgreSQL: {@code SET TRANSACTION READ ONLY} as the first statement of the transaction.
     * MySQL: any implicit transaction left on the connection is rolled back first, then {@code START TRANSACTION READ ONLY}
     * opens a fresh one ({@code SET TRANSACTION} is rejected by MySQL while a transaction is active, error 1568).
     */
    public <T> T inReadOnlyTransaction(DataSourceEntity ds, int timeoutSeconds, Function<NamedParameterJdbcTemplate, T> work) {
        DataSource dataSource = registry.dataSource(ds);
        try (Connection connection = dataSource.getConnection()) {
            if (connection.getAutoCommit()) {
                connection.setAutoCommit(false);
            }
            connection.rollback(); // end anything a previous borrower (e.g. metadata calls) left open
            if (!connection.isReadOnly()) {
                connection.setReadOnly(true);
            }
            try (Statement st = connection.createStatement()) {
                if (ds.getDbType() == DatabaseType.MYSQL) {
                    st.execute("START TRANSACTION READ ONLY");
                } else {
                    st.execute("SET TRANSACTION READ ONLY");
                }
            }
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            jdbc.setQueryTimeout(timeoutSeconds);
            jdbc.setFetchSize(500);
            try {
                return work.apply(new NamedParameterJdbcTemplate(jdbc));
            } finally {
                safeRollback(connection); // nothing a query does is ever committed
            }
        } catch (QueryTimeoutException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "QUERY_TIMEOUT",
                    "The query exceeded the " + timeoutSeconds + "s timeout and was cancelled", e);
        } catch (DataAccessException | SQLException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "QUERY_FAILED", databaseMessage(e), e);
        }
    }

    private static void safeRollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            log.warn("Rollback after read-only query failed: {}", e.getMessage());
        }
    }

    private ResultSetExtractor<QueryResult> extractor(int cap) {
        return rs -> {
            ResultSetMetaData md = rs.getMetaData();
            int n = md.getColumnCount();
            List<ResultColumn> columns = new ArrayList<>(n);
            for (int c = 1; c <= n; c++) {
                columns.add(new ResultColumn(md.getColumnLabel(c), md.getColumnTypeName(c), categorize(md.getColumnType(c))));
            }
            List<List<Object>> rows = new ArrayList<>();
            boolean truncated = false;
            while (rs.next()) {
                if (rows.size() >= cap) {
                    truncated = true;
                    break;
                }
                List<Object> row = new ArrayList<>(n);
                for (int c = 1; c <= n; c++) {
                    row.add(convert(rs.getObject(c)));
                }
                rows.add(row);
            }
            return new QueryResult(columns, rows, rows.size(), truncated, 0, cap);
        };
    }

    static Category categorize(int sqlType) {
        return switch (sqlType) {
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT, Types.REAL, Types.FLOAT, Types.DOUBLE,
                 Types.NUMERIC, Types.DECIMAL -> Category.NUMBER;
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR, Types.CLOB -> Category.TEXT;
            case Types.DATE, Types.TIME, Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE, Types.TIME_WITH_TIMEZONE -> Category.DATE;
            case Types.BOOLEAN, Types.BIT -> Category.BOOLEAN;
            default -> Category.OTHER;
        };
    }

    static Object convert(Object value) throws SQLException {
        if (value == null || value instanceof Number && !(value instanceof BigDecimal || value instanceof BigInteger)
                || value instanceof Boolean) {
            return value;
        }
        if (value instanceof BigDecimal bd) {
            return bd.stripTrailingZeros().scale() <= 0 && bd.abs().compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) < 0
                    ? bd.longValue() : bd.doubleValue();
        }
        if (value instanceof BigInteger bi) {
            return bi.bitLength() < 63 ? bi.longValue() : bi.toString();
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().toString();
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate().toString();
        }
        if (value instanceof java.sql.Time t) {
            return t.toLocalTime().toString();
        }
        if (value instanceof TemporalAccessor) {
            return value.toString();
        }
        if (value instanceof byte[] bytes) {
            return "<binary " + bytes.length + " bytes>";
        }
        if (value instanceof Array array) {
            Object arr = array.getArray();
            return arr instanceof Object[] objects ? java.util.Arrays.toString(objects) : String.valueOf(arr);
        }
        String text = value.toString();
        return text.length() > MAX_CELL_CHARS ? text.substring(0, MAX_CELL_CHARS) + "…" : text;
    }

    public static String databaseMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage() == null ? e.getMessage() : root.getMessage();
        return message == null ? "Query failed" : message.length() > 1000 ? message.substring(0, 1000) + "…" : message;
    }
}
