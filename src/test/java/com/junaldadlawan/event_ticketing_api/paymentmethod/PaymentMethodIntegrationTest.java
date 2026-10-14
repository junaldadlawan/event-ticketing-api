package com.junaldadlawan.event_ticketing_api.paymentmethod;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Saved cards through the real security chain and database, with the default (mock) gateway, so Stripe is never called.
 * The callers are real rows (the service reads the signed-in user), created here and removed afterwards with their cards.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentMethodIntegrationTest {

    private static final String BASE = "/api/v1/users/me/payment-methods";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> userIds = new ArrayList<>();
    private final int year = YearMonth.now(ZoneOffset.UTC).getYear() + 2;

    private User me;
    private String token;

    @BeforeEach
    void setUp() {
        me = user(Role.CUSTOMER);
        token = token(me);
    }

    @AfterEach
    void tearDown() {
        userIds.forEach(id -> jdbc.update("delete from payment_methods where user_id = ?", id));
        userIds.forEach(userRepository::deleteById);
    }

    private User user(Role role) {
        User saved = userRepository.save(User.builder().firstName("Pay").lastName("Tester")
                .email("pm-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local").passwordHash("x").role(role).build());
        userIds.add(saved.getId());
        return saved;
    }

    private String token(User user) {
        return "Bearer " + jwtService.generateAccessToken(user);
    }

    private String card(String tokenValue, String last4, boolean primary) {
        return "{\"paymentToken\":\"" + tokenValue + "\",\"primary\":" + primary + ",\"holderName\":\"Pay Tester\",\"last4\":\"" + last4
                + "\",\"expMonth\":12,\"expYear\":" + year + "}";
    }

    private ResultActions add(String caller, String json) throws Exception {
        return mockMvc.perform(post(BASE).header("Authorization", caller).contentType("application/json").content(json));
    }

    private UUID addOk(String tokenValue, String last4, boolean primary) throws Exception {
        String body = add(token, card(tokenValue, last4, primary)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private ResultActions list(String caller) throws Exception {
        return mockMvc.perform(get(BASE).header("Authorization", caller));
    }

    private int primaries(UUID userId) {
        return jdbc.queryForObject("select count(*) from payment_methods where user_id = ? and is_primary and deleted_at is null", Integer.class, userId);
    }

    @Test
    void aUserWithNoCards_getsAnEmptyList_andTheOtherMeRoutesStillWork() throws Exception {
        list(token).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", token)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/me/notifications").header("Authorization", token)).andExpect(status().isOk());
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    void anyUserManagesTheirOwnCards_customerAdminAndSuperAdmin() throws Exception {
        for (Role role : new Role[] {Role.CUSTOMER, Role.ADMIN, Role.SUPER_ADMIN}) {
            if (role == Role.SUPER_ADMIN && userRepository.count() > 0
                    && jdbc.queryForObject("select count(*) from users where role = 'SUPER_ADMIN'", Integer.class) > 0) {
                continue;   // only one super admin may exist: never create a second next to a seeded one
            }
            User who = user(role);
            String caller = token(who);
            add(caller, card("tok_visa", "4242", false)).andExpect(status().isCreated());
            list(caller).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        }
    }

    @Test
    void theFirstCardIsPrimary_andTheResponseHasNoTokenOrProviderId() throws Exception {
        add(token, card("tok_visa", "4242", false)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.primary").value(true))
                .andExpect(jsonPath("$.brand").value("visa"))
                .andExpect(jsonPath("$.last4").value("4242"))
                .andExpect(jsonPath("$.holderName").value("Pay Tester"))
                .andExpect(jsonPath("$.paymentToken").doesNotExist())
                .andExpect(jsonPath("$.stripePaymentMethodId").doesNotExist())
                .andExpect(content().string(not(containsString("tok_visa"))));
        addOk("tok_mastercard", "4444", false);

        list(token).andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].last4").value("4242"))
                .andExpect(jsonPath("$[0].primary").value(true)).andExpect(jsonPath("$[1].primary").value(false))
                .andExpect(content().string(not(containsString("tok_"))));
        assertThat(primaries(me.getId())).isEqualTo(1);
    }

    @Test
    void addingANewPrimary_unsetsTheOldOne_soTwoPrimariesAreImpossible_evenInTheDatabase() throws Exception {
        UUID first = addOk("tok_visa", "4242", false);
        UUID second = addOk("tok_mastercard", "4444", true);

        JsonNode cards = objectMapper.readTree(list(token).andReturn().getResponse().getContentAsString());
        assertThat(cards.get(0).get("id").asText()).isEqualTo(second.toString());   // primary first
        assertThat(primaries(me.getId())).isEqualTo(1);

        mockMvc.perform(put(BASE + "/{id}/primary", first).header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.primary").value(true));
        assertThat(primaries(me.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select is_primary from payment_methods where id = ?", Boolean.class, second)).isFalse();

        assertThatThrownBy(() -> jdbc.update("insert into payment_methods (id, user_id, holder_name, brand, last4, exp_month, exp_year, is_primary,"
                + " created_by, created_at) values (?, ?, 'H', 'visa', '0000', 12, 2099, true, 'test', now())", UUID.randomUUID(), me.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void thePrimaryCannotBeDeletedWhileOthersExist_butTheOnlyCardCanBe() throws Exception {
        UUID primary = addOk("tok_visa", "4242", false);
        UUID other = addOk("tok_mastercard", "4444", false);

        mockMvc.perform(delete(BASE + "/{id}", primary).header("Authorization", token)).andExpect(status().isConflict());
        mockMvc.perform(delete(BASE + "/{id}", other).header("Authorization", token)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select deleted_at is not null from payment_methods where id = ?", Boolean.class, other)).isTrue();   // soft delete
        mockMvc.perform(delete(BASE + "/{id}", primary).header("Authorization", token)).andExpect(status().isNoContent());
        list(token).andExpect(jsonPath("$.length()").value(0));
        // the next card is primary again
        add(token, card("tok_visa", "1111", false)).andExpect(status().isCreated()).andExpect(jsonPath("$.primary").value(true));
    }

    @Test
    void anotherUsersCard_is404_andAStrangerNeverSeesTheList() throws Exception {
        UUID mine = addOk("tok_visa", "4242", false);
        User stranger = user(Role.CUSTOMER);
        String strangerToken = token(stranger);

        mockMvc.perform(put(BASE + "/{id}/primary", mine).header("Authorization", strangerToken)).andExpect(status().isNotFound());
        mockMvc.perform(delete(BASE + "/{id}", mine).header("Authorization", strangerToken)).andExpect(status().isNotFound());
        list(strangerToken).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        list(token).andExpect(jsonPath("$.length()").value(1));
        assertThat(jdbc.queryForObject("select deleted_at is null from payment_methods where id = ?", Boolean.class, mine)).isTrue();
    }

    @Test
    void badCardsAreRefused_andNothingIsStored() throws Exception {
        YearMonth lastMonth = YearMonth.now(ZoneOffset.UTC).minusMonths(1);
        for (String json : new String[] {
                "{\"paymentToken\":\"tok_visa\",\"last4\":\"4242\",\"expMonth\":" + lastMonth.getMonthValue() + ",\"expYear\":" + lastMonth.getYear() + "}",   // expired
                card("tok_fail_declined", "4242", false),                    // a failing token
                card("tok_visa", "424", false),                              // last4 too short
                card("tok_visa", "42a2", false),                             // last4 not digits
                card("4242424242424242", "4242", false),                     // a card number, not a token
                "{\"paymentToken\":\"tok_visa\",\"holderName\":\"<b>Evil</b>\",\"last4\":\"4242\",\"expMonth\":12,\"expYear\":" + year + "}",
                "{\"paymentToken\":\"tok_visa\",\"holderName\":\"" + "A".repeat(101) + "\",\"last4\":\"4242\",\"expMonth\":12,\"expYear\":" + year + "}",
                "{\"paymentToken\":\"tok_visa\",\"last4\":\"4242\",\"expMonth\":13,\"expYear\":" + year + "}",
                "{\"paymentToken\":\"tok_visa\"}",                           // mock needs the details
                "{}"}) {
            add(token, json).andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("select count(*) from payment_methods where user_id = ?", Integer.class, me.getId())).isZero();
    }

    @Test
    void theMockGatewayGivesADummySetupSecret() throws Exception {
        mockMvc.perform(post(BASE + "/setup-intent").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.clientSecret").value("seti_mock_secret"));
        mockMvc.perform(post(BASE + "/setup-intent")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select stripe_customer_id from users where id = ?", String.class, me.getId())).isNull();   // mock never creates a customer
    }
}
