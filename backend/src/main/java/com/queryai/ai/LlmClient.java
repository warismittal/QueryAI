package com.queryai.ai;

/**
 * Boundary between QueryAI and any large language model. The production implementation uses Spring AI; tests use
 * mocks. Implementations must throw {@link com.queryai.common.ApiException} with status 503 when not configured and
 * 502 when the provider call fails, and must never invent a response.
 */
public interface LlmClient {

    boolean isConfigured();

    /** Human readable provider/model description, e.g. {@code openai · gpt-4o-mini}. */
    String description();

    /** Calls the model and maps its JSON answer onto {@code type} (Spring AI structured output). */
    <T> LlmResponse<T> structured(String systemPrompt, String userPrompt, Class<T> type);

    LlmResponse<String> text(String systemPrompt, String userPrompt);

    record LlmResponse<T>(T value, String model, long latencyMs) {
    }
}
