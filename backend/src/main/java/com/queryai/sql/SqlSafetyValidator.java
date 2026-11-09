package com.queryai.sql;

import com.queryai.datasource.DatabaseType;
import com.queryai.sql.SqlLexer.Token;
import com.queryai.sql.SqlLexer.Type;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Static, dependency-free safety analysis of a SQL statement before it ever reaches a database.
 * <p>
 * Guarantees enforced here (defence in depth – execution additionally happens in a read-only transaction with a
 * timeout and row cap, ideally with a read-only database user):
 * <ul>
 *   <li>exactly one statement, which must be a {@code SELECT} or {@code WITH ... SELECT}</li>
 *   <li>no data-modifying, DDL, transaction-control, locking or file/OS access keywords</li>
 *   <li>no dangerous functions (sleep, file access, dblink, dynamic query execution, sequence mutation, …)</li>
 *   <li>no system catalog access; only tables of the authorised schema snapshot may be referenced</li>
 *   <li>no MySQL executable comments</li>
 * </ul>
 */
public class SqlSafetyValidator {

    public enum Severity { ERROR, WARNING, INFO }

    public record Issue(Severity severity, String code, String message) {
    }

    public record Report(boolean valid, List<Issue> issues, Set<String> referencedTables, List<String> parameters,
                         boolean hasLimit, String normalizedSql) {
        public boolean hasWarnings() {
            return issues.stream().anyMatch(i -> i.severity() == Severity.WARNING);
        }
    }

    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "insert", "update", "delete", "merge", "upsert", "drop", "alter", "create", "truncate", "grant", "revoke",
            "copy", "call", "execute", "exec", "lock", "vacuum", "reindex", "cluster", "listen", "notify", "unlisten",
            "prepare", "deallocate", "handler", "outfile", "dumpfile", "into", "shutdown", "kill", "rename", "attach",
            "detach", "import", "install", "uninstall", "checkpoint", "discard", "refresh", "security", "begin", "commit",
            "rollback", "savepoint", "release");

    private static final Set<String> FORBIDDEN_FUNCTIONS = Set.of(
            // PostgreSQL
            "pg_sleep", "pg_sleep_for", "pg_sleep_until", "pg_read_file", "pg_read_binary_file", "pg_ls_dir",
            "pg_stat_file", "pg_terminate_backend", "pg_cancel_backend", "pg_reload_conf", "pg_rotate_logfile",
            "pg_logdir_ls", "pg_ls_logdir", "pg_ls_waldir", "lo_import", "lo_export", "lo_get", "lo_put", "lo_from_bytea",
            "dblink", "dblink_exec", "dblink_connect", "dblink_send_query", "set_config", "nextval", "setval",
            "pg_advisory_lock", "pg_advisory_xact_lock", "pg_try_advisory_lock", "query_to_xml", "query_to_xml_and_xmlschema",
            "query_to_xmlschema", "cursor_to_xml", "table_to_xml", "database_to_xml", "schema_to_xml", "pg_switch_wal",
            "pg_create_restore_point", "current_setting", "inet_server_addr", "pg_backend_pid", "version",
            // MySQL
            "sleep", "benchmark", "load_file", "get_lock", "release_lock", "release_all_locks", "master_pos_wait",
            "source_pos_wait", "sys_exec", "sys_eval", "user", "current_user", "session_user", "system_user");

    private static final Set<String> SYSTEM_SCHEMAS = Set.of(
            "pg_catalog", "information_schema", "pg_toast", "mysql", "performance_schema", "sys");

    private static final Set<String> CLAUSE_END = Set.of(
            "where", "group", "order", "having", "limit", "offset", "union", "intersect", "except", "window", "fetch",
            "for", "on", "using", "join", "inner", "left", "right", "full", "cross", "natural", "lateral", "qualify",
            "returning");

    /**
     * @param allowedTables lower-cased table names the caller may query, or {@code null} to skip authorisation
     * @param allowedSchema the schema queries are scoped to (qualifiers must match it), or {@code null}
     */
    public Report validate(String sql, DatabaseType dialect, Set<String> allowedTables, String allowedSchema) {
        List<Issue> issues = new ArrayList<>();
        Set<String> tables = new LinkedHashSet<>();
        List<String> params = new ArrayList<>();
        if (sql == null || sql.isBlank()) {
            issues.add(new Issue(Severity.ERROR, "EMPTY", "SQL is empty"));
            return new Report(false, issues, tables, params, false, "");
        }
        if (sql.length() > 100_000) {
            issues.add(new Issue(Severity.ERROR, "TOO_LONG", "SQL exceeds 100,000 characters"));
            return new Report(false, issues, tables, params, false, sql);
        }

        SqlLexer.Result lexed = SqlLexer.tokenize(sql, dialect);
        if (lexed.error() != null) {
            issues.add(new Issue(Severity.ERROR, "SYNTAX", lexed.error()));
            return new Report(false, issues, tables, params, false, sql);
        }
        if (lexed.hasExecutableComment()) {
            issues.add(new Issue(Severity.ERROR, "EXECUTABLE_COMMENT", "MySQL executable comments (/*! */) and hints are not allowed"));
        }
        List<Token> tokens = new ArrayList<>(lexed.tokens());
        // strip trailing semicolons
        while (!tokens.isEmpty() && tokens.get(tokens.size() - 1).type() == Type.SEMICOLON) {
            tokens.remove(tokens.size() - 1);
        }
        String normalized = tokens.isEmpty() ? "" : sql.substring(0, endOf(sql, tokens.get(tokens.size() - 1))).trim();
        if (tokens.isEmpty()) {
            issues.add(new Issue(Severity.ERROR, "EMPTY", "SQL contains no statement"));
            return new Report(false, issues, tables, params, false, normalized);
        }
        if (tokens.stream().anyMatch(t -> t.type() == Type.SEMICOLON)) {
            issues.add(new Issue(Severity.ERROR, "MULTIPLE_STATEMENTS", "Only a single SQL statement may be executed"));
        }

        // statement type
        Token first = tokens.stream().filter(t -> t.type() != Type.LPAREN).findFirst().orElse(tokens.get(0));
        if (!(first.isWord("select") || first.isWord("with"))) {
            issues.add(new Issue(Severity.ERROR, "NOT_READ_ONLY", "Only SELECT queries are allowed (statement starts with '" + first.text() + "')"));
        }

        // keyword / function / catalog checks
        Set<String> reportedKeywords = new LinkedHashSet<>();
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            Token next = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
            Token prev = i > 0 ? tokens.get(i - 1) : null;
            if (t.type() == Type.PARAM) {
                String name = t.text().startsWith(":") ? t.text().substring(1) : t.text();
                if (t.text().startsWith("$")) {
                    issues.add(new Issue(Severity.ERROR, "POSITIONAL_PARAMETER", "Use named parameters (:name) instead of " + t.text()));
                } else if (!params.contains(name)) {
                    params.add(name);
                }
                continue;
            }
            if (!t.isIdentifier()) {
                continue;
            }
            String word = t.normalized();
            boolean qualifiedMember = prev != null && prev.type() == Type.DOT; // e.g. o.update – a column, not a keyword
            if (t.type() == Type.WORD && !qualifiedMember && FORBIDDEN_KEYWORDS.contains(word)
                    && !(word.equals("for") || isAllowedContext(word, prev, next))) {
                if (reportedKeywords.add(word)) {
                    issues.add(new Issue(Severity.ERROR, "FORBIDDEN_KEYWORD", "'" + word.toUpperCase(Locale.ROOT)
                            + "' is not allowed: QueryAI only runs read-only SELECT statements"));
                }
            }
            if (next != null && next.type() == Type.LPAREN && FORBIDDEN_FUNCTIONS.contains(word)) {
                issues.add(new Issue(Severity.ERROR, "FORBIDDEN_FUNCTION", "Function '" + word + "()' is not allowed"));
            }
            if (next != null && next.type() == Type.DOT && SYSTEM_SCHEMAS.contains(word)) {
                issues.add(new Issue(Severity.ERROR, "SYSTEM_CATALOG", "Access to system schema '" + word + "' is not allowed"));
            }
        }

        // table references and CTE names
        Set<String> cteNames = collectCteNames(tokens);
        TableScan scan = scanTables(tokens);
        boolean hasLimit = scan.topLevelLimit;
        for (QualifiedName ref : scan.refs) {
            String table = ref.name();
            if (ref.schema() == null && cteNames.contains(table)) {
                continue;
            }
            if (table.startsWith("pg_") || (ref.schema() != null && SYSTEM_SCHEMAS.contains(ref.schema()))) {
                issues.add(new Issue(Severity.ERROR, "SYSTEM_CATALOG", "Access to system table '" + ref.display() + "' is not allowed"));
                continue;
            }
            tables.add(table);
            if (ref.schema() != null && allowedSchema != null && !ref.schema().equalsIgnoreCase(allowedSchema)) {
                issues.add(new Issue(Severity.ERROR, "UNAUTHORIZED_SCHEMA", "Schema '" + ref.schema()
                        + "' is outside this connection's authorised schema '" + allowedSchema + "'"));
            } else if (allowedTables != null && !allowedTables.contains(table)) {
                issues.add(new Issue(Severity.ERROR, "UNKNOWN_TABLE", "Table '" + ref.display()
                        + "' does not exist in the schema or you are not authorised to query it"));
            }
        }
        if (allowedTables == null) {
            issues.add(new Issue(Severity.WARNING, "SCHEMA_UNKNOWN", "Schema metadata is not loaded; table authorisation could not be checked"));
        }
        if (scan.commaJoinWithoutWhere) {
            issues.add(new Issue(Severity.WARNING, "CROSS_JOIN", "Comma-separated tables without a WHERE clause produce a Cartesian product"));
        }
        if (scan.selectStar) {
            issues.add(new Issue(Severity.INFO, "SELECT_STAR", "SELECT * returns every column; listing needed columns is faster and clearer"));
        }
        if (!hasLimit) {
            issues.add(new Issue(Severity.INFO, "NO_LIMIT", "No LIMIT clause: the result will be capped at the data source's row limit"));
        }
        boolean valid = issues.stream().noneMatch(i -> i.severity() == Severity.ERROR);
        return new Report(valid, issues, tables, params, hasLimit, normalized);
    }

    /** Words in the forbidden list that are legitimate in specific read-only contexts. */
    private static boolean isAllowedContext(String word, Token prev, Token next) {
        // "release" / "security" etc. are only dangerous as statements, which the statement-type check already blocks;
        // keep them forbidden anyway except for the common column-ish words used as aliases after AS.
        return prev != null && prev.isWord("as") && !word.equals("into");
    }

    private static int endOf(String sql, Token t) {
        return Math.min(sql.length(), t.position() + t.text().length());
    }

    private Set<String> collectCteNames(List<Token> tokens) {
        Set<String> names = new LinkedHashSet<>();
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (!t.isIdentifier()) {
                continue;
            }
            // name AS (   |   name (cols) AS (   |   name AS [NOT] MATERIALIZED (
            int j = i + 1;
            if (j < tokens.size() && tokens.get(j).type() == Type.LPAREN) {
                int depth = 0;
                for (; j < tokens.size(); j++) {
                    if (tokens.get(j).type() == Type.LPAREN) {
                        depth++;
                    } else if (tokens.get(j).type() == Type.RPAREN && --depth == 0) {
                        j++;
                        break;
                    }
                }
            }
            if (j < tokens.size() && tokens.get(j).isWord("as")) {
                int k = j + 1;
                if (k < tokens.size() && tokens.get(k).isWord("not")) {
                    k++;
                }
                if (k < tokens.size() && tokens.get(k).isWord("materialized")) {
                    k++;
                }
                Token prev = i > 0 ? tokens.get(i - 1) : null;
                boolean afterWithOrComma = prev != null && (prev.isWord("with") || prev.isWord("recursive") || prev.type() == Type.COMMA);
                if (afterWithOrComma && k < tokens.size() && tokens.get(k).type() == Type.LPAREN) {
                    names.add(t.normalized());
                }
            }
        }
        return names;
    }

    private record QualifiedName(String schema, String name) {
        String display() {
            return schema == null ? name : schema + "." + name;
        }
    }

    private static final class TableScan {
        final List<QualifiedName> refs = new ArrayList<>();
        boolean topLevelLimit;
        boolean commaJoinWithoutWhere;
        boolean selectStar;
    }

    private enum Paren { SUBQUERY, FUNCTION, GROUP }

    private TableScan scanTables(List<Token> tokens) {
        TableScan scan = new TableScan();
        Deque<Paren> stack = new ArrayDeque<>();
        // per-depth bookkeeping for cartesian detection
        Deque<int[]> fromState = new ArrayDeque<>(); // [commaTables, sawWhere]
        fromState.push(new int[]{0, 0});
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            Token prev = i > 0 ? tokens.get(i - 1) : null;
            if (t.type() == Type.LPAREN) {
                Token next = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
                Paren kind = next != null && (next.isWord("select") || next.isWord("with")) ? Paren.SUBQUERY
                        : prev != null && prev.isIdentifier() && !isKeywordBeforeParen(prev) ? Paren.FUNCTION : Paren.GROUP;
                stack.push(kind);
                if (kind == Paren.SUBQUERY) {
                    fromState.push(new int[]{0, 0});
                }
                continue;
            }
            if (t.type() == Type.RPAREN) {
                Paren closed = stack.isEmpty() ? Paren.GROUP : stack.pop();
                if (closed == Paren.SUBQUERY && fromState.size() > 1) {
                    int[] st = fromState.pop();
                    if (st[0] > 1 && st[1] == 0) {
                        scan.commaJoinWithoutWhere = true;
                    }
                }
                continue;
            }
            boolean inFunction = !stack.isEmpty() && stack.peek() == Paren.FUNCTION;
            if (t.isWord("limit") && stack.isEmpty()) {
                scan.topLevelLimit = true;
            }
            if (t.isWord("fetch") && stack.isEmpty()) {
                scan.topLevelLimit = true;
            }
            if (t.isWord("where") && !inFunction) {
                fromState.peek()[1] = 1;
            }
            if (t.type() == Type.OPERATOR && t.text().equals("*") && prev != null
                    && (prev.isWord("select") || prev.isWord("distinct"))) {
                scan.selectStar = true;
            }
            if (inFunction) {
                continue; // EXTRACT(YEAR FROM x), SUBSTRING(a FROM 1 FOR 2), TRIM(BOTH ' ' FROM x)
            }
            if (t.isWord("from") && !(prev != null && prev.isWord("distinct"))) {
                int count = parseTableList(tokens, i + 1, scan, true);
                fromState.peek()[0] = Math.max(fromState.peek()[0], count);
            } else if (t.isWord("join")) {
                parseTableList(tokens, i + 1, scan, false);
            }
        }
        int[] top = fromState.peek();
        if (top[0] > 1 && top[1] == 0) {
            scan.commaJoinWithoutWhere = true;
        }
        return scan;
    }

    private static boolean isKeywordBeforeParen(Token t) {
        if (t.type() != Type.WORD) {
            return false;
        }
        return Set.of("in", "exists", "as", "from", "join", "on", "and", "or", "not", "where", "select", "values",
                "any", "all", "some", "using", "over", "filter", "within", "lateral", "with", "recursive", "materialized",
                "by", "having", "then", "else", "when", "case", "is").contains(t.normalized());
    }

    /** Parses {@code table [AS alias] [, table ...]} and returns the number of table items seen. */
    private int parseTableList(List<Token> tokens, int start, TableScan scan, boolean allowComma) {
        int i = start;
        int items = 0;
        while (i < tokens.size()) {
            Token t = tokens.get(i);
            if (t.isWord("lateral") || t.isWord("only")) {
                i++;
                continue;
            }
            if (t.type() == Type.LPAREN) {
                // derived table or parenthesised join: handled by the main scan
                items++;
                return items;
            }
            if (!t.isIdentifier() || (t.type() == Type.WORD && CLAUSE_END.contains(t.normalized()))) {
                return items;
            }
            // qualified identifier chain
            List<String> parts = new ArrayList<>();
            parts.add(t.normalized());
            int j = i + 1;
            while (j + 1 < tokens.size() && tokens.get(j).type() == Type.DOT && tokens.get(j + 1).isIdentifier()) {
                parts.add(tokens.get(j + 1).normalized());
                j += 2;
            }
            boolean tableFunction = j < tokens.size() && tokens.get(j).type() == Type.LPAREN;
            if (!tableFunction) {
                String name = parts.get(parts.size() - 1);
                String schema = parts.size() >= 2 ? parts.get(parts.size() - 2) : null;
                scan.refs.add(new QualifiedName(schema, name));
            }
            items++;
            // skip alias
            if (j < tokens.size() && tokens.get(j).isWord("as")) {
                j++;
            }
            if (j < tokens.size() && tokens.get(j).isIdentifier()
                    && !(tokens.get(j).type() == Type.WORD && CLAUSE_END.contains(tokens.get(j).normalized()))) {
                j++;
            }
            if (allowComma && j < tokens.size() && tokens.get(j).type() == Type.COMMA) {
                i = j + 1;
                continue;
            }
            return items;
        }
        return items;
    }
}
