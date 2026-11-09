package com.queryai.history;

/** Aggregate execution statistics, populated by a JPQL constructor expression. */
public record QueryRunStats(Long executed, Long succeeded, Double avgDurationMs) {
}
