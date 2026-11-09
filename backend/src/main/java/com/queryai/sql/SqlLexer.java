package com.queryai.sql;

import com.queryai.datasource.DatabaseType;

import java.util.ArrayList;
import java.util.List;

/**
 * Dialect-aware SQL tokenizer used by the safety validator. It understands string literals (including PostgreSQL
 * E-strings and dollar quoting, MySQL backslash escapes and double-quoted strings), quoted identifiers, comments,
 * named parameters and PostgreSQL {@code ::} casts, so keywords hidden inside literals or comments are never
 * mistaken for statements and statements hidden inside comments are detected.
 */
public final class SqlLexer {

    public enum Type { WORD, QUOTED_IDENT, STRING, NUMBER, PARAM, LPAREN, RPAREN, COMMA, DOT, SEMICOLON, OPERATOR }

    public record Token(Type type, String text, int position) {
        /** Lower-cased value for WORD tokens; unquoted, lower-cased content for quoted identifiers. */
        public String normalized() {
            return switch (type) {
                case WORD -> text.toLowerCase(java.util.Locale.ROOT);
                case QUOTED_IDENT -> unquote(text).toLowerCase(java.util.Locale.ROOT);
                default -> text;
            };
        }

        public boolean isWord(String word) {
            return type == Type.WORD && text.equalsIgnoreCase(word);
        }

        public boolean isIdentifier() {
            return type == Type.WORD || type == Type.QUOTED_IDENT;
        }

        private static String unquote(String t) {
            if (t.length() < 2) {
                return t;
            }
            char q = t.charAt(0);
            String inner = t.substring(1, t.length() - 1);
            return inner.replace(String.valueOf(q) + q, String.valueOf(q));
        }
    }

    public record Result(List<Token> tokens, String error, boolean hasExecutableComment) {
    }

    private SqlLexer() {
    }

    public static Result tokenize(String sql, DatabaseType dialect) {
        List<Token> tokens = new ArrayList<>();
        boolean mysql = dialect == DatabaseType.MYSQL;
        boolean executableComment = false;
        int n = sql.length();
        int i = 0;
        while (i < n) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            // -- line comment (MySQL requires whitespace after --, PostgreSQL does not; treat both as comments)
            if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                i = skipLine(sql, i);
                continue;
            }
            if (c == '#' && mysql) {
                i = skipLine(sql, i);
                continue;
            }
            if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                if (mysql && i + 2 < n && (sql.charAt(i + 2) == '!' || sql.charAt(i + 2) == '+')) {
                    executableComment = true; // MySQL executes /*! ... */ content and /*+ */ optimizer hints
                }
                int depth = 1;
                int j = i + 2;
                while (j < n && depth > 0) {
                    if (!mysql && sql.charAt(j) == '/' && j + 1 < n && sql.charAt(j + 1) == '*') {
                        depth++; // PostgreSQL block comments nest
                        j += 2;
                    } else if (sql.charAt(j) == '*' && j + 1 < n && sql.charAt(j + 1) == '/') {
                        depth--;
                        j += 2;
                    } else {
                        j++;
                    }
                }
                if (depth > 0) {
                    return new Result(tokens, "Unterminated block comment", executableComment);
                }
                i = j;
                continue;
            }
            // PostgreSQL E'...' strings with backslash escapes
            if (!mysql && (c == 'E' || c == 'e') && i + 1 < n && sql.charAt(i + 1) == '\'') {
                int end = scanQuoted(sql, i + 1, '\'', true);
                if (end < 0) {
                    return new Result(tokens, "Unterminated string literal", executableComment);
                }
                tokens.add(new Token(Type.STRING, sql.substring(i, end), i));
                i = end;
                continue;
            }
            if (c == '\'') {
                int end = scanQuoted(sql, i, '\'', mysql);
                if (end < 0) {
                    return new Result(tokens, "Unterminated string literal", executableComment);
                }
                tokens.add(new Token(Type.STRING, sql.substring(i, end), i));
                i = end;
                continue;
            }
            if (c == '"') {
                int end = scanQuoted(sql, i, '"', mysql);
                if (end < 0) {
                    return new Result(tokens, "Unterminated quoted identifier", executableComment);
                }
                // MySQL (without ANSI_QUOTES) treats double quotes as string delimiters
                tokens.add(new Token(mysql ? Type.STRING : Type.QUOTED_IDENT, sql.substring(i, end), i));
                i = end;
                continue;
            }
            if (c == '`' && mysql) {
                int end = scanQuoted(sql, i, '`', false);
                if (end < 0) {
                    return new Result(tokens, "Unterminated quoted identifier", executableComment);
                }
                tokens.add(new Token(Type.QUOTED_IDENT, sql.substring(i, end), i));
                i = end;
                continue;
            }
            // PostgreSQL dollar quoting: $$...$$ or $tag$...$tag$
            if (c == '$' && !mysql) {
                int tagEnd = i + 1;
                while (tagEnd < n && (Character.isLetterOrDigit(sql.charAt(tagEnd)) || sql.charAt(tagEnd) == '_')) {
                    tagEnd++;
                }
                if (tagEnd < n && sql.charAt(tagEnd) == '$' && (tagEnd == i + 1 || !Character.isDigit(sql.charAt(i + 1)))) {
                    String tag = sql.substring(i, tagEnd + 1);
                    int close = sql.indexOf(tag, tagEnd + 1);
                    if (close < 0) {
                        return new Result(tokens, "Unterminated dollar-quoted string", executableComment);
                    }
                    tokens.add(new Token(Type.STRING, sql.substring(i, close + tag.length()), i));
                    i = close + tag.length();
                    continue;
                }
                // positional parameter like $1
                int j = i + 1;
                while (j < n && Character.isDigit(sql.charAt(j))) {
                    j++;
                }
                if (j > i + 1) {
                    tokens.add(new Token(Type.PARAM, sql.substring(i, j), i));
                    i = j;
                    continue;
                }
            }
            if (Character.isLetter(c) || c == '_') {
                int j = i + 1;
                while (j < n && (Character.isLetterOrDigit(sql.charAt(j)) || sql.charAt(j) == '_' || sql.charAt(j) == '$')) {
                    j++;
                }
                tokens.add(new Token(Type.WORD, sql.substring(i, j), i));
                i = j;
                continue;
            }
            if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(sql.charAt(i + 1)))) {
                int j = i + 1;
                while (j < n && (Character.isLetterOrDigit(sql.charAt(j)) || sql.charAt(j) == '.'
                        || ((sql.charAt(j) == '+' || sql.charAt(j) == '-') && (sql.charAt(j - 1) == 'e' || sql.charAt(j - 1) == 'E')))) {
                    j++;
                }
                tokens.add(new Token(Type.NUMBER, sql.substring(i, j), i));
                i = j;
                continue;
            }
            if (c == ':') {
                if (i + 1 < n && sql.charAt(i + 1) == ':') {
                    tokens.add(new Token(Type.OPERATOR, "::", i));
                    i += 2;
                    continue;
                }
                if (i + 1 < n && (Character.isLetter(sql.charAt(i + 1)) || sql.charAt(i + 1) == '_')) {
                    int j = i + 2;
                    while (j < n && (Character.isLetterOrDigit(sql.charAt(j)) || sql.charAt(j) == '_')) {
                        j++;
                    }
                    tokens.add(new Token(Type.PARAM, sql.substring(i, j), i));
                    i = j;
                    continue;
                }
                tokens.add(new Token(Type.OPERATOR, ":", i));
                i++;
                continue;
            }
            switch (c) {
                case '(' -> tokens.add(new Token(Type.LPAREN, "(", i));
                case ')' -> tokens.add(new Token(Type.RPAREN, ")", i));
                case ',' -> tokens.add(new Token(Type.COMMA, ",", i));
                case '.' -> tokens.add(new Token(Type.DOT, ".", i));
                case ';' -> tokens.add(new Token(Type.SEMICOLON, ";", i));
                default -> tokens.add(new Token(Type.OPERATOR, String.valueOf(c), i));
            }
            i++;
        }
        return new Result(tokens, null, executableComment);
    }

    private static int skipLine(String sql, int i) {
        int nl = sql.indexOf('\n', i);
        return nl < 0 ? sql.length() : nl + 1;
    }

    /** Returns the index just after the closing quote, or -1 when unterminated. Doubled quotes are escapes. */
    private static int scanQuoted(String sql, int start, char quote, boolean backslashEscapes) {
        int n = sql.length();
        int j = start + 1;
        while (j < n) {
            char ch = sql.charAt(j);
            if (backslashEscapes && ch == '\\') {
                j += 2;
                continue;
            }
            if (ch == quote) {
                if (j + 1 < n && sql.charAt(j + 1) == quote) {
                    j += 2;
                    continue;
                }
                return j + 1;
            }
            j++;
        }
        return -1;
    }
}
