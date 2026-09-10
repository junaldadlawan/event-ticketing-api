package com.junaldadlawan.event_ticketing_api.common.config;

import tools.jackson.databind.ObjectMapper;
import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.ApplicationContext;
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
import org.springframework.security.web.access.expression.DefaultHttpSecurityExpressionHandler;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                     JwtAuthenticationFilter jwtAuthenticationFilter,
                                                     ObjectMapper objectMapper,
                                                     ApplicationContext applicationContext) throws Exception {
        // WebExpressionAuthorizationManager's default expression handler isn't
        // ApplicationContext-aware out of the box, so a "@beanName.method()"
        // SpEL reference (used below) needs an explicit handler with the
        // context set, or it fails at request time with "no bean resolver
        // registered" instead of evaluating the expression.
        DefaultHttpSecurityExpressionHandler expressionHandler = new DefaultHttpSecurityExpressionHandler();
        expressionHandler.setApplicationContext(applicationContext);
        WebExpressionAuthorizationManager ownerOrOrganizerOrAdmin = new WebExpressionAuthorizationManager(
                "hasRole('ADMIN') or @organizationAccessGuard.isOwnerOrOrganizerAnywhere(authentication)");
        ownerOrOrganizerOrAdmin.setExpressionHandler(expressionHandler);

        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorizeRequests -> authorizeRequests
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/events", "/api/v1/events/**").permitAll()
                        // Can only check "owner/organizer of *some* organization," not "of
                        // *this event's* organization" — Event.organizationId is still an
                        // unwired placeholder (Phase 3's job to tighten this).
                        .requestMatchers(HttpMethod.POST, "/api/v1/events").access(ownerOrOrganizerOrAdmin)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/events/**").access(ownerOrOrganizerOrAdmin)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/events/**").access(ownerOrOrganizerOrAdmin)
                        .requestMatchers("/api/v1/users", "/api/v1/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/organizations", "/api/v1/organizations/**").authenticated()
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
