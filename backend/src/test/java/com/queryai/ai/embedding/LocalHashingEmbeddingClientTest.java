package com.queryai.ai.embedding;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalHashingEmbeddingClientTest {
    private final LocalHashingEmbeddingClient client = new LocalHashingEmbeddingClient();
    private final List<String> docs = List.of(
            "Table orders. Customer orders. Columns: id, customer id, order date, status, total amount.",
            "Table product reviews. Star ratings (1-5) left by customers. Columns: rating, review date, product id.",
            "Table inventory. Current stock per product per warehouse. Columns: quantity on hand, reorder level.",
            "Table payments. Payments captured for orders. Columns: payment method, amount, paid at.");

    private int best(String question) {
        List<float[]> vectors = client.embed(docs);
        float[] q = client.embed(List.of(question)).get(0);
        int best = -1;
        double score = -2;
        for (int i = 0; i < vectors.size(); i++) {
            double s = EmbeddingClient.cosine(q, vectors.get(i));
            if (s > score) {
                score = s;
                best = i;
            }
        }
        return best;
    }

    @Test
    void producesNormalisedDeterministicVectors() {
        float[] a = client.embed(List.of("monthly revenue")).get(0);
        float[] b = client.embed(List.of("monthly revenue")).get(0);
        assertThat(a).hasSize(LocalHashingEmbeddingClient.DIMENSIONS).containsExactly(b);
        assertThat(EmbeddingClient.cosine(a, a)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void matchesQuestionsToTablesThroughBusinessSynonyms() {
        assertThat(best("Which product is loved the most?")).isEqualTo(1);
        assertThat(best("Where are we running low on stock?")).isEqualTo(2);
        assertThat(best("How did sales grow last quarter?")).isEqualTo(0);
        assertThat(best("refunds by method")).isEqualTo(3);
    }
}
