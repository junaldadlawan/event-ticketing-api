package com.junaldadlawan.event_ticketing_api.auth.controller;

import com.junaldadlawan.event_ticketing_api.auth.dto.AccessTokenResponse;
import com.junaldadlawan.event_ticketing_api.auth.dto.LoginRequest;
import com.junaldadlawan.event_ticketing_api.auth.dto.LogoutRequest;
import com.junaldadlawan.event_ticketing_api.auth.dto.RefreshRequest;
import com.junaldadlawan.event_ticketing_api.auth.dto.TokenPairResponse;
import com.junaldadlawan.event_ticketing_api.auth.service.AuthService;
import com.junaldadlawan.event_ticketing_api.common.exception.TooManyRequestsException;
import com.junaldadlawan.event_ticketing_api.common.ratelimit.RateLimiter;
import com.junaldadlawan.event_ticketing_api.user.dto.RegisterRequest;
import com.junaldadlawan.event_ticketing_api.user.dto.UserResponse;
import com.junaldadlawan.event_ticketing_api.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final RateLimiter rateLimiter;

    // BR-NFR-004: cap login attempts per caller IP to deter credential stuffing (a shared IP is deliberate - one source can't hammer many accounts).
    @Value("${app.ratelimit.login-per-minute:10}")
    private int loginPerMinute;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.from(userService.register(request));
    }

    @PostMapping("/login")
    public TokenPairResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        if (!rateLimiter.tryAcquire("login", RateLimiter.clientIp(http), loginPerMinute)) {
            throw new TooManyRequestsException("Too many login attempts. Try again in a minute.");
        }
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AccessTokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }
}
