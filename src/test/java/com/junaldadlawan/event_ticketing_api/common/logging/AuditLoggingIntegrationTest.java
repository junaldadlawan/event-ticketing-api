package com.junaldadlawan.event_ticketing_api.common.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.junaldadlawan.event_ticketing_api.auth.repository.RefreshTokenRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.AccountStatus;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end through the real filter chain: business transactions emit
 * exactly one audit-log line each, correlated to the request by the same id
 * the client gets back in the {@code X-Request-Id} header, and never contain
 * passwords or tokens.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuditLoggingIntegrationTest {

    private static final String PASSWORD = "Passw0rd!-audit";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Logger auditLogger;
    private Logger rootLogger;
    private ListAppender<ILoggingEvent> appender;
    /** Everything logged by anything during the test, to prove no secret leaks anywhere. */
    private ListAppender<ILoggingEvent> allLogs;
    private Level originalLevel;
    private User user;
    private final List<UUID> extraUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .name("Audit Test")
                .email("audit-" + UUID.randomUUID() + "@test.local")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .role(Role.CUSTOMER)
                .build());
        auditLogger = (Logger) LoggerFactory.getLogger(BusinessAuditLogger.LOGGER_NAME);
        originalLevel = auditLogger.getLevel();
        auditLogger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        allLogs = new ListAppender<>();
        allLogs.start();
        rootLogger.addAppender(allLogs);
    }

    @AfterEach
    void tearDown() {
        auditLogger.detachAppender(appender);
        rootLogger.detachAppender(allLogs);
        auditLogger.setLevel(originalLevel);
        List<UUID> userIds = new ArrayList<>(extraUserIds);
        userIds.add(user.getId());
        refreshTokenRepository.findAll().stream()
                .filter(token -> userIds.contains(token.getUserId()))
                .forEach(refreshTokenRepository::delete);
        userIds.forEach(userRepository::deleteById);
    }

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
    }

    private ILoggingEvent onlyAuditLine(String action) {
        List<ILoggingEvent> matching = appender.list.stream()
                .filter(event -> action.equals(event.getMDCPropertyMap().get("action")))
                .toList();
        assertThat(matching).as("audit lines for " + action).hasSize(1);
        return matching.get(0);
    }

    @Test
    void successfulLogin_writesOneInfoAuditLine_correlatedToTheResponseRequestId() throws Exception {
        MvcResult result = login(user.getEmail(), PASSWORD);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        ILoggingEvent line = onlyAuditLine("auth.login");
        assertThat(line.getLevel()).isEqualTo(Level.INFO);
        assertThat(line.getMDCPropertyMap())
                .containsEntry("outcome", "SUCCESS")
                .containsEntry("actorId", user.getId().toString())
                .containsEntry("log_type", "audit");
        String requestId = result.getResponse().getHeader(RequestIdFilter.HEADER);
        assertThat(requestId).isNotBlank();
        assertThat(line.getMDCPropertyMap()).containsEntry("requestId", requestId);
        assertNoSecrets(result.getResponse().getContentAsString());
    }

    @Test
    void wrongPassword_writesOneWarnAuditLine_andNeverTheAttemptedPassword() throws Exception {
        MvcResult result = login(user.getEmail(), "definitely-not-the-password");
        assertThat(result.getResponse().getStatus()).isEqualTo(401);

        ILoggingEvent line = onlyAuditLine("auth.login");
        assertThat(line.getLevel()).isEqualTo(Level.WARN);
        assertThat(line.getMDCPropertyMap()).containsEntry("outcome", "FAILURE")
                .containsEntry("actorId", user.getId().toString());
        assertThat(line.getFormattedMessage()).contains("wrong password")
                .doesNotContain("definitely-not-the-password");
    }

    @Test
    void unknownEmail_isLoggedMasked_notInFull() throws Exception {
        String unknown = "nobody-" + UUID.randomUUID() + "@test.local";
        MvcResult result = login(unknown, "whatever-password");
        assertThat(result.getResponse().getStatus()).isEqualTo(401);

        ILoggingEvent line = onlyAuditLine("auth.login");
        assertThat(line.getLevel()).isEqualTo(Level.WARN);
        assertThat(line.getFormattedMessage()).contains("n***@test.local")
                .doesNotContain(unknown).doesNotContain("whatever-password");
        assertThat(line.getMDCPropertyMap()).containsEntry("actorId", "anonymous");
    }

    @Test
    void suspendedAccount_logsAFailureWithReason() throws Exception {
        user.setAccountStatus(AccountStatus.SUSPENDED);
        userRepository.save(user);

        MvcResult result = login(user.getEmail(), PASSWORD);
        assertThat(result.getResponse().getStatus()).isEqualTo(403);

        ILoggingEvent line = onlyAuditLine("auth.login");
        assertThat(line.getMDCPropertyMap()).containsEntry("outcome", "FAILURE");
        assertThat(line.getFormattedMessage()).contains("account suspended");
    }

    @Test
    void registration_isAudited_withTheRequestedRole() throws Exception {
        String email = "registered-" + UUID.randomUUID() + "@test.local";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("{\"name\":\"Reg Test\",\"email\":\"" + email + "\",\"passwordHash\":\"" + PASSWORD
                                + "\",\"role\":\"CUSTOMER\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        userRepository.findByEmail(email).ifPresent(created -> extraUserIds.add(created.getId()));

        ILoggingEvent line = onlyAuditLine("user.registered");
        assertThat(line.getFormattedMessage()).contains("role=CUSTOMER").doesNotContain(PASSWORD);
        assertThat(line.getMDCPropertyMap())
                .containsEntry("requestId", result.getResponse().getHeader(RequestIdFilter.HEADER));
    }

    /** Everything logged during the test: the root logger's lines plus the audit logger's own (additivity=false keeps those off root). */
    private List<ILoggingEvent> everythingLogged() {
        List<ILoggingEvent> all = new ArrayList<>(allLogs.list);
        all.addAll(appender.list);
        return all;
    }

    private void assertNoSecrets(String responseBody) {
        for (ILoggingEvent event : everythingLogged()) {
            assertThat(event.getFormattedMessage()).doesNotContain(PASSWORD);
            assertThat(event.getMDCPropertyMap().values()).noneMatch(value -> value.contains(PASSWORD));
        }
        assertThat(responseBody).contains("accessToken");
        String token = responseBody.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
        for (ILoggingEvent event : everythingLogged()) {
            assertThat(event.getFormattedMessage()).doesNotContain(token);
        }
    }

    /** Spring's own web debug logging used to print the request body object, password included. */
    @Test
    void noLogLine_anywhere_containsTheLoginPassword_orTheAttemptedOne() throws Exception {
        login(user.getEmail(), PASSWORD);
        login(user.getEmail(), "attempted-wrong-password-9");

        assertThat(allLogs.list).isNotEmpty();
        for (ILoggingEvent event : everythingLogged()) {
            assertThat(event.getFormattedMessage())
                    .doesNotContain(PASSWORD)
                    .doesNotContain("attempted-wrong-password-9");
        }
    }

    /** With the local split, audit lines must not also land in the application log. */
    @Test
    void auditLines_doNotPropagateToTheRootLogger_soTheyStayOutOfTheApplicationFile() throws Exception {
        login(user.getEmail(), PASSWORD);

        assertThat(appender.list).isNotEmpty();
        assertThat(allLogs.list).noneMatch(event -> BusinessAuditLogger.LOGGER_NAME.equals(event.getLoggerName()));
    }
}
