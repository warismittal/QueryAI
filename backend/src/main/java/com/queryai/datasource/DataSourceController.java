package com.queryai.datasource;

import com.queryai.datasource.DataSourceDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/datasources")
@Tag(name = "Data sources")
public class DataSourceController {
    private final DataSourceService service;

    public DataSourceController(DataSourceService service) {
        this.service = service;
    }

    @GetMapping
    public List<DataSourceDto> list(@RequestParam Long workspaceId) {
        return service.list(workspaceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a PostgreSQL or MySQL connection (password is encrypted at rest)")
    public DataSourceDto create(@Valid @RequestBody DataSourceRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public DataSourceDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public DataSourceDto update(@PathVariable Long id, @Valid @RequestBody DataSourceRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/test")
    @Operation(summary = "Run connection diagnostics for unsaved settings")
    public ConnectionTestResult testAdhoc(@Valid @RequestBody TestConnectionRequest request) {
        return service.testAdhoc(request);
    }

    @PostMapping("/{id}/test")
    @Operation(summary = "Run connection diagnostics for a saved data source")
    public ConnectionTestResult test(@PathVariable Long id) {
        return service.testSaved(id);
    }

    @GetMapping("/{id}/grants")
    public GrantsDto grants(@PathVariable Long id) {
        return service.getGrants(id);
    }

    @PutMapping("/{id}/grants")
    public GrantsDto updateGrants(@PathVariable Long id, @Valid @RequestBody UpdateGrantsRequest request) {
        return service.updateGrants(id, request);
    }
}
