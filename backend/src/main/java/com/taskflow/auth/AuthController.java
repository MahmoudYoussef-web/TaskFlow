package com.taskflow.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.AuthResponse register(@Valid @RequestBody AuthDtos.RegisterRequest req) {
        return auth.register(req);
    }

    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest req) {
        return auth.login(req);
    }

    @PostMapping("/refresh")
    public AuthDtos.AuthResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest req) {
        return auth.refresh(req);
    }

    @GetMapping("/me")
    public AuthDtos.UserDto me(@CurrentUser User user) {
        return AuthDtos.UserDto.from(user);
    }
}
