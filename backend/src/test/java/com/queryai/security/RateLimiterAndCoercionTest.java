package com.queryai.security;

import com.queryai.sql.ParameterCoercion;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterAndCoercionTest {

    @Test
    void slidingWindowAllowsUpToLimitThenRecovers() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(1000);
        assertThat(limiter.tryAcquire("k", 2, 0)).isZero();
        assertThat(limiter.tryAcquire("k", 2, 10)).isZero();
        assertThat(limiter.tryAcquire("k", 2, 20)).isPositive();
        assertThat(limiter.tryAcquire("other", 2, 20)).isZero();
        assertThat(limiter.tryAcquire("k", 2, 1001)).isZero();
    }

    @Test
    void coercesIsoDatesForJdbc() {
        Map<String, Object> out = ParameterCoercion.coerce(Map.of("d", "2026-01-31", "ts", "2026-01-31T10:15", "n", 5, "s", "web"));
        assertThat(out.get("d")).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(out.get("ts")).isEqualTo(LocalDateTime.of(2026, 1, 31, 10, 15));
        assertThat(out.get("n")).isEqualTo(5);
        assertThat(out.get("s")).isEqualTo("web");
    }
}
