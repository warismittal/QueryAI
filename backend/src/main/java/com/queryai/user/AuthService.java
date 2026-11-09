package com.queryai.user;

import com.queryai.audit.AuditAction;
import com.queryai.audit.AuditService;
import com.queryai.common.ApiException;
import com.queryai.config.QueryAiProperties;
import com.queryai.security.JwtService;
import com.queryai.user.UserDtos.AuthResponse;
import com.queryai.user.UserDtos.LoginRequest;
import com.queryai.user.UserDtos.RegisterRequest;
import com.queryai.user.UserDtos.UserDto;
import com.queryai.workspace.WorkspaceService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
public class AuthService {

    /** Used to keep login timing similar for unknown e-mails (prevents user enumeration by timing). */
    private final String dummyHash;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final WorkspaceService workspaceService;
    private final AuditService audit;
    private final QueryAiProperties properties;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       WorkspaceService workspaceService, AuditService audit, QueryAiProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.workspaceService = workspaceService;
        this.audit = audit;
        this.properties = properties;
        this.dummyHash = passwordEncoder.encode("timing-equalisation-placeholder");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User user = new User();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        // The very first account bootstraps the platform and becomes the system administrator.
        user.setSystemRole(users.count() == 0 ? SystemRole.ADMIN : SystemRole.USER);
        user.setLastLoginAt(Instant.now());
        users.save(user);

        workspaceService.createPersonalWorkspace(user);
        audit.recordFor(user, AuditAction.USER_REGISTERED, null, "USER", user.getId(), Map.of("email", email), true);
        return issue(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        User user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            audit.recordAnonymous(AuditAction.LOGIN_FAILED, Map.of("email", email, "reason", "unknown_email"));
            throw new BadCredentialsException("Invalid credentials");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            audit.recordFor(user, AuditAction.LOGIN_FAILED, null, "USER", user.getId(), Map.of("reason", "bad_password"), false);
            throw new BadCredentialsException("Invalid credentials");
        }
        if (!user.isEnabled()) {
            audit.recordFor(user, AuditAction.LOGIN_FAILED, null, "USER", user.getId(), Map.of("reason", "disabled"), false);
            throw new DisabledException("Account disabled");
        }
        user.setLastLoginAt(Instant.now());
        audit.recordFor(user, AuditAction.LOGIN, null, "USER", user.getId(), Map.of(), true);
        return issue(user);
    }

    /**
     * One-click access to the shared starter workspace on public deployments. The account is a regular analyst with
     * personal data masked; its password is never exposed to the browser.
     */
    @Transactional
    public AuthResponse guest() {
        QueryAiProperties.Demo shared = properties.demo();
        if (shared == null || !shared.enabled() || !shared.publicMode()) {
            throw ApiException.notFound("Guest access");
        }
        User user = users.findByEmailIgnoreCase(shared.email()).filter(User::isEnabled)
                .orElseThrow(() -> ApiException.notFound("Guest access"));
        user.setLastLoginAt(Instant.now());
        audit.recordFor(user, AuditAction.LOGIN, null, "USER", user.getId(), Map.of("method", "guest"), true);
        return issue(user);
    }

    private AuthResponse issue(User user) {
        JwtService.IssuedToken token = jwtService.issue(user);
        return new AuthResponse(token.value(), token.expiresAt(), UserDto.from(user));
    }
}
