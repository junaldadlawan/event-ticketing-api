package com.junaldadlawan.event_ticketing_api.auth.controller;

import com.junaldadlawan.event_ticketing_api.auth.dto.AccessTokenResponse;
import com.junaldadlawan.event_ticketing_api.auth.dto.LoginRequest;
import com.junaldadlawan.event_ticketing_api.auth.dto.LogoutRequest;
import com.junaldadlawan.event_ticketing_api.auth.dto.RefreshRequest;
import com.junaldadlawan.event_ticketing_api.auth.dto.TokenPairResponse;
import com.junaldadlawan.event_ticketing_api.auth.service.AuthService;
import com.junaldadlawan.event_ticketing_api.user.dto.UserRequest;
import com.junaldadlawan.event_ticketing_api.user.dto.UserResponse;
import com.junaldadlawan.event_ticketing_api.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@RequestBody UserRequest request) {
        return UserResponse.from(userService.register(request));
    }

    @PostMapping("/login")
    public TokenPairResponse login(@Valid @RequestBody LoginRequest request) {
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
