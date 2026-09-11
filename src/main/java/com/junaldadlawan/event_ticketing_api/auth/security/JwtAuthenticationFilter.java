package com.junaldadlawan.event_ticketing_api.auth.security;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
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

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Claims claims = jwtService.parseClaims(token);
                if ("access".equals(claims.get("type", String.class))) {
                    List<SimpleGrantedAuthority> authorities =
                            List.of(new SimpleGrantedAuthority("ROLE_" + claims.get("role", String.class)));
                    var authentication =
                            new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException e) {
                // Code-reviewer MEDIUM (Phase 10 review): deliberately does
                // NOT call SecurityContextHolder.clearContext() here. This
                // filter and DeviceAuthenticationFilter are registered at
                // the same addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)
                // anchor and only run in this order (JWT first) because of
                // stable-sort insertion order in SecurityConfig - a header
                // that fails JWT parsing (e.g. a device credential, which
                // has a structurally different shape) is exactly the case
                // where a later filter in the chain is expected to
                // authenticate the request instead. Unconditionally
                // clearing here would silently wipe out that later filter's
                // Authentication if the two filters were ever reordered.
            }
        }

        filterChain.doFilter(request, response);
    }
}
