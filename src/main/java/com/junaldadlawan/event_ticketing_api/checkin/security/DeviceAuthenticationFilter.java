package com.junaldadlawan.event_ticketing_api.checkin.security;

import com.junaldadlawan.event_ticketing_api.checkin.entity.ScannerDevice;
import com.junaldadlawan.event_ticketing_api.checkin.enums.ScannerDeviceStatus;
import com.junaldadlawan.event_ticketing_api.checkin.repository.ScannerDeviceRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Parallel to {@code JwtAuthenticationFilter}, not a replacement - both run
 * on every request (registered alongside each other in {@code
 * SecurityConfig}), each independently trying its own credential format
 * against the same {@code Authorization: Bearer <token>} header. A
 * {@code ScannerDevice} credential (see {@code ScannerDeviceCredentialService})
 * is structurally a {@code UUID.signature} string, which never parses as a
 * valid JWT and vice versa, so the two never collide in practice.
 * <p>
 * Sets {@code ROLE_SCANNER_DEVICE} only - deliberately NOT any user role,
 * so a device credential can never satisfy a user-scoped check ({@code
 * OrganizationAccessGuard.currentUserId()} et al. are simply never called
 * on the device-only endpoints this authenticates). {@code
 * authentication.getName()} is the verified device id.
 * <p>
 * Revocation is live, not credential-based: even a structurally valid,
 * correctly-signed credential for a {@code REVOKED} device is rejected
 * here (BR-CHECKIN: "revoke a device's credential immediately") - there's
 * no separate token-blacklist to maintain.
 */
@Component
@RequiredArgsConstructor
public class DeviceAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ScannerDeviceCredentialService credentialService;
    private final ScannerDeviceRepository scannerDeviceRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            Optional<UUID> deviceId = credentialService.verify(token);
            if (deviceId.isPresent()) {
                Optional<ScannerDevice> device = scannerDeviceRepository.findById(deviceId.get());
                if (device.isPresent() && device.get().getStatus() == ScannerDeviceStatus.ACTIVE) {
                    List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_SCANNER_DEVICE"));
                    var authentication =
                            new UsernamePasswordAuthenticationToken(deviceId.get().toString(), null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
