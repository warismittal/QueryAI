package com.queryai.sql;

import com.queryai.history.QuerySource;
import com.queryai.sql.SqlDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sql")
@Tag(name = "SQL validation & execution")
public class SqlController {
    private final SqlService sqlService;

    public SqlController(SqlService sqlService) {
        this.sqlService = sqlService;
    }

    @PostMapping("/validate")
    @Operation(summary = "Static safety/authorisation checks plus an EXPLAIN dry run against the database")
    public SqlValidationResult validate(@Valid @RequestBody ValidateRequest request) {
        return sqlService.validate(request);
    }

    @PostMapping("/execute")
    @Operation(summary = "Validate and execute SQL in a read-only transaction; always recorded in history")
    public ExecuteResponse execute(@Valid @RequestBody ExecuteRequest request) {
        return sqlService.execute(request, QuerySource.MANUAL);
    }
}
