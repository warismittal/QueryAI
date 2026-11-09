package com.queryai.admin;

import com.queryai.admin.AdminService.PlatformStats;
import com.queryai.admin.AdminService.QuotaRequest;
import com.queryai.admin.AdminService.UpdateUserRequest;
import com.queryai.common.PageResponse;
import com.queryai.user.UserDtos.UserDto;
import com.queryai.workspace.WorkspaceDtos.WorkspaceDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Administration")
public class AdminController {
    private final AdminService service;

    public AdminController(AdminService service) {
        this.service = service;
    }

    @GetMapping("/stats")
    public PlatformStats stats() {
        return service.stats();
    }

    @GetMapping("/users")
    public PageResponse<UserDto> users(@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "25") int size) {
        return service.users(q, page, size);
    }

    @PutMapping("/users/{id}")
    public UserDto updateUser(@PathVariable Long id, @RequestBody UpdateUserRequest request) {
        return service.updateUser(id, request);
    }

    @GetMapping("/workspaces")
    public PageResponse<WorkspaceDto> workspaces(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "25") int size) {
        return service.workspaces(page, size);
    }

    @PutMapping("/workspaces/{id}/quota")
    public WorkspaceDto quota(@PathVariable Long id, @Valid @RequestBody QuotaRequest request) {
        return service.setQuota(id, request);
    }
}
