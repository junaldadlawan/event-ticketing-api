package com.junaldadlawan.event_ticketing_api.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static ProblemDetail body(ResponseEntity<ProblemDetail> response) {
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    @Test
    void aDuplicateEmailThatReachesTheDatabase_isA409WithAReadableMessage() {
        var ex = new DataIntegrityViolationException("could not execute statement",
                new RuntimeException("ERROR: duplicate key value violates unique constraint \"uq_users_email\""));

        ResponseEntity<ProblemDetail> response = handler.handleDataIntegrity(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body(response).getDetail()).isEqualTo("An account with this email already exists");
    }

    @Test
    void anyOtherIntegrityViolation_isAGeneric409_thatLeaksNoSql() {
        var ex = new DataIntegrityViolationException("x", new RuntimeException("ERROR: insert or update on table \"tickets\" violates foreign key"));

        ResponseEntity<ProblemDetail> response = handler.handleDataIntegrity(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body(response).getDetail()).isEqualTo("The request conflicts with existing data").doesNotContain("tickets");
    }

    @Test
    void anUnexpectedException_isA500WithAGenericMessage_neverTheInternals() {
        ResponseEntity<ProblemDetail> response = handler.handleUnexpected(new IllegalStateException("secret internal detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(body(response).getStatus()).isEqualTo(500);
        assertThat(body(response).getDetail()).doesNotContain("secret").contains("Something went wrong");
    }

    @Test
    void springsOwnWebExceptions_keepTheirStatusAndMessage_insteadOfBecoming500() {
        ResponseEntity<ProblemDetail> notFound = handler.handleUnexpected(new ResponseStatusException(HttpStatus.NOT_FOUND, "No such thing"));
        ResponseEntity<ProblemDetail> wrongMethod = handler.handleUnexpected(
                new HttpRequestMethodNotSupportedException("GET", java.util.List.of("POST")));

        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(body(notFound).getDetail()).isEqualTo("No such thing");
        assertThat(wrongMethod.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(wrongMethod.getHeaders().getAllow()).contains(HttpMethod.POST);
    }

    @Test
    void securityExceptionsThatReachTheController_are403And401_notA500() {
        assertThat(handler.handleAccessDenied(new AccessDeniedException("no")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(handler.handleAuthentication(new BadCredentialsException("no")).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
