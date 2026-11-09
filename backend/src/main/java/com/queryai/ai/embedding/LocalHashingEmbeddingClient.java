package com.queryai.ai.embedding;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Built-in embedding model that needs no network or API key: a signed feature-hashing vectoriser over
 * <ul>
 *     <li>stemmed word tokens (snake_case and camelCase identifiers are split),</li>
 *     <li>business-concept features from a small analytics thesaurus (revenue ≈ sales ≈ income, customer ≈ client ≈ buyer ...),</li>
 *     <li>character trigrams, which make morphological variants and abbreviations land close together.</li>
 * </ul>
 * It is not a neural model; it gives QueryAI a deterministic semantic baseline that works offline and in CI, and the
 * same storage and pgvector search path is used when a real embedding model is configured.
 */
public class LocalHashingEmbeddingClient implements EmbeddingClient {
    public static final int DIMENSIONS = 512;
    private static final String MODEL = "local-hash-512-v1";

    private static final Set<String> STOP = Set.of("the", "a", "an", "of", "by", "for", "and", "or", "to", "in", "on",
            "with", "show", "me", "list", "what", "which", "who", "how", "many", "much", "is", "are", "was", "were", "each",
            "per", "this", "that", "their", "them", "than", "all", "give", "find", "get", "from", "into", "as", "at", "be",
            "do", "does", "did", "my", "our", "i", "we", "you", "it", "its", "one", "row", "table", "column", "columns",
            "used", "using", "id");

    /** Concept groups; every member (after stemming) maps to the group's concept feature. */
    private static final String[][] CONCEPTS = {
            {"revenue", "sale", "sales", "income", "earning", "earnings", "turnover", "gmv", "sell", "sold", "money", "amount", "total"},
            {"customer", "client", "buyer", "shopper", "user", "consumer", "member", "people", "person", "account"},
            {"product", "item", "sku", "good", "goods", "merchandise", "catalog", "article", "offering"},
            {"order", "purchase", "checkout", "basket", "cart", "transaction", "bought", "buy"},
            {"review", "rating", "star", "feedback", "score", "satisfaction", "loved", "love", "liked", "rated"},
            {"inventory", "stock", "quantity", "reorder", "supply", "available", "shortage"},
            {"warehouse", "fulfillment", "fulfilment", "depot", "distribution", "center", "centre", "facility"},
            {"region", "country", "city", "state", "location", "geography", "geographic", "area", "market", "where"},
            {"payment", "paid", "pay", "refund", "refunded", "charge", "billing", "invoice", "method"},
            {"category", "department", "categories", "hierarchy", "parent", "type", "kind"},
            {"segment", "tier", "cohort", "group", "corporate", "business", "consumer"},
            {"discount", "promotion", "promo", "coupon", "markdown", "deal"},
            {"shipping", "delivery", "deliver", "delivered", "shipment", "ship", "shipped", "freight"},
            {"channel", "platform", "web", "mobile", "app", "marketplace", "online", "source"},
            {"brand", "manufacturer", "vendor", "supplier", "maker"},
            {"margin", "profit", "cost", "profitability", "markup", "landed"},
            {"price", "pricing", "expensive", "cheap", "list", "unit"},
            {"date", "time", "month", "year", "day", "week", "quarter", "period", "when", "recent", "recently", "trend", "growth", "signup", "launched"},
            {"status", "cancelled", "canceled", "returned", "processing", "completed", "state"},
    };
    private static final Map<String, String> CONCEPT_OF = new HashMap<>();

    static {
        for (String[] group : CONCEPTS) {
            String concept = "concept:" + group[0];
            for (String word : group) {
                CONCEPT_OF.putIfAbsent(stem(word), concept);
            }
        }
    }

    @Override
    public String model() {
        return MODEL;
    }

    @Override
    public String description() {
        return "built-in hashed embeddings (" + DIMENSIONS + "-d)";
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> out = new ArrayList<>(texts.size());
        for (String text : texts) {
            out.add(embedOne(text));
        }
        return out;
    }

    float[] embedOne(String text) {
        float[] v = new float[DIMENSIONS];
        for (String token : tokens(text)) {
            add(v, "w:" + token, 1.0f);
            String concept = CONCEPT_OF.get(token);
            if (concept != null) {
                add(v, concept, 1.2f);
            }
            String padded = "^" + token + "$";
            for (int i = 0; i + 3 <= padded.length(); i++) {
                add(v, "g:" + padded.substring(i, i + 3), 0.3f);
            }
        }
        return EmbeddingClient.normalize(v);
    }

    private static void add(float[] v, String feature, float weight) {
        int h = fnv1a(feature);
        int index = Math.floorMod(h, DIMENSIONS);
        float sign = ((h >>> 16) & 1) == 0 ? 1f : -1f;
        v[index] += sign * weight;
    }

    private static int fnv1a(String s) {
        int h = 0x811c9dc5;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            h ^= b & 0xff;
            h *= 0x01000193;
        }
        // final avalanche so neighbouring features spread over the vector
        h ^= h >>> 15;
        h *= 0x2c1b3c6d;
        h ^= h >>> 12;
        return h;
    }

    static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        // split camelCase before lower-casing: orderDate -> order Date
        String spaced = text.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
        for (String raw : spaced.split("[^a-z0-9]+")) {
            if (raw.length() < 2 || STOP.contains(raw) || raw.chars().allMatch(Character::isDigit)) {
                continue;
            }
            out.add(stem(raw));
        }
        return out;
    }

    static String stem(String w) {
        if (w.endsWith("ies") && w.length() > 4) {
            return w.substring(0, w.length() - 3) + "y";
        }
        if (w.endsWith("ing") && w.length() > 5) {
            return w.substring(0, w.length() - 3);
        }
        if (w.endsWith("es") && w.length() > 4 && (w.endsWith("ses") || w.endsWith("xes") || w.endsWith("ches") || w.endsWith("shes"))) {
            return w.substring(0, w.length() - 2);
        }
        if (w.endsWith("s") && !w.endsWith("ss") && w.length() > 3) {
            return w.substring(0, w.length() - 1);
        }
        return w;
    }
}
