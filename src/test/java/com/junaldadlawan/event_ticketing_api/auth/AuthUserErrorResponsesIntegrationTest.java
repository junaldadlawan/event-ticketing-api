package com.junaldadlawan.event_ticketing_api.auth;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every failure of the auth and user endpoints answers the right status with the same JSON shape
 * ({@code status} + a readable {@code detail}), never a 500 for something the caller did.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthUserErrorResponsesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final List<UUID> userIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        userIds.forEach(userRepository::deleteById);
    }

    private User user(Role role) {
        return user(role, "err-" + UUID.randomUUID() + "@test.local");
    }

    private User user(Role role, String email) {
        User saved = userRepository.save(User.builder().firstName("Err").lastName("User").email(email)
                .passwordHash(passwordEncoder.encode("Passw0rd!")).role(role).build());
        userIds.add(saved.getId());
        return saved;
    }

    private String token(User user) {
        return "Bearer " + jwtService.generateAccessToken(user);
    }

    private static final String REGISTER = "{\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"birthDate\":\"1990-05-17\","
            + "\"email\":\"%s\",\"passwordHash\":\"Passw0rd!\"}";

    private ResultActions register(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register").contentType("application/json").content(body));
    }

    private ResultActions problem(ResultActions result, int status, String detailContains) throws Exception {
        return result.andExpect(status().is(status)).andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(detailContains)));
    }

    // ---- register: an email that is already taken ----

    @Test
    void register_withAnEmailThatIsTaken_is409_notA500() throws Exception {
        User existing = user(Role.CUSTOMER);

        problem(register(REGISTER.formatted(existing.getEmail())), 409, "An account with this email already exists");
    }

    @Test
    void register_withAnEmailThatDiffersOnlyInCase_is409() throws Exception {
        User existing = user(Role.CUSTOMER, "case-" + UUID.randomUUID() + "@test.local");

        problem(register(REGISTER.formatted(existing.getEmail().toUpperCase())), 409, "already exists");
    }

    @Test
    void register_withTheEmailOfADeletedAccount_is409_becauseTheDatabaseKeyStillHoldsIt() throws Exception {
        User deleted = user(Role.CUSTOMER);
        deleted.markDeleted();
        userRepository.save(deleted);

        problem(register(REGISTER.formatted(deleted.getEmail())), 409, "already exists");
    }

    // ---- request bodies that cannot be read ----

    @Test
    void anUnreadableBody_is400WithAReadableDetail() throws Exception {
        problem(register(""), 400, "missing or is not valid JSON");
        problem(register("{not json"), 400, "missing or is not valid JSON");
        problem(register("[]"), 400, "missing or is not valid JSON");
        problem(mockMvc.perform(post("/api/v1/auth/login").contentType("application/json").content("")), 400, "missing or is not valid JSON");
        problem(mockMvc.perform(post("/api/v1/auth/refresh").contentType("application/json").content("{")), 400, "not valid JSON");
    }

    @Test
    void aBadDate_namesTheField_andANumberIsNotADate() throws Exception {
        // a bare number would otherwise be read as a day count since 1970
        problem(register(REGISTER.formatted("num-" + UUID.randomUUID() + "@test.local").replace("\"1990-05-17\"", "12345")), 400, "birthDate");
        problem(register(REGISTER.formatted("num-" + UUID.randomUUID() + "@test.local").replace("\"1990-05-17\"", "[1990,5,17]")), 400, "birthDate");
        problem(register(REGISTER.formatted("a@test.local").replace("1990-05-17", "17/05/1990")), 400, "birthDate: must be a valid date");
    }

    @Test
    void anUnknownEnumValue_listsTheAllowedOnes() throws Exception {
        User admin = user(Role.ADMIN);
        User target = user(Role.CUSTOMER);

        problem(mockMvc.perform(patch("/api/v1/users/{id}", target.getId()).header("Authorization", token(admin))
                .contentType("application/json").content("{\"role\":\"SUPERUSER\"}")), 400, "role: must be one of [CUSTOMER, ADMIN]");
    }

    @Test
    void aWrongTypeForAField_namesTheField() throws Exception {
        User me = user(Role.CUSTOMER);

        problem(mockMvc.perform(patch("/api/v1/users/me").header("Authorization", token(me))
                .contentType("application/json").content("{\"firstName\":{\"a\":1}}")), 400, "firstName");
    }

    @Test
    void validationFailures_listEveryBadField() throws Exception {
        problem(register("{\"firstName\":\"Jane1\",\"lastName\":\"\",\"birthDate\":\"1990-05-17\",\"email\":\"x[]@gmail.com\",\"passwordHash\":\"\"}"),
                400, "firstName: ");
        problem(register("{\"firstName\":\"Jane1\",\"lastName\":\"\",\"birthDate\":\"1990-05-17\",\"email\":\"x[]@gmail.com\",\"passwordHash\":\"\"}"),
                400, "email: ");
    }

    // ---- wrong method / media type / path ----

    @Test
    void aWrongContentType_is415() throws Exception {
        problem(mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.TEXT_PLAIN).content("hello")), 415, "");
    }

    @Test
    void aWrongMethod_is405_andAnUnknownPath_is404_bothInTheSameShape() throws Exception {
        String token = token(user(Role.CUSTOMER));

        problem(mockMvc.perform(get("/api/v1/auth/login").header("Authorization", token)), 405, "");
        problem(mockMvc.perform(get("/api/v1/does-not-exist").header("Authorization", token)), 404, "");
    }

    // ---- login and refresh ----

    @Test
    void login_withAWrongPasswordOrAnUnknownEmail_isTheSame401() throws Exception {
        User existing = user(Role.CUSTOMER);

        problem(mockMvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"" + existing.getEmail() + "\",\"password\":\"wrong\"}")), 401, "Invalid email or password");
        problem(mockMvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"nobody-" + UUID.randomUUID() + "@test.local\",\"password\":\"wrong\"}")), 401, "Invalid email or password");
    }

    @Test
    void login_withMissingFields_is400() throws Exception {
        problem(mockMvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{}")), 400, "email: ");
        problem(mockMvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{\"email\":\"a@b.co\"}")), 400, "password: ");
    }

    @Test
    void refresh_withAGarbageToken_is401_andWithNoneIs400() throws Exception {
        problem(mockMvc.perform(post("/api/v1/auth/refresh").contentType("application/json").content("{\"refreshToken\":\"not.a.token\"}")),
                401, "Invalid email or password");
        problem(mockMvc.perform(post("/api/v1/auth/refresh").contentType("application/json").content("{}")), 400, "refreshToken: ");
    }

    // ---- who may call what ----

    @Test
    void noTokenOrABadToken_is401_inJson() throws Exception {
        problem(mockMvc.perform(get("/api/v1/users/me")), 401, "Authentication required");
        problem(mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer not.a.jwt")), 401, "Authentication required");
        problem(mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Basic abc")), 401, "Authentication required");
    }

    @Test
    void aCustomerOnAnAdminEndpoint_is403_inJson() throws Exception {
        String token = token(user(Role.CUSTOMER));

        problem(mockMvc.perform(get("/api/v1/users").header("Authorization", token)), 403, "permission");
        problem(mockMvc.perform(patch("/api/v1/users/{id}", UUID.randomUUID()).header("Authorization", token)
                .contentType("application/json").content("{}")), 403, "permission");
        problem(mockMvc.perform(delete("/api/v1/users/{id}", UUID.randomUUID()).header("Authorization", token)), 403, "permission");
    }

    // ---- the user endpoints ----

    @Test
    void changingYourEmailToSomeoneElses_is409_butKeepingYourOwnIsFine() throws Exception {
        User me = user(Role.CUSTOMER);
        User other = user(Role.CUSTOMER);

        problem(mockMvc.perform(patch("/api/v1/users/me").header("Authorization", token(me)).contentType("application/json")
                .content("{\"email\":\"" + other.getEmail() + "\"}")), 409, "already exists");
        problem(mockMvc.perform(patch("/api/v1/users/me").header("Authorization", token(me)).contentType("application/json")
                .content("{\"email\":\"" + other.getEmail().toUpperCase() + "\"}")), 409, "already exists");
        mockMvc.perform(patch("/api/v1/users/me").header("Authorization", token(me)).contentType("application/json")
                .content("{\"email\":\"" + me.getEmail() + "\",\"firstName\":\"Same\"}")).andExpect(status().isOk());
    }

    @Test
    void anAdminChangingAnEmailToSomeoneElses_is409() throws Exception {
        User admin = user(Role.ADMIN);
        User target = user(Role.CUSTOMER);
        User other = user(Role.CUSTOMER);

        problem(mockMvc.perform(patch("/api/v1/users/{id}", target.getId()).header("Authorization", token(admin))
                .contentType("application/json").content("{\"email\":\"" + other.getEmail() + "\"}")), 409, "already exists");
    }

    @Test
    void anUnknownOrMalformedUserId_is404Or400() throws Exception {
        String admin = token(user(Role.ADMIN));
        UUID unknown = UUID.randomUUID();

        problem(mockMvc.perform(patch("/api/v1/users/{id}", unknown).header("Authorization", admin)
                .contentType("application/json").content("{}")), 404, unknown.toString());
        problem(mockMvc.perform(delete("/api/v1/users/{id}", unknown).header("Authorization", admin)), 404, unknown.toString());
        problem(mockMvc.perform(patch("/api/v1/users/{id}", "not-a-uuid").header("Authorization", admin)
                .contentType("application/json").content("{}")), 400, "Invalid value for parameter 'id'");
    }

    @Test
    void aDeletedAccountIsGone_deletingTwiceIs404_andItsOwnProfileIs404() throws Exception {
        User admin = user(Role.ADMIN);
        User target = user(Role.CUSTOMER);
        String targetToken = token(target);

        mockMvc.perform(delete("/api/v1/users/{id}", target.getId()).header("Authorization", token(admin))).andExpect(status().isNoContent());

        problem(mockMvc.perform(delete("/api/v1/users/{id}", target.getId()).header("Authorization", token(admin))), 404, "not found");
        problem(mockMvc.perform(get("/api/v1/users/me").header("Authorization", targetToken)), 404, "not found");
        problem(mockMvc.perform(patch("/api/v1/users/{id}", target.getId()).header("Authorization", token(admin))
                .contentType("application/json").content("{\"firstName\":\"Zed\"}")), 404, "not found");
    }

    @Test
    void changingYourPassword_wrongCurrentIs400_missingFieldsAre400() throws Exception {
        String token = token(user(Role.CUSTOMER));

        problem(mockMvc.perform(patch("/api/v1/users/me/change-password").header("Authorization", token).contentType("application/json")
                .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"Another1!\"}")), 400, "Current password is incorrect");
        problem(mockMvc.perform(patch("/api/v1/users/me/change-password").header("Authorization", token).contentType("application/json")
                .content("{}")), 400, "currentPassword: ");
    }
}
