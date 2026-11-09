package com.queryai.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.queryai.common.ApiError;
import com.queryai.common.RequestUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/** Throttles credential endpoints per IP and AI endpoints per user. Registered inside the security chain. */
public class RateLimitFilter extends OncePerRequestFilter {
    private final SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(60_000);
    private final int authLimit;
    private final int aiLimit;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(int authLimit, int aiLimit, ObjectMapper objectMapper) {
        this.authLimit = authLimit;
        this.aiLimit = aiLimit;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if ("POST".equals(request.getMethod())) {
            long waitMs = 0;
            if (path.startsWith("/api/v1/auth/login") || path.startsWith("/api/v1/auth/register") || path.startsWith("/api/v1/auth/guest")) {
                waitMs = limiter.tryAcquire("auth:" + RequestUtils.clientIp(request), authLimit, System.currentTimeMillis());
            } else if (path.startsWith("/api/v1/analytics/analyze") || path.startsWith("/api/v1/analytics/generate")
                    || path.startsWith("/api/v1/evaluation/run")) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                String who = auth != null && auth.isAuthenticated() ? auth.getName() : RequestUtils.clientIp(request);
                waitMs = limiter.tryAcquire("ai:" + who, aiLimit, System.currentTimeMillis());
            }
            if (waitMs > 0) {
                long seconds = Math.max(1, waitMs / 1000);
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(seconds));
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                objectMapper.writeValue(response.getOutputStream(), new ApiError(Instant.now(), 429, "RATE_LIMITED",
                        "Too many requests. Please retry in " + seconds + " seconds.", path, null));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
