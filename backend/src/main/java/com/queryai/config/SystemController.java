package com.queryai.config;

import com.queryai.ai.LlmClient;
import com.queryai.ai.embedding.SemanticSchemaIndex;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.info.BuildProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public, non-sensitive capability information used by the UI (e.g. to explain that AI is not configured). */
@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "System")
public class SystemController {
    private final LlmClient llm;
    private final QueryAiProperties properties;
    private final ObjectProvider<BuildProperties> build;
    private final SemanticSchemaIndex semanticIndex;

    public SystemController(LlmClient llm, QueryAiProperties properties, ObjectProvider<BuildProperties> build,
                            SemanticSchemaIndex semanticIndex) {
        this.llm = llm;
        this.properties = properties;
        this.build = build;
        this.semanticIndex = semanticIndex;
    }

    public record SystemInfo(String version, boolean llmConfigured, String llmProvider, List<String> supportedDatabases,
                             boolean demoEnabled, boolean semanticSearch, String embeddingModel, boolean pgvector,
                             boolean uploadsEnabled, boolean guestAccess) {
    }

    @GetMapping("/info")
    public SystemInfo info() {
        BuildProperties bp = build.getIfAvailable();
        return new SystemInfo(bp == null ? "1.0.0" : bp.getVersion(), llm.isConfigured(),
                llm.isConfigured() ? llm.description() : null, List.of("POSTGRESQL", "MYSQL"), properties.demo().enabled(),
                semanticIndex.enabled(), semanticIndex.description(), semanticIndex.enabled() && semanticIndex.pgvectorAvailable(),
                properties.warehouse() != null && properties.warehouse().enabled(),
                properties.demo().enabled() && properties.demo().publicMode());
    }
}
