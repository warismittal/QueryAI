package com.queryai.user;

import com.queryai.audit.AuditAction;
import com.queryai.audit.AuditService;
import com.queryai.common.ApiException;
import com.queryai.config.QueryAiProperties;
import com.queryai.security.CurrentUser;
import com.queryai.user.UserDtos.ChangePasswordRequest;
import com.queryai.user.UserDtos.UpdateProfileRequest;
import com.queryai.user.UserDtos.UserDto;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class UserService {
    private final CurrentUser currentUser;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final QueryAiProperties properties;

    public UserService(CurrentUser currentUser, PasswordEncoder passwordEncoder, AuditService audit, QueryAiProperties properties) {
        this.currentUser = currentUser;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.properties = properties;
    }

    /** On public deployments the shared demo account is read-only so one visitor cannot lock out the next. */
    private void guardSharedDemo(User user) {
        QueryAiProperties.Demo demo = properties.demo();
        if (demo != null && demo.enabled() && demo.publicMode() && user.getEmail().equalsIgnoreCase(demo.email())) {
            throw ApiException.forbidden("The shared demo account cannot be changed. Create your own free account instead.");
        }
    }

    @Transactional(readOnly = true)
    public UserDto me() {
        return UserDto.from(currentUser.require());
    }

    @Transactional
    public UserDto updateProfile(UpdateProfileRequest request) {
        User user = currentUser.require();
        guardSharedDemo(user);
        user.setFullName(request.fullName().trim());
        user.setJobTitle(request.jobTitle() == null || request.jobTitle().isBlank() ? null : request.jobTitle().trim());
        audit.record(AuditAction.PROFILE_UPDATED, null, "USER", user.getId(), Map.of());
        return UserDto.from(user);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = currentUser.require();
        guardSharedDemo(user);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        audit.record(AuditAction.PASSWORD_CHANGED, null, "USER", user.getId(), Map.of());
    }
}
