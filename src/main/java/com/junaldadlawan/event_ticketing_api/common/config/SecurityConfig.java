package com.junaldadlawan.event_ticketing_api.common.config;

import tools.jackson.databind.ObjectMapper;
import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.checkin.security.DeviceAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;
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
                                                     DeviceAuthenticationFilter deviceAuthenticationFilter,
                                                     ObjectMapper objectMapper) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorizeRequests -> authorizeRequests
                        // PayMongo's webhook sends no login: it is authenticated by its signature header (checked in
                        // PayMongoWebhookController). The controller only exists when payments.gateway=paymongo.
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/paymongo").permitAll()
                        // refresh/logout are public on purpose: a client calls refresh
                        // precisely because its access token has expired, so it has no
                        // valid bearer token to send. Both are guarded by possession of
                        // a signed, stored, unrevoked refresh token in the request body.
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        // BR-ANALYTICS-003: a visitor opening a public event page is recorded with or without an account. It only ever
                        // answers 204 and is rate-limited per client (EventViewServiceImpl); a bearer token, when sent, is still read.
                        .requestMatchers(HttpMethod.POST, "/api/v1/events/*/views").permitAll()
                        // Must precede the broader GET /api/v1/events/** permitAll
                        // matcher below (Spring Security matches in declaration
                        // order, first match wins) - promo-code listing is
                        // owning-organizer-only, not public, per openapi.yaml
                        // (no `security: []` override on listPromoCodes).
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/promo-codes").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/events/*/promo-codes").authenticated()
                        // Edit, pause/resume and delete address the promo code by its own id; the owner/organizer/admin check
                        // (resolved from the code's own event) is in PromoCodeServiceImpl. Never public.
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/promo-codes/*").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/promo-codes/*/status").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/promo-codes/*").authenticated()
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
                        // Same reasoning again: GET /events/{eventId}/check-in-config
                        // (Phase 10) has no stated restriction in openapi.yaml at all
                        // (unlike e.g. refund-policy's explicit visibility note) - read
                        // as "any authenticated caller", which deliberately includes a
                        // ROLE_SCANNER_DEVICE principal (UC-SCAN-04: a scanning client
                        // needs to query its own event's mode/expiry), not just users.
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/check-in-config").authenticated()
                        // Same reasoning again: GET /events/{eventId}/analytics (Phase 14)
                        // is owning-organizer/admin-only per BR-ANALYTICS-001, not public -
                        // must precede the broader GET /api/v1/events/** permitAll matcher
                        // below.
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/analytics", "/api/v1/events/*/analytics/**").authenticated()
                        // BR-EVENT-006 activity history: owner/organizer/admin only (checked in AuditLogServiceImpl)
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/activity").authenticated()
                        // Same reasoning again: GET /events/managed is the management
                        // listing (admin / org owner / organizer, any status) - NOT
                        // public, unlike GET /events - must precede the broader
                        // GET /api/v1/events/** permitAll matcher below. Which events a
                        // given caller sees is enforced in EventServiceImpl.listManagedEvents.
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/managed").authenticated()
                        // BR-REVIEW-001: the review list (organizers) and "my review" need a signed-in caller; the
                        // summary and the recap feed stay public under the permitAll below.
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/reviews", "/api/v1/events/*/reviews/me").authenticated()
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
                        // Same reasoning: GET /users/me/tickets (and its /events filter list) is any signed-in user's own tickets.
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me/tickets", "/api/v1/users/me/tickets/events").authenticated()
                        // Same reasoning: GET /users/me/waitlist-entries (Phase 9)
                        // is any authenticated user's own waitlist history, not an
                        // admin-only user-management endpoint.
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me/waitlist-entries").authenticated()
                        // Same reasoning again: GET /users/me/notifications
                        // (Phase 11) is any authenticated user's own
                        // notification history.
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me/notifications").authenticated()
                        // BR-NOTIFY-002: any signed-in user's own notification on/off switches
                        .requestMatchers("/api/v1/users/me/notification-preferences").authenticated()
                        // GET/PATCH /users/me: any authenticated caller's own
                        // profile - viewing/editing your own name or email is
                        // not an admin-only user-management action, even
                        // though it nests under /users/**. role is never
                        // settable on this path (UserSelfUpdateRequest has no
                        // role field at all), so this can't be used for
                        // self-elevation.
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/users/me").authenticated()
                        // Same reasoning again: PATCH /users/me/change-password is any
                        // authenticated caller changing their OWN password (verified via
                        // their current password in UserServiceImpl#updateSelfPassword),
                        // not an admin-only user-management action.
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/users/me/change-password").authenticated()
                        // Saved cards: every signed-in user (customer or admin) manages their OWN cards, so these must precede the
                        // admin-only /users/** matcher below (the service only ever works on the caller's own rows).
                        .requestMatchers("/api/v1/users/me/payment-methods", "/api/v1/users/me/payment-methods/**").authenticated()
                        .requestMatchers("/api/v1/users", "/api/v1/users/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/v1/organizations", "/api/v1/organizations/**").authenticated()
                        // Announcements and sales: everyone reads them (like the public event list); writing and removing need a
                        // login and are admin-only (checked in PostServiceImpl).
                        .requestMatchers(HttpMethod.GET, "/api/v1/posts").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/posts").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/posts/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/posts/*").authenticated()
                        // Public category list/detail; writes need a login and are
                        // admin-only (CategoryServiceImpl#requireAdmin).
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories", "/api/v1/categories/**").permitAll()
                        .requestMatchers("/api/v1/categories", "/api/v1/categories/**").authenticated()
                        // Spring forwards failed requests (404, 500, ...) to /error, which goes through this chain
                        // again; the JWT filter does not run on that forward, so without this a signed-in caller
                        // would see "Authentication required" instead of the real error. Anonymous callers still
                        // get 401 from anyRequest() on the original request.
                        .requestMatchers("/error").permitAll()
                        // Uploaded images (ticket template backgrounds) are served publicly - they are drawn on
                        // tickets; uploading itself needs a login (anyRequest().authenticated()).
                        .requestMatchers(HttpMethod.GET, "/api/v1/uploads/files/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/uploads").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/venues/**").permitAll()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/venues/**").authenticated()
                        // /api/v1/events/**'s existing GET permitAll already covers
                        // GET /events/{id}/ticket-types and GET /events/{id}/seatmap;
                        // /api/v1/ticket-types/** is a new top-level prefix (like
                        // /api/v1/venues/** in Phase 2) that needs its own matcher.
                        .requestMatchers(HttpMethod.POST, "/api/v1/events/*/ticket-types").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/events/*/ticket-types/order").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/ticket-types/**").permitAll()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/ticket-types/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/ticket-types/**").authenticated()
                        // Pause / resume selling one ticket type; owner / organizer / admin is enforced in TicketTypeServiceImpl.
                        .requestMatchers(HttpMethod.PUT, "/api/v1/ticket-types/*/sales-status").authenticated()
                        // /api/v1/ticket-templates/** (Phase 6b) is a new top-level prefix
                        // (PATCH-only, no GET-single/DELETE per openapi.yaml) - always
                        // authenticated, no public GET exists for it at all.
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/ticket-templates/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/ticket-templates/**").authenticated()
                        // No public GET exists for carts at all - buyer-only,
                        // always authenticated (Phase 5a).
                        .requestMatchers("/api/v1/carts", "/api/v1/carts/**").authenticated()
                        // Phase 12 (BR-ADMIN-003): no public GET exists for
                        // disputes either - raiser/admin visibility and
                        // admin-only list/update are enforced in
                        // DisputeServiceImpl, same idiom as carts/organizations
                        // above.
                        .requestMatchers("/api/v1/disputes", "/api/v1/disputes/**").authenticated()
                        // Phase 12 (BR-ADMIN-002): admin-only moderation-action
                        // log - unlike disputes above, this one IS restricted at
                        // the HTTP layer (hasRole) since every operation under
                        // this prefix is unconditionally admin-only, with no
                        // raiser/owner visibility branch to justify pushing the
                        // check down into the service layer alone.
                        .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        // Phase 13 (BR-NFR-005): admin-only audit log, same
                        // unconditional hasRole restriction as the moderation
                        // action log above - not nested under /admin/** per
                        // openapi.yaml's literal /audit-log path, so it needs
                        // its own matcher.
                        .requestMatchers(HttpMethod.GET, "/api/v1/audit-log").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        // Phase 14 (BR-ANALYTICS-002): platform-wide analytics, unconditionally
                        // admin-only, no per-resource owner/organizer branch - same idiom as
                        // audit-log above.
                        .requestMatchers(HttpMethod.GET, "/api/v1/analytics/platform").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        // the admin's money summary: same unconditional admin-only rule
                        .requestMatchers(HttpMethod.GET, "/api/v1/analytics/platform/finance").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        // Phase 10: openapi.yaml's `security: [deviceAuth: []]` override
                        // on these three - device-credential-only, a user JWT must NOT
                        // work here (a user could otherwise validate/scan tickets it has
                        // no business touching). ROLE_SCANNER_DEVICE is granted only by
                        // DeviceAuthenticationFilter, never by JwtAuthenticationFilter.
                        .requestMatchers(HttpMethod.GET, "/api/v1/scanner-devices/*/dataset").hasRole("SCANNER_DEVICE")
                        // moving a device: the device itself (deviceAuth) or a signed-in user; who may is checked in the service
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/scanner-devices/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/check-in/validate", "/api/v1/check-in/fallback-scans").hasRole("SCANNER_DEVICE")
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
                // Both filters anchor at the same UsernamePasswordAuthenticationFilter
                // offset, so their relative order is whichever this stable
                // sort's registration order happens to be (JWT first) - see
                // JwtAuthenticationFilter's catch block for why it's safe
                // either way (it never clobbers an Authentication a later
                // filter set).
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(deviceAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * CORS for browser clients served from another origin (the web app on its own domain, e.g. CloudFront, in front of the ALB / ECS service).
     * Only the origins listed in {@code app.cors.allowed-origins} (comma-separated, exact scheme + host + port, never {@code *}) may call
     * {@code /api/v1/**}; with none listed no cross-origin call is allowed (the safe default for a same-origin deployment). Authentication is
     * the JWT in the Authorization header, never a cookie, so credentials are not allowed. Preflight (OPTIONS) is answered by the CORS filter
     * before authentication. The ticket designer also reads uploaded images through a canvas, which needs these headers on
     * {@code /api/v1/uploads/files/**} (covered by the same pattern). Webhooks are server to server and need none.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:5173}") List<String> allowedOrigins) {
        List<String> origins = allowedOrigins.stream().map(String::trim).filter(o -> !o.isEmpty()).toList();
        if (origins.stream().anyMatch(o -> o.contains("*"))) {
            throw new IllegalStateException("app.cors.allowed-origins must list explicit origins (https://app.example.com), not a wildcard");
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Idempotency-Key", "X-Request-Id"));
        config.setExposedHeaders(List.of("X-Request-Id", "Location", "Retry-After", "Content-Disposition"));
        config.setAllowCredentials(false);
        config.setMaxAge(java.time.Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
