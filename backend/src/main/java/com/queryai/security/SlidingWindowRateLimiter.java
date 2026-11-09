package com.queryai.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small in-memory sliding-window rate limiter. Suitable for a single backend instance; swap for a Redis-backed
 * implementation when running multiple replicas.
 */
public class SlidingWindowRateLimiter {
    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();
    private final long windowMillis;

    public SlidingWindowRateLimiter(long windowMillis) {
        this.windowMillis = windowMillis;
    }

    /** @return 0 if the request is allowed, otherwise the number of milliseconds until a slot frees up. */
    public long tryAcquire(String key, int limit, long nowMillis) {
        if (limit <= 0) {
            return 0;
        }
        if (windows.size() > 50_000) {
            evictIdle(nowMillis);
        }
        Deque<Long> deque = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && nowMillis - deque.peekFirst() >= windowMillis) {
                deque.pollFirst();
            }
            if (deque.size() >= limit) {
                return Math.max(1, windowMillis - (nowMillis - deque.peekFirst()));
            }
            deque.addLast(nowMillis);
            return 0;
        }
    }

    private void evictIdle(long nowMillis) {
        windows.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                Long last = e.getValue().peekLast();
                return last == null || nowMillis - last >= windowMillis;
            }
        });
    }
}
