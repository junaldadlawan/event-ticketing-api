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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registration (and the profile updates) only accept plain characters, the name comes as first / middle / last name,
 * and a new account is always a CUSTOMER.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegistrationValidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ObjectMapper objectMapper;

    private final List<String> emails = new ArrayList<>();
    private final List<UUID> userIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        emails.forEach(email -> userRepository.findByEmail(email).ifPresent(user -> userRepository.deleteById(user.getId())));
        userIds.forEach(userRepository::deleteById);
    }

    private String uniqueEmail() {
        return "reg-" + UUID.randomUUID() + "@test.local";
    }

    private MvcResult send(Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content(objectMapper.writeValueAsString(body))).andReturn();
    }

    private Map<String, Object> body(String first, String middle, String last, String email) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstName", first);
        if (middle != null) {
            body.put("middleName", middle);
        }
        body.put("lastName", last);
        body.put("birthDate", "1990-05-17");
        body.put("email", email);
        body.put("passwordHash", "Passw0rd!");
        return body;
    }

    /** Registers and returns the HTTP status; only what this test really created (201) is cleaned up afterwards. */
    private int register(String first, String middle, String last, String email) throws Exception {
        int status = send(body(first, middle, last, email)).getResponse().getStatus();
        if (status == 201) {
            emails.add(email);
        }
        return status;
    }

    // ---- names ----

    @Test
    void names_withLettersSpacesDotsApostrophesAndHyphens_areAccepted() throws Exception {
        for (String name : new String[] {"Jane", "José Álvarez", "O'Brien", "O’Brien", "Mary-Jane", "Dr. Lee", "李雷", "Zoë"}) {
            assertThat(register(name, null, "Doe", uniqueEmail())).as("first " + name).isEqualTo(201);
            assertThat(register("Jane", null, name, uniqueEmail())).as("last " + name).isEqualTo(201);
            assertThat(register("Jane", name, "Doe", uniqueEmail())).as("middle " + name).isEqualTo(201);
        }
    }

    @Test
    void names_withAnyOtherCharacter_areRejected_inEveryPart() throws Exception {
        for (String name : new String[] {"Jane1", "42", "mark[]", "<b>Jane</b>", "Jane_Doe", "Jane@Doe", "Jane 😀", "(Jane)",
                "Jane;Doe", "Jane/Doe", "-Jane", " Jane", ".Jane", "'Jane"}) {
            assertThat(register(name, null, "Doe", uniqueEmail())).as("first [" + name + "]").isEqualTo(400);
            assertThat(register("Jane", null, name, uniqueEmail())).as("last [" + name + "]").isEqualTo(400);
            assertThat(register("Jane", name, "Doe", uniqueEmail())).as("middle [" + name + "]").isEqualTo(400);
        }
    }

    @Test
    void firstAndLastNameAreRequired_theMiddleNameIsNot() throws Exception {
        for (String blank : new String[] {null, "", "   "}) {
            assertThat(register(blank, null, "Doe", uniqueEmail())).as("first [" + blank + "]").isEqualTo(400);
            assertThat(register("Jane", null, blank, uniqueEmail())).as("last [" + blank + "]").isEqualTo(400);
        }
        assertThat(register("Jane", null, "Doe", uniqueEmail())).isEqualTo(201);
        assertThat(register("Jane", "", "Doe", uniqueEmail())).isEqualTo(201);
    }

    @Test
    void theResponseCarriesTheSeparateNames_andNoFullName() throws Exception {
        String email = uniqueEmail();

        MvcResult result = send(body("Jane", "Quinn", "Doe", email));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        emails.add(email);
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("firstName").asText()).isEqualTo("Jane");
        assertThat(json.get("middleName").asText()).isEqualTo("Quinn");
        assertThat(json.get("lastName").asText()).isEqualTo("Doe");
        assertThat(json.has("name")).isFalse();
        assertThat(userRepository.findByEmail(email)).get().satisfies(user -> assertThat(user.fullName()).isEqualTo("Jane Quinn Doe"));
    }

    @Test
    void anEmptyMiddleName_isStoredAsNone() throws Exception {
        String email = uniqueEmail();

        assertThat(register("Jane", "", "Doe", email)).isEqualTo(201);

        assertThat(userRepository.findByEmail(email)).get().satisfies(user -> assertThat(user.getMiddleName()).isNull());
    }

    @Test
    void theOldSingleNameField_isNotEnough() throws Exception {
        Map<String, Object> old = new LinkedHashMap<>();
        old.put("name", "Jane Doe");
        old.put("email", uniqueEmail());
        old.put("passwordHash", "Passw0rd!");

        assertThat(send(old).getResponse().getStatus()).isEqualTo(400);
    }

    // ---- birth date and phone number ----

    @Test
    void theBirthDateIsRequired_inIsoFormat_inThePast_andNotMoreThan120YearsAgo() throws Exception {
        String today = java.time.LocalDate.now().toString();
        String tomorrow = java.time.LocalDate.now().plusDays(1).toString();
        String tooOld = java.time.LocalDate.now().minusYears(121).toString();
        String oldestOk = java.time.LocalDate.now().minusYears(120).toString();
        for (String bad : new String[] {today, tomorrow, "2999-01-01", tooOld, "1990-13-40", "17/05/1990", "May 17 1990", "not a date", "1990-5-7", ""}) {
            Map<String, Object> body = body("Jane", null, "Doe", uniqueEmail());
            body.put("birthDate", bad);
            assertThat(send(body).getResponse().getStatus()).as("[" + bad + "]").isEqualTo(400);
        }
        Map<String, Object> missing = body("Jane", null, "Doe", uniqueEmail());
        missing.remove("birthDate");
        assertThat(send(missing).getResponse().getStatus()).isEqualTo(400);
        Map<String, Object> nullDate = body("Jane", null, "Doe", uniqueEmail());
        nullDate.put("birthDate", null);
        assertThat(send(nullDate).getResponse().getStatus()).isEqualTo(400);
        for (String good : new String[] {"1990-05-17", "2000-02-29", oldestOk, java.time.LocalDate.now().minusDays(1).toString()}) {
            String email = uniqueEmail();
            Map<String, Object> body = body("Jane", null, "Doe", email);
            body.put("birthDate", good);
            assertThat(send(body).getResponse().getStatus()).as(good).isEqualTo(201);
            emails.add(email);
        }
    }

    @Test
    void theResponseCarriesTheBirthDate_andThePhoneNumberWhenGiven() throws Exception {
        String email = uniqueEmail();
        emails.add(email);
        Map<String, Object> body = body("Jane", null, "Doe", email);
        body.put("phoneNumber", "+639171234567");

        MvcResult result = send(body);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("birthDate").asText()).isEqualTo("1990-05-17");
        assertThat(json.get("phoneNumber").asText()).isEqualTo("+639171234567");
        assertThat(userRepository.findByEmail(email)).get().satisfies(user -> {
            assertThat(user.getBirthDate()).isEqualTo(java.time.LocalDate.of(1990, 5, 17));
            assertThat(user.getPhoneNumber()).isEqualTo("+639171234567");
        });
    }

    @Test
    void thePhoneNumberIsOptional_andEmptyOrMissingMeansNone() throws Exception {
        String withoutField = uniqueEmail();
        String empty = uniqueEmail();
        emails.add(withoutField);
        emails.add(empty);
        Map<String, Object> emptyBody = body("Jane", null, "Doe", empty);
        emptyBody.put("phoneNumber", "");

        assertThat(register("Jane", null, "Doe", withoutField)).isEqualTo(201);
        assertThat(send(emptyBody).getResponse().getStatus()).isEqualTo(201);

        assertThat(userRepository.findByEmail(withoutField)).get().satisfies(user -> assertThat(user.getPhoneNumber()).isNull());
        assertThat(userRepository.findByEmail(empty)).get().satisfies(user -> assertThat(user.getPhoneNumber()).isNull());
    }

    @Test
    void phoneNumbers_inInternationalFormat_areAccepted_andAnythingElseIsRejected() throws Exception {
        for (String good : new String[] {"+639171234567", "+14155552671", "+442071838750", "+12345678", "+123456789012345"}) {
            String email = uniqueEmail();
            Map<String, Object> body = body("Jane", null, "Doe", email);
            body.put("phoneNumber", good);
            assertThat(send(body).getResponse().getStatus()).as(good).isEqualTo(201);
            emails.add(email);
        }
        for (String bad : new String[] {"09171234567", "639171234567", "+0639171234567", "+63 917 123 4567", "+63-917-123-4567", "(0917) 123 4567",
                "+63917123456a", "+1234567", "+1234567890123456", "++639171234567", "+", "call me", "+63917123456\n", "<b>+639171234567</b>", "+\uff16\uff13\uff19\uff11\uff17\uff11\uff12\uff13\uff14\uff15"}) {
            Map<String, Object> body = body("Jane", null, "Doe", uniqueEmail());
            body.put("phoneNumber", bad);
            assertThat(send(body).getResponse().getStatus()).as("[" + bad + "]").isEqualTo(400);
        }
    }

    @Test
    void updatingYourOwnBirthDateAndPhone_followsTheSameRules_andAnEmptyPhoneRemovesIt() throws Exception {
        String token = signedInUser();

        for (String body : new String[] {"{\"birthDate\":\"2999-01-01\"}", "{\"birthDate\":\"yesterday\"}", "{\"phoneNumber\":\"0917 123 4567\"}",
                "{\"phoneNumber\":\"+63917\"}"}) {
            mockMvc.perform(patch("/api/v1/users/me").header("Authorization", token).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        MvcResult set = mockMvc.perform(patch("/api/v1/users/me").header("Authorization", token).contentType("application/json")
                        .content("{\"birthDate\":\"1985-12-01\",\"phoneNumber\":\"+14155552671\"}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode json = objectMapper.readTree(set.getResponse().getContentAsString());
        assertThat(json.get("birthDate").asText()).isEqualTo("1985-12-01");
        assertThat(json.get("phoneNumber").asText()).isEqualTo("+14155552671");

        // leaving a field out keeps it; "" removes the phone number
        MvcResult kept = mockMvc.perform(patch("/api/v1/users/me").header("Authorization", token).contentType("application/json")
                        .content("{\"firstName\":\"Renamed\"}"))
                .andExpect(status().isOk()).andReturn();
        assertThat(objectMapper.readTree(kept.getResponse().getContentAsString()).get("phoneNumber").asText()).isEqualTo("+14155552671");
        MvcResult removed = mockMvc.perform(patch("/api/v1/users/me").header("Authorization", token).contentType("application/json")
                        .content("{\"phoneNumber\":\"\"}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode after = objectMapper.readTree(removed.getResponse().getContentAsString());
        assertThat(after.get("phoneNumber").isNull()).isTrue();
        assertThat(after.get("birthDate").asText()).isEqualTo("1985-12-01");
    }

    // ---- emails ----

    @Test
    void emails_inPlainAscii_areAccepted() throws Exception {
        for (String email : new String[] {"a.b+tag@test.local", "x_y-z@sub.example.co", "UPPER.Case@Example.COM", "a%b@test.local",
                "n1@a-b.example.org"}) {
            assertThat(register("Jane", null, "Doe", email.replace("@", "." + UUID.randomUUID().toString().substring(0, 6) + "@")))
                    .as(email).isEqualTo(201);
        }
    }

    @Test
    void emails_withAnyOtherCharacterOrShape_areRejected() throws Exception {
        for (String email : new String[] {"asdasdasd[]@gmail.com", "a b@x.com", "a..b@x.com", ".a@x.com", "a.@x.com", "-a@x.com", "a@x",
                "a@-x.com", "a@x-.com", "a@x..com", "a@x.c", "é@x.com", "a@éx.com", "a\"b@x.com", "a(b)@x.com", "a,b@x.com",
                "a;b@x.com", "a<b>@x.com", "a'b@x.com", "a@b@x.com", "@x.com", "a@", "a@x.com ", " a@x.com", "a@x_y.com", "a{b}@x.com", ""}) {
            assertThat(register("Jane", null, "Doe", email)).as("[" + email + "]").isEqualTo(400);
        }
    }

    // ---- the rest of the body ----

    @Test
    void missingFields_areRejected() throws Exception {
        for (String body : new String[] {"{}", "{\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"email\":\"" + uniqueEmail() + "\"}",
                "{\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"passwordHash\":\"x\"}",
                "{\"lastName\":\"Doe\",\"email\":\"" + uniqueEmail() + "\",\"passwordHash\":\"x\"}",
                "{\"firstName\":\"Jane\",\"email\":\"" + uniqueEmail() + "\",\"passwordHash\":\"x\"}",
                "{\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"email\":\"" + uniqueEmail() + "\",\"passwordHash\":\"\"}"}) {
            mockMvc.perform(post("/api/v1/auth/register").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void aRoleSentInTheBody_isIgnored_everyNewAccountIsACustomer() throws Exception {
        String email = uniqueEmail();
        emails.add(email);
        Map<String, Object> body = body("Mallory", null, "Smith", email);
        body.put("role", "ADMIN");
        body.put("createdBy", "x");

        assertThat(send(body).getResponse().getStatus()).isEqualTo(201);

        assertThat(userRepository.findByEmail(email)).get().satisfies(user -> {
            assertThat(user.getRole()).isEqualTo(Role.CUSTOMER);
            assertThat(user.getPasswordHash()).isNotEqualTo("Passw0rd!");
        });
    }

    @Test
    void thePasswordMayContainAnyCharacter() throws Exception {
        String email = uniqueEmail();
        emails.add(email);
        Map<String, Object> body = body("Jane", null, "Doe", email);
        body.put("passwordHash", "p@ss [w0rd] <>\"'; é李");

        assertThat(send(body).getResponse().getStatus()).isEqualTo(201);
    }

    // ---- the profile updates follow the same rules ----

    private String signedInUser() {
        User saved = userRepository.save(User.builder().firstName("Existing").lastName("User").email(uniqueEmail())
                .passwordHash(passwordEncoder.encode("irrelevant")).role(Role.CUSTOMER).build());
        userIds.add(saved.getId());
        return "Bearer " + jwtService.generateAccessToken(saved);
    }

    @Test
    void updatingYourOwnProfile_withBadCharacters_isRejected() throws Exception {
        String token = signedInUser();

        for (String body : new String[] {"{\"email\":\"x[]@gmail.com\"}", "{\"email\":\"a..b@x.com\"}", "{\"firstName\":\"mark[]\"}",
                "{\"lastName\":\"Doe1\"}", "{\"middleName\":\"<b>x</b>\"}", "{\"firstName\":\"<b>x</b>\"}", "{\"firstName\":\"\"}",
                "{\"lastName\":\"  \"}"}) {
            mockMvc.perform(patch("/api/v1/users/me").header("Authorization", token).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void updatingYourOwnNames_changesOnlyTheGivenParts_andAnEmptyMiddleNameRemovesIt() throws Exception {
        String token = signedInUser();

        mockMvc.perform(patch("/api/v1/users/me").header("Authorization", token).contentType("application/json")
                        .content("{\"firstName\":\"José\",\"middleName\":\"O'Neil\",\"lastName\":\"Brien-Lee\"}"))
                .andExpect(status().isOk());
        MvcResult onlyLast = mockMvc.perform(patch("/api/v1/users/me").header("Authorization", token).contentType("application/json")
                        .content("{\"lastName\":\"Reyes\"}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode json = objectMapper.readTree(onlyLast.getResponse().getContentAsString());
        assertThat(json.get("firstName").asText()).isEqualTo("José");
        assertThat(json.get("middleName").asText()).isEqualTo("O'Neil");
        assertThat(json.get("lastName").asText()).isEqualTo("Reyes");
        assertThat(json.has("name")).isFalse();

        MvcResult removed = mockMvc.perform(patch("/api/v1/users/me").header("Authorization", token).contentType("application/json")
                        .content("{\"middleName\":\"\"}"))
                .andExpect(status().isOk()).andReturn();
        assertThat(objectMapper.readTree(removed.getResponse().getContentAsString()).get("middleName").isNull()).isTrue();
    }

    @Test
    void anEmailThatIsChangedThroughTheProfile_followsTheSameRules() throws Exception {
        String token = signedInUser();
        String newEmail = uniqueEmail();
        emails.add(newEmail);

        mockMvc.perform(patch("/api/v1/users/me").header("Authorization", token).contentType("application/json")
                        .content("{\"email\":\"" + newEmail + "\"}"))
                .andExpect(status().isOk());
    }
}
