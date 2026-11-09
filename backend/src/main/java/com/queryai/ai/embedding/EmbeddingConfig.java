package com.queryai.ai.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.queryai.config.QueryAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;
import java.util.Optional;

@Configuration
public class EmbeddingConfig {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingConfig.class);

    /** Holds no client when semantic search is switched off ({@code queryai.embeddings.provider=none}). */
    @Bean
    public EmbeddingProvider embeddingProvider(QueryAiProperties properties, ObjectMapper mapper) {
        QueryAiProperties.Embeddings cfg = properties.embeddings();
        String provider = cfg == null || cfg.provider() == null ? "local" : cfg.provider().trim().toLowerCase(Locale.ROOT);
        switch (provider) {
            case "none", "off", "disabled" -> {
                log.info("Semantic schema search disabled (queryai.embeddings.provider=none)");
                return new EmbeddingProvider(Optional.empty());
            }
            case "openai", "openai-compatible", "ollama", "gemini" -> {
                if (cfg.url() == null || cfg.url().isBlank() || cfg.model() == null || cfg.model().isBlank()) {
                    log.warn("Embeddings provider '{}' needs QUERYAI_EMBEDDINGS_URL and QUERYAI_EMBEDDINGS_MODEL; "
                            + "using the built-in local embeddings instead", provider);
                    return new EmbeddingProvider(Optional.of(new LocalHashingEmbeddingClient()));
                }
                log.info("Semantic schema search uses {} at {}", cfg.model(), cfg.url());
                return new EmbeddingProvider(Optional.of(new OpenAiCompatibleEmbeddingClient(cfg.url().trim(), cfg.apiKey(),
                        cfg.model().trim(), cfg.timeoutSeconds() <= 0 ? 20 : cfg.timeoutSeconds(), mapper)));
            }
            default -> {
                log.info("Semantic schema search uses the built-in local embeddings");
                return new EmbeddingProvider(Optional.of(new LocalHashingEmbeddingClient()));
            }
        }
    }

    /** Wrapper bean so "no embeddings" can be expressed without a null bean. */
    public record EmbeddingProvider(Optional<EmbeddingClient> client) {
    }
}
