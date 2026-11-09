package com.queryai.security;

import com.queryai.common.ApiException;
import com.queryai.user.SystemRole;
import com.queryai.user.User;
import com.queryai.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Resolves the authenticated user from the JWT in the security context. */
@Component
public class CurrentUser {
    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public Optional<Long> idIfPresent() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            try {
                return Optional.of(Long.valueOf(token.getToken().getSubject()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public Long id() {
        return idIfPresent().orElseThrow(() -> ApiException.unauthorized("Authentication required"));
    }

    /** Loads the user and verifies the account is still active (tokens of disabled users stop working immediately). */
    public User require() {
        User user = users.findById(id()).orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));
        if (!user.isEnabled()) {
            throw ApiException.forbidden("This account has been disabled");
        }
        return user;
    }

    public boolean isSystemAdmin() {
        return require().getSystemRole() == SystemRole.ADMIN;
    }
}
