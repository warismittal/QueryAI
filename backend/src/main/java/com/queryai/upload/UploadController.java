package com.queryai.upload;

import com.queryai.upload.UploadService.UploadResult;
import com.queryai.upload.UploadService.UploadedTableDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/uploads")
@Tag(name = "Uploads")
public class UploadController {
    private final UploadService service;

    public UploadController(UploadService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a CSV file as a new table (types are inferred; data becomes queryable immediately)")
    public UploadResult upload(@RequestParam("file") MultipartFile file, @RequestParam Long workspaceId,
                               @RequestParam(required = false) String tableName,
                               @RequestParam(defaultValue = "false") boolean replace) {
        return service.upload(file, workspaceId, tableName, replace);
    }

    @GetMapping
    public List<UploadedTableDto> list(@RequestParam Long workspaceId) {
        return service.list(workspaceId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
