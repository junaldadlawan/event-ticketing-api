package com.junaldadlawan.event_ticketing_api.common.config;

import tools.jackson.databind.ObjectMapper;
import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                     JwtAuthenticationFilter jwtAuthenticationFilter,
                                                     ObjectMapper objectMapper) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorizeRequests -> authorizeRequests
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        // Must precede the broader GET /api/v1/events/** permitAll
                        // matcher below (Spring Security matches in declaration
                        // order, first match wins) - promo-code listing is
                        // owning-organizer-only, not public, per openapi.yaml
                        // (no `security: []` override on listPromoCodes).
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/promo-codes").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/events/*/promo-codes").authenticated()
                        // Same reasoning: GET /events/{eventId}/orders (Phase 6a) is
                        // owning-organizer/admin-only, not public - must precede the
                        // broader GET /api/v1/events/** permitAll matcher below.
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/orders").authenticated()
                        // Same reasoning again: GET/POST /events/{eventId}/ticket-templates
                        // (Phase 6b) is owning-organizer-only per openapi.yaml's
                        // listTicketTemplates summary, not public - must precede the
                        // broader GET /api/v1/events/** permitAll matcher below.
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/ticket-templates").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/events/*/ticket-templates").authenticated()
                        // Same reasoning again: GET /events/{eventId}/refund-policy
                        // (Phase 8) is organizer/admin/buyer-with-an-order-on-it only
                        // per openapi.yaml's getRefundPolicy summary, NOT public
                        // (unlike GET /events/{eventId}/resale-policy) - must precede
                        // the broader GET /api/v1/events/** permitAll matcher below.
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/refund-policy").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/events", "/api/v1/events/**").permitAll()
                        // Precise per-event, per-organization authorization now lives in
                        // EventServiceImpl (owner/organizer of the event's own org, or
                        // admin) — mirrors how organization/venue already do their own
                        // service-layer checks, so the coarse "owner/organizer of *some*
                        // org" SpEL check from Phase 1 is no longer needed here.
                        .requestMatchers(HttpMethod.POST, "/api/v1/events", "/api/v1/events/*/publish", "/api/v1/events/*/cancel").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/events/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/events/**").authenticated()
                        // Must precede the broad /api/v1/users/** ADMIN-only matcher
                        // below - GET /users/me/orders (Phase 6a) is any authenticated
                        // buyer's own order history, not an admin-only user-management
                        // endpoint, even though its path nests under /users/**.
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me/orders").authenticated()
                        // Same reasoning: GET /users/me/waitlist-entries (Phase 9)
                        // is any authenticated user's own waitlist history, not an
                        // admin-only user-management endpoint.
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me/waitlist-entries").authenticated()
                        .requestMatchers("/api/v1/users", "/api/v1/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/organizations", "/api/v1/organizations/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/venues/**").permitAll()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/venues/**").authenticated()
                        // /api/v1/events/**'s existing GET permitAll already covers
                        // GET /events/{id}/ticket-types and GET /events/{id}/seatmap;
                        // /api/v1/ticket-types/** is a new top-level prefix (like
                        // /api/v1/venues/** in Phase 2) that needs its own matcher.
                        .requestMatchers(HttpMethod.POST, "/api/v1/events/*/ticket-types").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/ticket-types/**").permitAll()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/ticket-types/**").authenticated()
                        // /api/v1/ticket-templates/** (Phase 6b) is a new top-level prefix
                        // (PATCH-only, no GET-single/DELETE per openapi.yaml) - always
                        // authenticated, no public GET exists for it at all.
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/ticket-templates/**").authenticated()
                        // No public GET exists for carts at all - buyer-only,
                        // always authenticated (Phase 5a).
                        .requestMatchers("/api/v1/carts", "/api/v1/carts/**").authenticated()
                        // /api/v1/orders/** and /api/v1/tickets/** (Phase 6a) need no
                        // explicit matcher of their own - neither prefix is touched by
                        // any permitAll/role-restricted matcher above, so both already
                        // fall through to the anyRequest().authenticated() below; the
                        // real buyer/organizer/admin visibility check happens in
                        // OrderServiceImpl/TicketServiceImpl.
                        .anyRequest().authenticated())
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                                    HttpStatus.UNAUTHORIZED, "Authentication required");
                            objectMapper.writeValue(response.getOutputStream(), problem);
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                                    HttpStatus.FORBIDDEN, "You do not have permission to access this resource");
                            objectMapper.writeValue(response.getOutputStream(), problem);
                        }))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
