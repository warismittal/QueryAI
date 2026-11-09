package com.queryai.ai.embedding;

import java.util.List;

/**
 * Turns text into dense vectors for semantic schema search. Implementations return one L2-normalised vector per input
 * (in input order) and throw on failure; callers fall back to lexical ranking.
 */
public interface EmbeddingClient {

    /** Stable identifier of the model; embeddings are stored and compared per model. */
    String model();

    /** Human-readable description for the UI and pipeline trace. */
    String description();

    List<float[]> embed(List<String> texts);

    static float[] normalize(float[] v) {
        double sum = 0;
        for (float x : v) {
            sum += x * x;
        }
        if (sum == 0) {
            return v;
        }
        float inv = (float) (1.0 / Math.sqrt(sum));
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            out[i] = v[i] * inv;
        }
        return out;
    }

    static double cosine(float[] a, float[] b) {
        if (a.length != b.length) {
            return 0;
        }
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return na == 0 || nb == 0 ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
