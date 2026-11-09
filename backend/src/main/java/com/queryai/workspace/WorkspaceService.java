package com.queryai.workspace;

import com.queryai.audit.AuditAction;
import com.queryai.audit.AuditService;
import com.queryai.common.ApiException;
import com.queryai.history.QueryRunRepository;
import com.queryai.history.QuerySource;
import com.queryai.security.CurrentUser;
import com.queryai.user.User;
import com.queryai.user.UserRepository;
import com.queryai.workspace.WorkspaceDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Service
public class WorkspaceService {
    private final WorkspaceRepository workspaces;
    private final WorkspaceMemberRepository members;
    private final UserRepository users;
    private final WorkspaceAccessService access;
    private final CurrentUser currentUser;
    private final QueryRunRepository queryRuns;
    private final AuditService audit;

    public WorkspaceService(WorkspaceRepository workspaces, WorkspaceMemberRepository members, UserRepository users,
                            WorkspaceAccessService access, CurrentUser currentUser, QueryRunRepository queryRuns,
                            AuditService audit) {
        this.workspaces = workspaces;
        this.members = members;
        this.users = users;
        this.access = access;
        this.currentUser = currentUser;
        this.queryRuns = queryRuns;
        this.audit = audit;
    }

    @Transactional
    public Workspace createPersonalWorkspace(User owner) {
        String first = owner.getFullName().split("\\s+")[0];
        return createInternal(owner, first + "'s Workspace", "Personal workspace");
    }

    @Transactional
    public WorkspaceDto create(CreateWorkspaceRequest request) {
        User user = currentUser.require();
        Workspace ws = createInternal(user, request.name().trim(), request.description());
        audit.record(AuditAction.WORKSPACE_CREATED, ws.getId(), "WORKSPACE", ws.getId(), Map.of("name", ws.getName()));
        return toDto(ws, WorkspaceRole.OWNER);
    }

    private Workspace createInternal(User owner, String name, String description) {
        Workspace ws = new Workspace();
        ws.setName(name);
        ws.setDescription(description);
        ws.setOwnerId(owner.getId());
        workspaces.save(ws);
        WorkspaceMember member = new WorkspaceMember();
        member.setWorkspaceId(ws.getId());
        member.setUser(owner);
        member.setRole(WorkspaceRole.OWNER);
        members.save(member);
        return ws;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceDto> listMine() {
        User user = currentUser.require();
        return workspaces.findForUser(user.getId()).stream()
                .map(ws -> toDto(ws, members.findMembership(ws.getId(), user.getId()).map(WorkspaceMember::getRole).orElse(WorkspaceRole.VIEWER)))
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkspaceDto get(Long id) {
        WorkspaceRole role = access.roleIn(id);
        return toDto(load(id), role);
    }

    @Transactional
    public WorkspaceDto update(Long id, UpdateWorkspaceRequest request) {
        WorkspaceRole role = access.require(id, WorkspaceRole.ADMIN);
        Workspace ws = load(id);
        ws.setName(request.name().trim());
        ws.setDescription(request.description());
        audit.record(AuditAction.WORKSPACE_UPDATED, id, "WORKSPACE", id, Map.of("name", ws.getName()));
        return toDto(ws, role);
    }

    @Transactional
    public void delete(Long id) {
        access.require(id, WorkspaceRole.OWNER);
        User user = currentUser.require();
        if (workspaces.findForUser(user.getId()).size() <= 1 && members.findMembership(id, user.getId()).isPresent()) {
            throw ApiException.badRequest("You cannot delete your only workspace");
        }
        Workspace ws = load(id);
        audit.record(AuditAction.WORKSPACE_DELETED, null, "WORKSPACE", id, Map.of("name", ws.getName()));
        workspaces.delete(ws);
    }

    @Transactional(readOnly = true)
    public List<MemberDto> listMembers(Long workspaceId) {
        access.requireMember(workspaceId);
        return members.findByWorkspaceWithUser(workspaceId).stream().map(MemberDto::from).toList();
    }

    @Transactional
    public MemberDto addMember(Long workspaceId, AddMemberRequest request) {
        WorkspaceRole myRole = access.require(workspaceId, WorkspaceRole.ADMIN);
        if (request.role() == WorkspaceRole.OWNER && myRole != WorkspaceRole.OWNER) {
            throw ApiException.forbidden("Only owners can add other owners");
        }
        User user = users.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> ApiException.badRequest("No QueryAI account exists for " + request.email()
                        + ". Ask them to register first."));
        if (members.findMembership(workspaceId, user.getId()).isPresent()) {
            throw ApiException.conflict(user.getEmail() + " is already a member of this workspace");
        }
        WorkspaceMember member = new WorkspaceMember();
        member.setWorkspaceId(workspaceId);
        member.setUser(user);
        member.setRole(request.role());
        members.save(member);
        audit.record(AuditAction.MEMBER_ADDED, workspaceId, "USER", user.getId(), Map.of("email", user.getEmail(), "role", request.role().name()));
        return MemberDto.from(member);
    }

    @Transactional
    public MemberDto changeRole(Long workspaceId, Long userId, ChangeRoleRequest request) {
        WorkspaceRole myRole = access.require(workspaceId, WorkspaceRole.ADMIN);
        WorkspaceMember member = members.findMembership(workspaceId, userId).orElseThrow(() -> ApiException.notFound("Member"));
        boolean touchesOwner = member.getRole() == WorkspaceRole.OWNER || request.role() == WorkspaceRole.OWNER;
        if (touchesOwner && myRole != WorkspaceRole.OWNER) {
            throw ApiException.forbidden("Only owners can grant or revoke the OWNER role");
        }
        if (member.getRole() == WorkspaceRole.OWNER && request.role() != WorkspaceRole.OWNER
                && members.countByWorkspaceIdAndRole(workspaceId, WorkspaceRole.OWNER) <= 1) {
            throw ApiException.badRequest("A workspace must keep at least one owner");
        }
        WorkspaceRole previous = member.getRole();
        member.setRole(request.role());
        audit.record(AuditAction.MEMBER_ROLE_CHANGED, workspaceId, "USER", userId, Map.of("from", previous.name(), "to", request.role().name()));
        return MemberDto.from(member);
    }

    @Transactional
    public void removeMember(Long workspaceId, Long userId) {
        boolean self = userId.equals(currentUser.id());
        WorkspaceRole myRole = self ? access.roleIn(workspaceId) : access.require(workspaceId, WorkspaceRole.ADMIN);
        WorkspaceMember member = members.findMembership(workspaceId, userId).orElseThrow(() -> ApiException.notFound("Member"));
        if (member.getRole() == WorkspaceRole.OWNER) {
            if (!self && myRole != WorkspaceRole.OWNER) {
                throw ApiException.forbidden("Only owners can remove an owner");
            }
            if (members.countByWorkspaceIdAndRole(workspaceId, WorkspaceRole.OWNER) <= 1) {
                throw ApiException.badRequest("A workspace must keep at least one owner");
            }
        }
        members.delete(member);
        audit.record(AuditAction.MEMBER_REMOVED, workspaceId, "USER", userId, Map.of("self", self));
    }

    @Transactional(readOnly = true)
    public UsageDto usage(Long workspaceId) {
        access.requireMember(workspaceId);
        Workspace ws = load(workspaceId);
        Instant periodStart = startOfMonth();
        long ai = queryRuns.countByWorkspaceIdAndSourceAndCreatedAtGreaterThanEqual(workspaceId, QuerySource.AI, periodStart);
        long total = queryRuns.countByWorkspaceIdAndCreatedAtGreaterThanEqual(workspaceId, periodStart);
        return new UsageDto(workspaceId, periodStart, ai, total, ws.getMonthlyQueryQuota(), Math.max(0, ws.getMonthlyQueryQuota() - ai));
    }

    /** Throws 429 when the workspace has used its monthly AI generation quota. */
    @Transactional(readOnly = true)
    public void ensureAiQuota(Long workspaceId) {
        Workspace ws = load(workspaceId);
        long used = queryRuns.countByWorkspaceIdAndSourceAndCreatedAtGreaterThanEqual(workspaceId, QuerySource.AI, startOfMonth());
        if (used >= ws.getMonthlyQueryQuota()) {
            throw ApiException.tooManyRequests("This workspace has used its monthly quota of " + ws.getMonthlyQueryQuota()
                    + " AI queries. Ask a platform administrator to raise it.");
        }
    }

    @Transactional
    public WorkspaceDto setQuota(Long workspaceId, int quota) {
        Workspace ws = load(workspaceId);
        ws.setMonthlyQueryQuota(quota);
        audit.record(AuditAction.WORKSPACE_UPDATED, workspaceId, "WORKSPACE", workspaceId, Map.of("monthlyQueryQuota", quota));
        return toDto(ws, null);
    }

    public Workspace load(Long id) {
        return workspaces.findById(id).orElseThrow(() -> ApiException.notFound("Workspace"));
    }

    public WorkspaceDto toDto(Workspace ws, WorkspaceRole role) {
        return new WorkspaceDto(ws.getId(), ws.getName(), ws.getDescription(), ws.getOwnerId(), ws.getMonthlyQueryQuota(),
                role, members.countByWorkspaceId(ws.getId()), ws.getCreatedAt());
    }

    private static Instant startOfMonth() {
        return LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
