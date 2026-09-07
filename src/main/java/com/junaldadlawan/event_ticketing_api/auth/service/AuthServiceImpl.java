package com.junaldadlawan.event_ticketing_api.auth.service;

import com.junaldadlawan.event_ticketing_api.auth.dto.AccessTokenResponse;
import com.junaldadlawan.event_ticketing_api.auth.dto.LoginRequest;
import com.junaldadlawan.event_ticketing_api.auth.dto.LogoutRequest;
import com.junaldadlawan.event_ticketing_api.auth.dto.RefreshRequest;
import com.junaldadlawan.event_ticketing_api.auth.dto.TokenPairResponse;
import com.junaldadlawan.event_ticketing_api.auth.entity.RefreshToken;
import com.junaldadlawan.event_ticketing_api.auth.repository.RefreshTokenRepository;
import com.junaldadlawan.event_ticketing_api.common.exception.InvalidCredentialsException;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    public TokenPairResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String refreshToken = issueRefreshToken(user);

        return new TokenPairResponse(
                jwtService.generateAccessToken(user),
                refreshToken,
                jwtService.getAccessTokenExpirySeconds());
    }

    @Override
    public AccessTokenResponse refresh(RefreshRequest request) {
        Claims claims = parseRefreshClaims(request.refreshToken());

        UUID jti = UUID.fromString(claims.getId());
        RefreshToken storedToken = refreshTokenRepository.findById(jti)
                .orElseThrow(InvalidCredentialsException::new);

        if (storedToken.isRevoked() || storedToken.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidCredentialsException();
        }

        User user = userRepository.findById(UUID.fromString(claims.getSubject()))
                .orElseThrow(InvalidCredentialsException::new);

        return new AccessTokenResponse(
                jwtService.generateAccessToken(user),
                jwtService.getAccessTokenExpirySeconds());
    }

    @Override
    public void logout(LogoutRequest request) {
        Claims claims;
        try {
            claims = parseRefreshClaims(request.refreshToken());
        } catch (InvalidCredentialsException e) {
            return;
        }

        refreshTokenRepository.findById(UUID.fromString(claims.getId()))
                .ifPresent(storedToken -> {
                    storedToken.setRevoked(true);
                    refreshTokenRepository.save(storedToken);
                });
    }

    private Claims parseRefreshClaims(String token) {
        Claims claims;
        try {
            claims = jwtService.parseClaims(token);
        } catch (JwtException e) {
            throw new InvalidCredentialsException();
        }
        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new InvalidCredentialsException();
        }
        return claims;
    }

    private String issueRefreshToken(User user) {
        UUID jti = UUID.randomUUID();
        RefreshToken refreshToken = RefreshToken.builder()
                .id(jti)
                .userId(user.getId())
                .expiresAt(Instant.now().plusMillis(jwtService.getRefreshTokenExpiryMs()))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);
        return jwtService.generateRefreshToken(user, jti);
    }
}
