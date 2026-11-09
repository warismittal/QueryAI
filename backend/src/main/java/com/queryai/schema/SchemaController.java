package com.queryai.schema;

import com.queryai.schema.SchemaDtos.*;
import com.queryai.sql.QueryResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/datasources/{dataSourceId}")
@Tag(name = "Schema & glossary")
public class SchemaController {
    private final SchemaService schemaService;
    private final GlossaryService glossaryService;

    public SchemaController(SchemaService schemaService, GlossaryService glossaryService) {
        this.schemaService = schemaService;
        this.glossaryService = glossaryService;
    }

    @GetMapping("/schema")
    @Operation(summary = "Schema metadata (captured on first access) merged with business descriptions")
    public SchemaView schema(@PathVariable Long dataSourceId) {
        return schemaService.getSchema(dataSourceId);
    }

    @PostMapping("/schema/refresh")
    @Operation(summary = "Re-read tables, columns, keys and indexes from the database")
    public SchemaView refresh(@PathVariable Long dataSourceId) {
        return schemaService.refresh(dataSourceId);
    }

    @GetMapping("/schema/search")
    @Operation(summary = "Semantic search over tables (embeddings + pgvector cosine similarity)")
    public SchemaSearchResponse search(@PathVariable Long dataSourceId, @RequestParam("q") String query) {
        return schemaService.semanticSearch(dataSourceId, query);
    }

    @GetMapping("/tables/{table}/preview")
    @Operation(summary = "First 25 rows of a table")
    public QueryResult preview(@PathVariable Long dataSourceId, @PathVariable String table) {
        return schemaService.preview(dataSourceId, table);
    }

    @PutMapping("/annotations")
    @Operation(summary = "Set or clear the business description of a table or column")
    public SchemaView annotate(@PathVariable Long dataSourceId, @Valid @RequestBody AnnotationRequest request) {
        return schemaService.annotate(dataSourceId, request);
    }

    @GetMapping("/glossary")
    public List<GlossaryTermDto> glossary(@PathVariable Long dataSourceId) {
        return glossaryService.list(dataSourceId);
    }

    @PostMapping("/glossary")
    @ResponseStatus(HttpStatus.CREATED)
    public GlossaryTermDto createTerm(@PathVariable Long dataSourceId, @Valid @RequestBody GlossaryTermRequest request) {
        return glossaryService.create(dataSourceId, request);
    }

    @PutMapping("/glossary/{termId}")
    public GlossaryTermDto updateTerm(@PathVariable Long dataSourceId, @PathVariable Long termId,
                                      @Valid @RequestBody GlossaryTermRequest request) {
        return glossaryService.update(dataSourceId, termId, request);
    }

    @DeleteMapping("/glossary/{termId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTerm(@PathVariable Long dataSourceId, @PathVariable Long termId) {
        glossaryService.delete(dataSourceId, termId);
    }
}
