package com.queryai.ai;

import com.queryai.ai.ChartRecommender.ChartType;
import com.queryai.ai.ChartRecommender.Column;
import com.queryai.ai.ChartRecommender.Kind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChartRecommenderTest {

    @Test
    void timeSeriesBecomesLineChart() {
        var r = ChartRecommender.recommend(List.of(new Column("month", Kind.DATE), new Column("revenue", Kind.NUMBER)),
                List.of(List.of("2026-01-01", 10.5), List.of("2026-02-01", 12.0)));
        assertThat(r.type()).isEqualTo(ChartType.LINE);
        assertThat(r.xColumn()).isEqualTo("month");
        assertThat(r.yColumns()).containsExactly("revenue");
    }

    @Test
    void fewCategoriesBecomePieAndManyBecomeBar() {
        var cols = List.of(new Column("channel", Kind.TEXT), new Column("orders", Kind.NUMBER));
        var pie = ChartRecommender.recommend(cols, List.of(List.of("web", 10), List.of("mobile_app", 7), List.of("marketplace", 2)));
        assertThat(pie.type()).isEqualTo(ChartType.PIE);
        var rows = java.util.stream.IntStream.range(0, 20).mapToObj(i -> List.<Object>of("c" + i, i)).toList();
        assertThat(ChartRecommender.recommend(cols, rows).type()).isEqualTo(ChartType.BAR);
    }

    @Test
    void singleNumericRowIsMetricAndIdsAreNotMeasures() {
        assertThat(ChartRecommender.recommend(List.of(new Column("aov", Kind.NUMBER)), List.of(List.of(123.4))).type())
                .isEqualTo(ChartType.METRIC);
        var rows = java.util.stream.IntStream.range(1, 11).mapToObj(i -> List.<Object>of(i, i * 5.0)).toList();
        var r = ChartRecommender.recommend(List.of(new Column("customer_id", Kind.NUMBER), new Column("spend", Kind.NUMBER)), rows);
        assertThat(r.type()).isEqualTo(ChartType.BAR);
        assertThat(r.xColumn()).isEqualTo("customer_id");
    }

    @Test
    void twoMeasuresWithoutDimensionsBecomeScatterAndEmptyIsTable() {
        var rows = java.util.stream.IntStream.range(0, 10).mapToObj(i -> List.<Object>of(i * 1.5, i * 2.0)).toList();
        assertThat(ChartRecommender.recommend(List.of(new Column("price", Kind.NUMBER), new Column("rating", Kind.NUMBER)), rows).type())
                .isEqualTo(ChartType.SCATTER);
        assertThat(ChartRecommender.recommend(List.of(new Column("x", Kind.TEXT)), List.of()).type()).isEqualTo(ChartType.TABLE);
    }

    @Test
    void yearColumnIsTreatedAsTime() {
        var r = ChartRecommender.recommend(List.of(new Column("year", Kind.NUMBER), new Column("revenue", Kind.NUMBER)),
                List.of(List.of(2024, 1.0), List.of(2025, 2.0)));
        assertThat(r.type()).isEqualTo(ChartType.LINE);
    }
}
