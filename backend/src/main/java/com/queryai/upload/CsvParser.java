package com.queryai.upload;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Streaming RFC 4180 CSV reader: quoted fields, escaped quotes ({@code ""}), delimiters and line breaks inside quotes,
 * CRLF/LF/CR line endings and a leading UTF-8 BOM. The delimiter (comma, semicolon, tab or pipe) is detected from the
 * header line when not given.
 */
public final class CsvParser {

    public record CsvData(List<String> header, List<String[]> rows, char delimiter) {
    }

    public static class CsvException extends RuntimeException {
        public CsvException(String message) {
            super(message);
        }
    }

    private CsvParser() {
    }

    public static CsvData parse(Reader input, Character delimiter, int maxRows, int maxColumns) throws IOException {
        String text = readAll(input);
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            text = text.substring(1);
        }
        if (text.isBlank()) {
            throw new CsvException("The file is empty");
        }
        char d = delimiter != null ? delimiter : detectDelimiter(text);
        List<String[]> records = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldStarted = false;
        int line = 1;
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i += 2;
                        continue;
                    }
                    inQuotes = false;
                    i++;
                    continue;
                }
                if (c == '\n') {
                    line++;
                }
                field.append(c);
                i++;
                continue;
            }
            if (c == '"' && !fieldStarted) {
                inQuotes = true;
                fieldStarted = true;
                i++;
                continue;
            }
            if (c == d) {
                current.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
                i++;
                continue;
            }
            if (c == '\r' || c == '\n') {
                current.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
                addRecord(records, current, maxRows, maxColumns, line);
                current = new ArrayList<>();
                if (c == '\r' && i + 1 < n && text.charAt(i + 1) == '\n') {
                    i++;
                }
                line++;
                i++;
                continue;
            }
            field.append(c);
            fieldStarted = true;
            i++;
        }
        if (inQuotes) {
            throw new CsvException("Unclosed quote starting before line " + line);
        }
        if (fieldStarted || field.length() > 0 || !current.isEmpty()) {
            current.add(field.toString());
            addRecord(records, current, maxRows, maxColumns, line);
        }
        if (records.isEmpty()) {
            throw new CsvException("The file has no header row");
        }
        List<String> header = new ArrayList<>(List.of(records.get(0)));
        List<String[]> rows = new ArrayList<>(records.subList(1, records.size()));
        for (int r = 0; r < rows.size(); r++) {
            String[] row = rows.get(r);
            if (row.length > header.size()) {
                boolean extraEmpty = true;
                for (int k = header.size(); k < row.length; k++) {
                    if (!row[k].isBlank()) {
                        extraEmpty = false;
                        break;
                    }
                }
                if (!extraEmpty) {
                    throw new CsvException("Row " + (r + 2) + " has " + row.length + " values but the header has " + header.size() + " columns");
                }
            }
            if (row.length != header.size()) {
                String[] fixed = new String[header.size()];
                System.arraycopy(row, 0, fixed, 0, Math.min(row.length, header.size()));
                for (int k = row.length; k < fixed.length; k++) {
                    fixed[k] = "";
                }
                rows.set(r, fixed);
            }
        }
        return new CsvData(header, rows, d);
    }

    private static void addRecord(List<String[]> records, List<String> fields, int maxRows, int maxColumns, int line) {
        if (fields.size() == 1 && fields.get(0).isEmpty()) {
            return; // blank line
        }
        if (fields.size() > maxColumns) {
            throw new CsvException("Line " + line + " has " + fields.size() + " columns; the limit is " + maxColumns);
        }
        if (records.size() > maxRows) { // header + maxRows data rows
            throw new CsvException("The file has more than " + maxRows + " rows, which is the upload limit");
        }
        records.add(fields.toArray(new String[0]));
    }

    static char detectDelimiter(String text) {
        int end = 0;
        boolean quoted = false;
        while (end < text.length()) {
            char c = text.charAt(end);
            if (c == '"') {
                quoted = !quoted;
            } else if (!quoted && (c == '\n' || c == '\r')) {
                break;
            }
            end++;
        }
        String first = text.substring(0, end);
        char best = ',';
        int bestCount = -1;
        for (char candidate : new char[]{',', ';', '\t', '|'}) {
            int count = 0;
            boolean q = false;
            for (int i = 0; i < first.length(); i++) {
                char c = first.charAt(i);
                if (c == '"') {
                    q = !q;
                } else if (!q && c == candidate) {
                    count++;
                }
            }
            if (count > bestCount) {
                best = candidate;
                bestCount = count;
            }
        }
        return best;
    }

    private static String readAll(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[8192];
        int read;
        while ((read = reader.read(buf)) != -1) {
            sb.append(buf, 0, read);
        }
        return sb.toString();
    }
}
