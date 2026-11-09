package com.queryai.workspace;

import com.queryai.workspace.WorkspaceDtos.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workspaces")
@Tag(name = "Workspaces")
public class WorkspaceController {
    private final WorkspaceService service;

    public WorkspaceController(WorkspaceService service) {
        this.service = service;
    }

    @GetMapping
    public List<WorkspaceDto> list() {
        return service.listMine();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceDto create(@Valid @RequestBody CreateWorkspaceRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public WorkspaceDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public WorkspaceDto update(@PathVariable Long id, @Valid @RequestBody UpdateWorkspaceRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @GetMapping("/{id}/members")
    public List<MemberDto> members(@PathVariable Long id) {
        return service.listMembers(id);
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberDto addMember(@PathVariable Long id, @Valid @RequestBody AddMemberRequest request) {
        return service.addMember(id, request);
    }

    @PutMapping("/{id}/members/{userId}")
    public MemberDto changeRole(@PathVariable Long id, @PathVariable Long userId, @Valid @RequestBody ChangeRoleRequest request) {
        return service.changeRole(id, userId, request);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable Long id, @PathVariable Long userId) {
        service.removeMember(id, userId);
    }

    @GetMapping("/{id}/usage")
    public UsageDto usage(@PathVariable Long id) {
        return service.usage(id);
    }
}
