package com.queryai.ai;

import com.queryai.ai.embedding.SemanticSchemaIndex;
import com.queryai.ai.embedding.SemanticSchemaIndex.SemanticResult;
import com.queryai.config.QueryAiProperties;
import com.queryai.datasource.DataSourceEntity;
import com.queryai.schema.SchemaDtos.ColumnView;
import com.queryai.schema.SchemaDtos.SchemaView;
import com.queryai.schema.SchemaDtos.TableView;
import com.queryai.schema.SchemaService;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Selects the tables relevant to a question from the authorised schema snapshot and renders them as compact,
 * LLM-friendly DDL-like text including keys, relationships and business descriptions.
 */
@Service
public class SchemaRetrievalService {
    private final SchemaService schemaService;
    private final SemanticSchemaIndex semanticIndex;
    private final int maxTables;

    public SchemaRetrievalService(SchemaService schemaService, SemanticSchemaIndex semanticIndex, QueryAiProperties properties) {
        this.schemaService = schemaService;
        this.semanticIndex = semanticIndex;
        this.maxTables = Math.max(3, properties.ai().maxSchemaTables());
    }

    /** {@code method} describes how tables were ranked, e.g. "hybrid · pgvector · local-hash-512-v1" or "lexical". */
    public record RetrievedSchema(List<String> tables, String promptText, int totalTables, String method) {
        public RetrievedSchema(List<String> tables, String promptText, int totalTables) {
            this(tables, promptText, totalTables, "lexical");
        }
    }

    public RetrievedSchema retrieve(DataSourceEntity ds, String question, List<String> keywords) {
        SchemaView view = schemaService.toView(ds, schemaService.ensureSnapshot(ds));
        Map<String, Set<String>> neighbours = new HashMap<>();
        view.relationships().forEach(r -> {
            neighbours.computeIfAbsent(r.fromTable(), k -> new HashSet<>()).add(r.toTable());
            neighbours.computeIfAbsent(r.toTable(), k -> new HashSet<>()).add(r.fromTable());
        });
        List<SchemaRanker.TableInfo> infos = view.tables().stream().map(t -> new SchemaRanker.TableInfo(
                t.name(), t.columns().stream().map(ColumnView::name).toList(), t.description(),
                t.columns().stream().map(ColumnView::description).toList(), neighbours.getOrDefault(t.name(), Set.of()))).toList();
        String query = keywords == null || keywords.isEmpty() ? question : question + " " + String.join(" ", keywords);
        SemanticResult semantic = semanticIndex.search(ds.getId(), view, query, view.tables().size());
        List<String> selected = SchemaRanker.rank(question, keywords, infos, maxTables, semantic.scores());
        String method = "off".equals(semantic.engine()) ? "lexical" : "hybrid · " + semantic.engine() + " · " + semantic.model();
        Map<String, TableView> byName = new HashMap<>();
        view.tables().forEach(t -> byName.put(t.name(), t));

        StringBuilder sb = new StringBuilder();
        sb.append("Schema: ").append(view.schema()).append(" (").append(view.dbType()).append(")\n");
        for (String name : selected) {
            TableView t = byName.get(name);
            sb.append("\nTABLE ").append(t.name());
            if (t.rowEstimate() != null && t.rowEstimate() > 0) {
                sb.append("  -- ~").append(t.rowEstimate()).append(" rows");
            }
            if (t.description() != null) {
                sb.append("  -- ").append(oneLine(t.description()));
            }
            sb.append('\n');
            for (ColumnView c : t.columns()) {
                sb.append("  ").append(c.name()).append(' ').append(c.dataType());
                if (c.primaryKey()) {
                    sb.append(" PRIMARY KEY");
                }
                if (c.references() != null) {
                    sb.append(" REFERENCES ").append(c.references());
                }
                if (!c.nullable() && !c.primaryKey()) {
                    sb.append(" NOT NULL");
                }
                if (c.description() != null) {
                    sb.append("  -- ").append(oneLine(c.description()));
                }
                sb.append('\n');
            }
        }
        if (selected.size() < view.tables().size()) {
            List<String> others = view.tables().stream().map(TableView::name).filter(n -> !selected.contains(n)).toList();
            sb.append("\nOther tables exist but were judged irrelevant: ").append(String.join(", ", others)).append('\n');
        }
        return new RetrievedSchema(selected, sb.toString(), view.tables().size(), method);
    }

    private static String oneLine(String s) {
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > 240 ? t.substring(0, 240) + "…" : t;
    }
}
