package com.queryai.user;

import com.queryai.user.UserDtos.AuthResponse;
import com.queryai.user.UserDtos.LoginRequest;
import com.queryai.user.UserDtos.RegisterRequest;
import com.queryai.user.UserDtos.UserDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {
    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an account and a personal workspace; returns a JWT")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange email and password for a JWT")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/guest")
    @Operation(summary = "Sign in to the shared starter workspace (public deployments only)")
    public AuthResponse guest() {
        return authService.guest();
    }

    @GetMapping("/me")
    @Operation(summary = "Currently authenticated user")
    public UserDto me() {
        return userService.me();
    }
}
