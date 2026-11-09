package com.queryai.savedquery;

import com.queryai.history.QuerySource;
import com.queryai.savedquery.SavedQueryDtos.*;
import com.queryai.sql.SqlDtos.ExecuteResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/saved-queries")
@Tag(name = "Saved queries")
public class SavedQueryController {
    private final SavedQueryService service;

    public SavedQueryController(SavedQueryService service) {
        this.service = service;
    }

    @GetMapping
    public List<SavedQueryDto> list(@RequestParam Long workspaceId, @RequestParam(required = false) String folder,
                                    @RequestParam(defaultValue = "false") boolean templates,
                                    @RequestParam(required = false) String q) {
        return service.list(workspaceId, folder, templates, q);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SavedQueryDto create(@Valid @RequestBody CreateSavedQueryRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public SavedQueryDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public SavedQueryDto update(@PathVariable Long id, @Valid @RequestBody UpdateSavedQueryRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public SavedQueryDto duplicate(@PathVariable Long id) {
        return service.duplicate(id);
    }

    @GetMapping("/{id}/versions")
    public List<VersionDto> versions(@PathVariable Long id) {
        return service.versions(id);
    }

    @PostMapping("/{id}/versions/{version}/restore")
    public SavedQueryDto restore(@PathVariable Long id, @PathVariable int version) {
        return service.restore(id, version);
    }

    @PostMapping("/{id}/run")
    public ExecuteResponse run(@PathVariable Long id, @RequestBody(required = false) RunSavedQueryRequest request) {
        return service.run(id, request == null ? null : request.parameters(), QuerySource.SAVED_QUERY);
    }
}
