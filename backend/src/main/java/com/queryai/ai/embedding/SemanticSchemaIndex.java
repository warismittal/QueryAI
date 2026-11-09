package com.queryai.ai.embedding;

import com.queryai.ai.embedding.EmbeddingConfig.EmbeddingProvider;
import com.queryai.schema.SchemaDtos.ColumnView;
import com.queryai.schema.SchemaDtos.RelationshipView;
import com.queryai.schema.SchemaDtos.SchemaView;
import com.queryai.schema.SchemaDtos.TableView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Array;
import java.sql.SQLException;
import java.util.*;

/**
 * Semantic index of a data source's tables. Every table is described as a short document (name, description,
 * columns, column notes, relationships), embedded once per schema version and stored in {@code schema_embeddings}.
 * Questions are embedded at query time and matched by cosine similarity — inside PostgreSQL with pgvector's
 * {@code <=>} operator when the extension is installed, otherwise in the application.
 */
@Service
public class SemanticSchemaIndex {
    private static final Logger log = LoggerFactory.getLogger(SemanticSchemaIndex.class);

    private final JdbcTemplate jdbc;
    private final Optional<EmbeddingClient> client;
    private volatile Boolean pgvector;

    public SemanticSchemaIndex(JdbcTemplate jdbc, EmbeddingProvider provider) {
        this.jdbc = jdbc;
        this.client = provider.client();
    }

    public record Match(String table, double score) {
    }

    /** Result of a semantic lookup; {@code engine} is "pgvector", "in-app" or "off". */
    public record SemanticResult(List<Match> matches, String engine, String model) {
        public Map<String, Double> scores() {
            Map<String, Double> m = new HashMap<>();
            matches.forEach(x -> m.put(x.table(), x.score()));
            return m;
        }

        public static SemanticResult off() {
            return new SemanticResult(List.of(), "off", null);
        }
    }

    public boolean enabled() {
        return client.isPresent();
    }

    public String description() {
        return client.map(EmbeddingClient::description).orElse("disabled");
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean pgvectorAvailable() {
        Boolean known = pgvector;
        if (known == null) {
            try {
                known = Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector')", Boolean.class));
            } catch (RuntimeException e) {
                known = false;
            }
            pgvector = known;
            log.info("Semantic schema search engine: {}", known ? "pgvector (cosine distance in PostgreSQL)" : "in-app cosine similarity");
        }
        return known;
    }

    /**
     * Ranks all tables of the schema by similarity to {@code question}. Never throws: any failure (provider down,
     * timeout) is logged and reported as engine "off" so retrieval falls back to lexical ranking. Runs outside any
     * surrounding transaction so a failed statement here can never poison the caller's transaction.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SemanticResult search(Long dataSourceId, SchemaView view, String question, int limit) {
        if (client.isEmpty() || view.tables().isEmpty() || question == null || question.isBlank()) {
            return SemanticResult.off();
        }
        EmbeddingClient embedder = client.get();
        try {
            ensureIndexed(dataSourceId, view, embedder);
            float[] q = embedder.embed(List.of(question)).get(0);
            if (pgvectorAvailable()) {
                try {
                    return new SemanticResult(searchInDatabase(dataSourceId, embedder.model(), q, limit), "pgvector", embedder.model());
                } catch (RuntimeException e) {
                    log.warn("pgvector search failed, ranking in the application instead: {}", e.getMessage());
                }
            }
            return new SemanticResult(searchInApp(dataSourceId, embedder.model(), q, limit), "in-app", embedder.model());
        } catch (RuntimeException e) {
            log.warn("Semantic schema search unavailable for data source {}: {}", dataSourceId, e.getMessage());
            return SemanticResult.off();
        }
    }

    /** Drops stored embeddings, e.g. after a schema refresh changed many tables. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void invalidate(Long dataSourceId) {
        jdbc.update("DELETE FROM schema_embeddings WHERE data_source_id = ?", dataSourceId);
    }

    private List<Match> searchInDatabase(Long dataSourceId, String model, float[] q, int limit) {
        String vector = toVectorLiteral(q);
        return jdbc.query("""
                        SELECT table_name, 1 - (embedding::vector <=> CAST(? AS vector)) AS score
                        FROM schema_embeddings
                        WHERE data_source_id = ? AND model = ?
                        ORDER BY embedding::vector <=> CAST(? AS vector)
                        LIMIT ?""",
                (rs, i) -> new Match(rs.getString(1), rs.getDouble(2)), vector, dataSourceId, model, vector, limit);
    }

    private List<Match> searchInApp(Long dataSourceId, String model, float[] q, int limit) {
        List<Match> matches = new ArrayList<>();
        jdbc.query("SELECT table_name, embedding FROM schema_embeddings WHERE data_source_id = ? AND model = ?", rs -> {
            matches.add(new Match(rs.getString(1), EmbeddingClient.cosine(q, toFloats(rs.getArray(2)))));
        }, dataSourceId, model);
        matches.sort(Comparator.comparingDouble(Match::score).reversed());
        return matches.size() > limit ? new ArrayList<>(matches.subList(0, limit)) : matches;
    }

    private void ensureIndexed(Long dataSourceId, SchemaView view, EmbeddingClient embedder) {
        Map<String, String> docs = new LinkedHashMap<>();
        Map<String, Set<String>> related = new HashMap<>();
        for (RelationshipView r : view.relationships()) {
            related.computeIfAbsent(r.fromTable(), k -> new TreeSet<>()).add(r.toTable());
            related.computeIfAbsent(r.toTable(), k -> new TreeSet<>()).add(r.fromTable());
        }
        for (TableView t : view.tables()) {
            docs.put(t.name(), document(t, related.getOrDefault(t.name(), Set.of())));
        }
        Map<String, String> stored = new HashMap<>();
        jdbc.query("SELECT table_name, content_hash FROM schema_embeddings WHERE data_source_id = ? AND model = ?",
                rs -> {
                    stored.put(rs.getString(1), rs.getString(2));
                }, dataSourceId, embedder.model());

        List<String> changed = new ArrayList<>();
        Map<String, String> hashes = new HashMap<>();
        docs.forEach((table, doc) -> {
            String h = sha256(doc);
            hashes.put(table, h);
            if (!h.equals(stored.get(table))) {
                changed.add(table);
            }
        });
        for (String old : stored.keySet()) {
            if (!docs.containsKey(old)) {
                jdbc.update("DELETE FROM schema_embeddings WHERE data_source_id = ? AND model = ? AND table_name = ?",
                        dataSourceId, embedder.model(), old);
            }
        }
        if (changed.isEmpty()) {
            return;
        }
        List<float[]> vectors = embedder.embed(changed.stream().map(docs::get).toList());
        for (int i = 0; i < changed.size(); i++) {
            String table = changed.get(i);
            Float[] boxed = box(vectors.get(i));
            jdbc.update(con -> {
                var ps = con.prepareStatement("""
                        INSERT INTO schema_embeddings (data_source_id, table_name, model, content_hash, content, embedding, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, now())
                        ON CONFLICT (data_source_id, table_name, model)
                        DO UPDATE SET content_hash = EXCLUDED.content_hash, content = EXCLUDED.content,
                                      embedding = EXCLUDED.embedding, updated_at = now()""");
                ps.setLong(1, dataSourceId);
                ps.setString(2, table);
                ps.setString(3, embedder.model());
                ps.setString(4, hashes.get(table));
                ps.setString(5, docs.get(table));
                ps.setArray(6, con.createArrayOf("float4", boxed));
                return ps;
            });
        }
        log.info("Indexed {} table(s) of data source {} for semantic search ({})", changed.size(), dataSourceId, embedder.model());
    }

    /** The text that represents a table in the vector space. */
    static String document(TableView t, Set<String> related) {
        StringBuilder sb = new StringBuilder();
        sb.append("Table ").append(t.name().replace('_', ' ')).append(" (").append(t.name()).append(").");
        if (t.description() != null && !t.description().isBlank()) {
            sb.append(' ').append(t.description().trim());
        }
        sb.append(" Columns: ");
        StringJoiner cols = new StringJoiner(", ");
        for (ColumnView c : t.columns()) {
            String col = c.name().replace('_', ' ');
            if (c.description() != null && !c.description().isBlank()) {
                col += " — " + c.description().trim();
            }
            cols.add(col);
        }
        sb.append(cols).append('.');
        if (!related.isEmpty()) {
            sb.append(" Related to: ").append(String.join(", ", related)).append('.');
        }
        return sb.toString();
    }

    private static String toVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 10).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }

    private static Float[] box(float[] v) {
        Float[] out = new Float[v.length];
        for (int i = 0; i < v.length; i++) {
            out[i] = v[i];
        }
        return out;
    }

    private static float[] toFloats(Array array) throws SQLException {
        Object raw = array.getArray();
        if (raw instanceof Float[] f) {
            float[] out = new float[f.length];
            for (int i = 0; i < f.length; i++) {
                out[i] = f[i] == null ? 0f : f[i];
            }
            return out;
        }
        if (raw instanceof float[] f) {
            return f;
        }
        if (raw instanceof Object[] o) {
            float[] out = new float[o.length];
            for (int i = 0; i < o.length; i++) {
                out[i] = o[i] == null ? 0f : ((Number) o[i]).floatValue();
            }
            return out;
        }
        throw new SQLException("Unexpected embedding array type " + raw.getClass());
    }

    private static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
