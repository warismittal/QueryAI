package com.queryai.pii;

import com.queryai.pii.PiiService.PiiOverview;
import com.queryai.pii.PiiService.PiiPolicyRequest;
import com.queryai.pii.PiiService.PiiSettingsRequest;
import com.queryai.schema.SchemaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/datasources/{dataSourceId}/pii")
@Tag(name = "PII protection")
public class PiiController {
    private final PiiService service;
    private final SchemaService schemaService;

    public PiiController(PiiService service, SchemaService schemaService) {
        this.service = service;
        this.schemaService = schemaService;
    }

    @GetMapping
    @Operation(summary = "Columns classified as personal data and whether they are masked")
    public PiiOverview overview(@PathVariable Long dataSourceId) {
        schemaService.getSchema(dataSourceId); // classification needs the schema snapshot; captured on first access
        return service.overview(dataSourceId);
    }

    @PutMapping("/columns")
    @Operation(summary = "Override the classification of one column (admin)")
    public PiiOverview updateColumn(@PathVariable Long dataSourceId, @Valid @RequestBody PiiPolicyRequest request) {
        return service.updateColumn(dataSourceId, request);
    }

    @PutMapping("/settings")
    @Operation(summary = "Turn PII masking on or off for this data source (admin)")
    public PiiOverview updateSettings(@PathVariable Long dataSourceId, @RequestBody PiiSettingsRequest request) {
        return service.updateSettings(dataSourceId, request);
    }
}
