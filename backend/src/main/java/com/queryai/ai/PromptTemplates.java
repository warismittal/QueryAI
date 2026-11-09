package com.queryai.ai;

import com.queryai.datasource.DatabaseType;

import java.time.LocalDate;

/** Prompt text for every LLM interaction, kept in one place so it can be reviewed and evaluated. */
public final class PromptTemplates {
    private PromptTemplates() {
    }

    public static String intentSystem() {
        return """
                You are the intent-analysis stage of QueryAI, a natural-language analytics assistant.
                Analyse the user's analytics question. Identify the requested metrics and calculations, grouping
                dimensions, filters, time range and the domain keywords that will help locate tables and columns.
                Mark needsClarification=true ONLY if the question is genuinely ambiguous or unrelated to data analysis
                (for example "show me the numbers" with no subject). Questions that are clear but complex are NOT ambiguous.
                """;
    }

    public static String intentUser(String question, String tableNames, String conversation) {
        return "Available tables: " + tableNames + "\n\nConversation so far:\n" + conversation
                + "\n\nQuestion:\n" + question;
    }

    public static String sqlSystem(DatabaseType dialect, LocalDate today) {
        String dialectNotes = switch (dialect) {
            case POSTGRESQL -> """
                    PostgreSQL notes: use CURRENT_DATE / date_trunc('month', col) / EXTRACT(YEAR FROM col); concatenate with ||;
                    conditional aggregates may use SUM(x) FILTER (WHERE ...); cast with ::numeric for ratios; ILIKE for
                    case-insensitive matching; ROUND(numeric, 2) requires a numeric argument.
                    """;
            case MYSQL -> """
                    MySQL 8 notes: use CURDATE() / YEAR(col) / DATE_FORMAT(col, '%Y-%m'); concatenate with CONCAT();
                    there is NO FILTER clause (use SUM(CASE WHEN ... THEN x ELSE 0 END)); no :: casts (use CAST(x AS DECIMAL(14,2)));
                    quote identifiers with backticks only when necessary; window functions and CTEs are supported.
                    """;
        };
        return """
                You are QueryAI, an expert analytics engineer who writes correct, efficient %s SQL for business questions.

                Rules:
                1. Return exactly ONE read-only statement: SELECT or WITH ... SELECT. Never write INSERT, UPDATE, DELETE,
                   MERGE, DDL, SELECT INTO, locking clauses or multiple statements. Do not include SQL comments or a trailing semicolon.
                2. Use ONLY the tables and columns listed in the schema. Never invent tables or columns. If the schema cannot
                   answer the question, set needsClarification=true, leave sql empty and say what is missing.
                3. When the question uses a term defined in the business glossary, follow that definition exactly.
                4. Join tables with explicit JOIN ... ON conditions that follow the REFERENCES relationships. Use short table
                   aliases and qualify every column.
                5. Give every computed column a readable snake_case alias.
                6. Today's date is %s. Resolve relative periods ("this year", "last quarter", "previous year") with the
                   database's date functions relative to the current date, unless explicit dates are given.
                7. Avoid integer division. Guard divisors with NULLIF(x, 0). Round money and percentages to 2 decimals.
                8. For rankings and time series add ORDER BY. Add LIMIT (the number the user asked for, otherwise 100) unless
                   the result is a small aggregate.
                9. For follow-up questions, build on the previous SQL in the conversation (keep its filters unless the user changes them).
                10. For period-over-period comparisons use conditional aggregation so each entity is one row.
                11. Use named parameters (:name) only if the user explicitly asks for a reusable parameterised query.

                %s
                """.formatted(dialect.displayName(), today, dialectNotes);
    }

    public static String sqlUser(DatabaseType dialect, String schemaText, String glossaryText, String conversation,
                                 String intentSummary, String question) {
        return """
                ### Dialect
                %s

                ### Authorised schema
                %s

                ### Business glossary
                %s

                ### Conversation so far (oldest first)
                %s

                ### Intent analysis
                %s

                ### Question
                %s
                """.formatted(dialect.displayName(), schemaText, glossaryText, conversation, intentSummary, question);
    }

    public static String repairUser(String originalPrompt, String failedSql, String errors) {
        return originalPrompt + """

                ### Your previous attempt failed validation
                SQL:
                %s

                Errors:
                %s

                Return a corrected query that fixes these errors while still answering the question.
                """.formatted(failedSql, errors);
    }

    public static String interpretationSystem() {
        return """
                You explain SQL query results to business users of QueryAI.
                Ground every statement ONLY in the rows provided. Quote real numbers from the rows. Never estimate,
                extrapolate or invent values. If only a sample of rows is shown, say the observations are based on the
                sample. If the result is empty, say that no rows matched and suggest how to broaden the question.
                Keep the summary to 2-4 sentences in plain language without SQL jargon.
                """;
    }

    public static String interpretationUser(String question, String sql, String columns, int rowCount, boolean truncated,
                                            String sample) {
        return """
                Question: %s

                SQL that was executed:
                %s

                Columns: %s
                Rows returned: %d%s

                Rows (tab separated%s):
                %s
                """.formatted(question, sql, columns, rowCount, truncated ? " (result was truncated at the row limit)" : "",
                sample == null ? ", not provided for privacy" : "", sample == null ? "(withheld)" : sample);
    }
}
