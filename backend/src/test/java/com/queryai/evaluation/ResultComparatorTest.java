package com.queryai.evaluation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResultComparatorTest {

    @Test
    void matchesIgnoringRowOrderColumnOrderAndExtraColumns() {
        List<List<Object>> expected = List.of(List.of("web", 10.0), List.of("mobile", 5));
        List<List<Object>> actual = List.of(List.of(5L, "mobile", 99), List.of(10, "WEB", 1));
        assertThat(ResultComparator.compare(expected, actual).match()).isTrue();
    }

    @Test
    void detectsDifferentValuesAndRowCounts() {
        assertThat(ResultComparator.compare(List.of(List.of(1)), List.of(List.of(2))).match()).isFalse();
        assertThat(ResultComparator.compare(List.of(List.of(1)), List.of(List.of(1), List.of(1))).match()).isFalse();
    }

    @Test
    void roundsNumbersToCents() {
        assertThat(ResultComparator.compare(List.of(List.of(10.004)), List.of(List.of("10.00"))).match()).isTrue();
    }
}
