package com.queryai.upload;

import com.queryai.upload.ColumnTypeInference.SqlType;
import com.queryai.upload.CsvParser.CsvData;
import com.queryai.upload.CsvParser.CsvException;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvUploadParsingTest {

    private static CsvData parse(String csv) throws Exception {
        return CsvParser.parse(new StringReader(csv), null, 1000, 100);
    }

    @Test
    void parsesQuotedFieldsBomAndLineEndings() throws Exception {
        CsvData d = parse("﻿Name,Amount,\"Note, with comma\",Joined\r\nAlice,10.5,\"say \"\"hi\"\"\nline2\",2025-01-05\nBob,7,,2025/2/3\n\n");
        assertThat(d.header()).containsExactly("Name", "Amount", "Note, with comma", "Joined");
        assertThat(d.rows()).hasSize(2);
        assertThat(d.rows().get(0)[2]).isEqualTo("say \"hi\"\nline2");
        assertThat(d.delimiter()).isEqualTo(',');
    }

    @Test
    void detectsSemicolonDelimiter() throws Exception {
        assertThat(parse("a;b\n1;2\n").delimiter()).isEqualTo(';');
    }

    @Test
    void rejectsRaggedRowsAndOversizedFiles() {
        assertThatThrownBy(() -> parse("a,b\n1,2,3\n")).isInstanceOf(CsvException.class).hasMessageContaining("Row 2");
        assertThatThrownBy(() -> CsvParser.parse(new StringReader("a\n1\n2\n3\n"), null, 2, 10))
                .isInstanceOf(CsvException.class).hasMessageContaining("limit");
        assertThatThrownBy(() -> parse("a,b\n\"open,1\n")).isInstanceOf(CsvException.class);
    }

    @Test
    void infersTheMostSpecificType() {
        assertThat(ColumnTypeInference.infer(rows("1", "-20", "NA"), 0)).isEqualTo(SqlType.BIGINT);
        assertThat(ColumnTypeInference.infer(rows("1", "2.5"), 0)).isEqualTo(SqlType.NUMERIC);
        assertThat(ColumnTypeInference.infer(rows("01234", "98765"), 0)).isEqualTo(SqlType.TEXT); // zip codes keep zeros
        assertThat(ColumnTypeInference.infer(rows("Yes", "no"), 0)).isEqualTo(SqlType.BOOLEAN);
        assertThat(ColumnTypeInference.infer(rows("2025-01-05", "2025/2/3"), 0)).isEqualTo(SqlType.DATE);
        assertThat(ColumnTypeInference.infer(rows("2025-01-05 10:00", "2025-01-06T11:30:00Z", "2025-01-07"), 0)).isEqualTo(SqlType.TIMESTAMP);
        assertThat(ColumnTypeInference.infer(rows("", "null"), 0)).isEqualTo(SqlType.TEXT);
        assertThat(ColumnTypeInference.infer(rows("12", "twelve"), 0)).isEqualTo(SqlType.TEXT);
    }

    @Test
    void convertsTimestampsWithOffsetsToUtc() {
        assertThat(ColumnTypeInference.convert("2025-01-06T11:30:00+0530", SqlType.TIMESTAMP))
                .isEqualTo(LocalDateTime.of(2025, 1, 6, 6, 0));
        assertThat(ColumnTypeInference.convert("N/A", SqlType.BIGINT)).isNull();
    }

    @Test
    void makesSafeUniqueIdentifiers() {
        assertThat(Identifiers.columns(List.of("Order Date", "order_date", "Select", "2024 Sales", "", "Customer Name (Full)")))
                .containsExactly("order_date", "order_date_2", "select_value", "c_2024_sales", "column_5", "customer_name_full");
        assertThat(Identifiers.tableFromFileName("reports/My Sales Q1.csv")).isEqualTo("my_sales_q1");
        assertThat(Identifiers.isSafe("x\"; drop table users; --")).isFalse();
    }

    @Test
    void splitsBundledDemoScriptIntoStatements() {
        List<String> s = WarehouseBootstrap.statements("-- header\nBEGIN;\nINSERT INTO a VALUES (1);\nINSERT INTO a VALUES (2);\nCOMMIT;\nANALYZE;\n");
        assertThat(s).containsExactly("INSERT INTO a VALUES (1)", "INSERT INTO a VALUES (2)");
    }

    private static List<String[]> rows(String... values) {
        return java.util.Arrays.stream(values).map(v -> new String[]{v}).toList();
    }
}
