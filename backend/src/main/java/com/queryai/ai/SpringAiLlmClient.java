package com.queryai.ai;

import com.queryai.common.ApiException;
import com.queryai.config.QueryAiProperties;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link LlmClient} backed by the Spring AI {@link ChatModel} selected with {@code spring.ai.model.chat}
 * (OpenAI or any OpenAI-compatible endpoint, or Anthropic). Structured output uses Spring AI's
 * {@link BeanOutputConverter} (JSON-schema format instructions + Jackson mapping) followed by Bean Validation.
 */
@Component
public class SpringAiLlmClient implements LlmClient {
    private static final Logger log = LoggerFactory.getLogger(SpringAiLlmClient.class);
    private static final String UNSET = "not-configured";

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final Environment env;
    private final Validator validator;
    private final String provider;

    public SpringAiLlmClient(ObjectProvider<ChatModel> chatModelProvider, Environment env, Validator validator,
                             QueryAiProperties properties) {
        this.chatModelProvider = chatModelProvider;
        this.env = env;
        this.validator = validator;
        this.provider = properties.ai().provider() == null ? "openai" : properties.ai().provider().toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean isConfigured() {
        String key = env.getProperty("spring.ai." + provider + ".api-key", UNSET);
        return chatModelProvider.getIfAvailable() != null && !key.isBlank() && !UNSET.equals(key);
    }

    @Override
    public String description() {
        String model = env.getProperty("spring.ai." + provider + ".chat.options.model", "default model");
        return provider + " · " + model;
    }

    @Override
    public <T> LlmResponse<T> structured(String systemPrompt, String userPrompt, Class<T> type) {
        BeanOutputConverter<T> converter = new BeanOutputConverter<>(type);
        LlmResponse<String> raw = call(systemPrompt, userPrompt + "\n\n" + converter.getFormat());
        T value;
        try {
            value = converter.convert(raw.value());
        } catch (RuntimeException e) {
            log.warn("LLM returned output that is not valid JSON for {}: {}", type.getSimpleName(), e.getMessage());
            throw ApiException.badGateway("LLM_INVALID_OUTPUT", "The AI model returned a malformed response. Please try again.", e);
        }
        if (value == null) {
            throw ApiException.badGateway("LLM_INVALID_OUTPUT", "The AI model returned an empty response.", null);
        }
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            String detail = violations.stream().map(v -> v.getPropertyPath() + " " + v.getMessage()).collect(Collectors.joining(", "));
            throw ApiException.badGateway("LLM_INVALID_OUTPUT", "The AI model response failed validation: " + detail, null);
        }
        return new LlmResponse<>(value, raw.model(), raw.latencyMs());
    }

    @Override
    public LlmResponse<String> text(String systemPrompt, String userPrompt) {
        return call(systemPrompt, userPrompt);
    }

    private LlmResponse<String> call(String systemPrompt, String userPrompt) {
        if (!isConfigured()) {
            throw ApiException.unavailable("LLM_NOT_CONFIGURED", "No AI model is configured. Set QUERYAI_LLM_PROVIDER and "
                    + ("anthropic".equals(provider) ? "ANTHROPIC_API_KEY" : "OPENAI_API_KEY")
                    + " on the backend. You can still write and run SQL manually.");
        }
        ChatModel chatModel = chatModelProvider.getObject();
        long started = System.currentTimeMillis();
        try {
            // Messages are passed as objects (not templates), so braces in schema text or SQL are never interpreted.
            ChatResponse response = chatModel.call(new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt))));
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                throw ApiException.badGateway("LLM_EMPTY_RESPONSE", "The AI model returned no answer.", null);
            }
            String text = response.getResult().getOutput().getText();
            if (text == null || text.isBlank()) {
                throw ApiException.badGateway("LLM_EMPTY_RESPONSE", "The AI model returned no answer.", null);
            }
            String model = response.getMetadata() != null && response.getMetadata().getModel() != null
                    && !response.getMetadata().getModel().isBlank() ? response.getMetadata().getModel() : description();
            return new LlmResponse<>(text, model, System.currentTimeMillis() - started);
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("LLM call failed after {} ms: {}", System.currentTimeMillis() - started, e.getMessage());
            throw ApiException.badGateway("LLM_UNAVAILABLE", "The AI provider request failed: " + summarize(e), e);
        }
    }

    private static String summarize(Throwable e) {
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return m.length() > 300 ? m.substring(0, 300) + "…" : m;
    }
}
