package com.queryai.ai.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Calls any OpenAI-compatible {@code /embeddings} endpoint (OpenAI, Ollama, Gemini's OpenAI layer, Jina, vLLM ...).
 */
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {
    private static final int BATCH = 64;

    private final RestClient client;
    private final String url;
    private final String model;
    private final ObjectMapper mapper;

    public OpenAiCompatibleEmbeddingClient(String url, String apiKey, String model, int timeoutSeconds, ObjectMapper mapper) {
        this.url = url;
        this.model = model;
        this.mapper = mapper;
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(Math.max(2, timeoutSeconds))).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)));
        RestClient.Builder builder = RestClient.builder().requestFactory(factory);
        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + apiKey.trim());
        }
        this.client = builder.build();
    }

    @Override
    public String model() {
        return "openai-compatible:" + model;
    }

    @Override
    public String description() {
        return model + " embeddings";
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> out = new ArrayList<>(texts.size());
        for (int from = 0; from < texts.size(); from += BATCH) {
            List<String> batch = texts.subList(from, Math.min(texts.size(), from + BATCH));
            String body = client.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", model, "input", batch))
                    .retrieve().body(String.class);
            out.addAll(parse(body, batch.size()));
        }
        return out;
    }

    private List<float[]> parse(String body, int expected) {
        try {
            JsonNode data = mapper.readTree(body == null ? "{}" : body).path("data");
            if (!data.isArray() || data.size() != expected) {
                throw new IllegalStateException("Embedding endpoint returned " + data.size() + " vectors for " + expected + " inputs");
            }
            float[][] ordered = new float[expected][];
            for (int i = 0; i < data.size(); i++) {
                JsonNode item = data.get(i);
                int index = item.has("index") ? item.get("index").asInt() : i;
                JsonNode vector = item.path("embedding");
                float[] v = new float[vector.size()];
                for (int j = 0; j < v.length; j++) {
                    v[j] = (float) vector.get(j).asDouble();
                }
                ordered[index] = EmbeddingClient.normalize(v);
            }
            return List.of(ordered);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse the embedding response: " + e.getMessage(), e);
        }
    }
}
