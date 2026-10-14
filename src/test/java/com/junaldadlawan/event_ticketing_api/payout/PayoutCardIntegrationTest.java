package com.junaldadlawan.event_ticketing_api.payout;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationMemberRepository;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
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
 * Payout cards through the real security chain and database. Callers are in-memory JWT users; the organizations, their
 * members and cards are created here and removed afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PayoutCardIntegrationTest {

    private static final String TOKEN = "tok_visa_9f8e7d6c5b4a";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> orgIds = new ArrayList<>();
    private final List<OrganizationMember> members = new ArrayList<>();

    private UUID orgId;
    private String owner;
    private String organizer;
    private String stranger;
    private String admin;
    private String superAdmin;

    @BeforeEach
    void setUp() {
        orgId = organization(OrganizationStatus.APPROVED);
        owner = memberToken(orgId, OrganizationRole.OWNER);
        organizer = memberToken(orgId, OrganizationRole.ORGANIZER);
        stranger = token(Role.CUSTOMER, UUID.randomUUID());
        admin = token(Role.ADMIN, UUID.randomUUID());
        superAdmin = token(Role.SUPER_ADMIN, UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        orgIds.forEach(id -> {
            jdbc.update("delete from payouts where organization_id = ?", id);
            jdbc.update("delete from payout_cards where organization_id = ?", id);
        });
        members.forEach(member -> organizationMemberRepository.findByUserIdAndOrganizationId(member.getUserId(), member.getOrganizationId())
                .ifPresent(organizationMemberRepository::delete));
        orgIds.forEach(organizationRepository::deleteById);
    }

    private UUID organization(OrganizationStatus status) {
        Organization saved = organizationRepository.save(Organization.builder().name("Payout Card Test Org " + UUID.randomUUID())
                .status(status).ownerId(UUID.randomUUID()).documents(List.of()).build());
        orgIds.add(saved.getId());
        return saved.getId();
    }

    private String token(Role role, UUID id) {
        return "Bearer " + jwtService.generateAccessToken(User.builder().id(id)
                .email("payout-card-" + UUID.randomUUID() + "@test.local").role(role).build());
    }

    private String memberToken(UUID org, OrganizationRole role) {
        UUID userId = UUID.randomUUID();
        OrganizationMember member = OrganizationMember.builder().userId(userId).organizationId(org).build();
        member.getRoles().add(role);
        members.add(organizationMemberRepository.save(member));
        return token(Role.CUSTOMER, userId);
    }

    private static String body(String last4, boolean primary) {
        return body(last4, YearMonth.now(ZoneOffset.UTC).getYear() + 2, 12, TOKEN + last4, primary);
    }

    private static String body(String last4, int expYear, int expMonth, String paymentToken, boolean primary) {
        return "{\"holderName\":\"Demo Events Co.\",\"brand\":\"Visa\",\"last4\":\"" + last4 + "\",\"expMonth\":" + expMonth
                + ",\"expYear\":" + expYear + ",\"paymentToken\":\"" + paymentToken + "\",\"primary\":" + primary + "}";
    }

    private ResultActions add(String caller, UUID org, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/organizations/{id}/payout-cards", org).header("Authorization", caller)
                .contentType("application/json").content(json));
    }

    private UUID addOk(String last4, boolean primary) throws Exception {
        MvcResult result = add(owner, orgId, body(last4, primary)).andExpect(status().isCreated()).andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private ResultActions list(String caller) throws Exception {
        return mockMvc.perform(get("/api/v1/organizations/{id}/payout-cards", orgId).header("Authorization", caller));
    }

    private ResultActions makePrimary(String caller, UUID cardId) throws Exception {
        return mockMvc.perform(put("/api/v1/payout-cards/{id}/primary", cardId).header("Authorization", caller));
    }

    private ResultActions remove(String caller, UUID cardId) throws Exception {
        return mockMvc.perform(delete("/api/v1/payout-cards/{id}", cardId).header("Authorization", caller));
    }

    private int primaries() {
        return jdbc.queryForObject("select count(*) from payout_cards where organization_id = ? and is_primary and deleted_at is null",
                Integer.class, orgId);
    }

    @Test
    void theOwnerAddsCards_theFirstIsPrimary_andTheTokenNeverComesBack() throws Exception {
        add(owner, orgId, body("4242", false)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.primary").value(true))
                .andExpect(jsonPath("$.last4").value("4242"))
                .andExpect(jsonPath("$.brand").value("Visa"))
                .andExpect(jsonPath("$.paymentToken").doesNotExist())
                .andExpect(content().string(not(containsString(TOKEN))));
        add(owner, orgId, body("1111", false)).andExpect(status().isCreated()).andExpect(jsonPath("$.primary").value(false));

        list(owner).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].last4").value("4242")).andExpect(jsonPath("$[0].primary").value(true))
                .andExpect(content().string(not(containsString(TOKEN))));
        assertThat(primaries()).isEqualTo(1);
    }

    @Test
    void addingAPrimaryCard_unsetsTheOldOne_soThereIsNeverMoreThanOnePrimary() throws Exception {
        UUID first = addOk("4242", false);
        UUID second = addOk("1111", true);

        JsonNode cards = objectMapper.readTree(list(owner).andReturn().getResponse().getContentAsString());
        for (JsonNode card : cards) {
            assertThat(card.get("primary").asBoolean()).isEqualTo(card.get("id").asText().equals(second.toString()));
        }
        assertThat(primaries()).isEqualTo(1);

        makePrimary(owner, first).andExpect(status().isOk()).andExpect(jsonPath("$.primary").value(true));
        assertThat(primaries()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select is_primary from payout_cards where id = ?", Boolean.class, second)).isFalse();
    }

    @Test
    void theDatabaseItselfRefusesASecondPrimary() throws Exception {
        addOk("4242", true);

        assertThatThrownBy(() -> jdbc.update("insert into payout_cards (id, organization_id, holder_name, brand, last4, exp_month, exp_year,"
                + " payment_token, is_primary, created_by, created_at) values (?, ?, 'H', 'Visa', '0000', 12, 2099, 'tok_other_12345678', true, 'test', now())",
                UUID.randomUUID(), orgId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(primaries()).isEqualTo(1);
    }

    @Test
    void thePrimaryCannotBeRemovedWhileOthersExist_butAnyOtherCardCan() throws Exception {
        UUID primary = addOk("4242", false);
        UUID other = addOk("1111", false);

        remove(owner, primary).andExpect(status().isConflict());
        list(owner).andExpect(jsonPath("$.length()").value(2));

        remove(owner, other).andExpect(status().isNoContent());
        list(owner).andExpect(jsonPath("$.length()").value(1));
        // soft delete: the row is still there
        assertThat(jdbc.queryForObject("select deleted_at is not null from payout_cards where id = ?", Boolean.class, other)).isTrue();

        // the last card, even the primary, can go; the next card added is primary again
        remove(owner, primary).andExpect(status().isNoContent());
        list(owner).andExpect(jsonPath("$.length()").value(0));
        add(owner, orgId, body("2222", false)).andExpect(status().isCreated()).andExpect(jsonPath("$.primary").value(true));
    }

    @Test
    void theOrganizerCanOnlyRead() throws Exception {
        UUID card = addOk("4242", false);

        list(organizer).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        add(organizer, orgId, body("1111", false)).andExpect(status().isForbidden());
        makePrimary(organizer, card).andExpect(status().isForbidden());
        remove(organizer, card).andExpect(status().isForbidden());
        list(owner).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void aStrangerAndAnAnonymousCallerGetNothing() throws Exception {
        UUID card = addOk("4242", false);

        list(stranger).andExpect(status().isForbidden());
        add(stranger, orgId, body("1111", false)).andExpect(status().isForbidden());
        makePrimary(stranger, card).andExpect(status().isForbidden());
        remove(stranger, card).andExpect(status().isForbidden());
        // another organization's owner is a stranger here too
        UUID otherOrg = organization(OrganizationStatus.APPROVED);
        String otherOwner = memberToken(otherOrg, OrganizationRole.OWNER);
        list(otherOwner).andExpect(status().isForbidden());
        remove(otherOwner, card).andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/organizations/{id}/payout-cards", orgId)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/organizations/{id}/payout-cards", orgId).contentType("application/json").content(body("1111", false)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/payout-cards/{id}", card)).andExpect(status().isUnauthorized());
        list(owner).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void anAdminAndTheSuperAdminMayManageAndRead() throws Exception {
        add(admin, orgId, body("4242", false)).andExpect(status().isCreated());
        UUID second = UUID.fromString(objectMapper.readTree(
                add(superAdmin, orgId, body("1111", false)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText());

        list(admin).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        list(superAdmin).andExpect(status().isOk());
        makePrimary(admin, second).andExpect(status().isOk());
        remove(superAdmin, second).andExpect(status().isConflict());   // it is the primary and another card exists
    }

    @Test
    void onlyAnApprovedOrganizationMayAddCards() throws Exception {
        UUID pending = organization(OrganizationStatus.PENDING);
        String pendingOwner = memberToken(pending, OrganizationRole.OWNER);

        add(pendingOwner, pending, body("4242", false)).andExpect(status().isForbidden());
        add(admin, pending, body("4242", false)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from payout_cards where organization_id = ?", Integer.class, pending)).isZero();
        add(owner, UUID.randomUUID(), body("4242", false)).andExpect(status().isNotFound());
    }

    @Test
    void badCardDetailsAreRejected_andNothingIsStored() throws Exception {
        int year = YearMonth.now(ZoneOffset.UTC).getYear() + 2;
        YearMonth lastMonth = YearMonth.now(ZoneOffset.UTC).minusMonths(1);
        for (String json : new String[] {
                body("4242", lastMonth.getYear(), lastMonth.getMonthValue(), TOKEN, false),   // expired
                body("424", year, 12, TOKEN, false),                                          // last4 too short
                body("42a2", year, 12, TOKEN, false),                                         // last4 not digits
                body("4242", year, 13, TOKEN, false),                                         // month out of range
                body("4242", year, 0, TOKEN, false),
                body("4242", year, 12, "4242424242424242", false),                            // a card number, not a token
                body("4242", year, 12, "tok short", false),                                   // not a token
                body("4242", year, 12, "short", false),
                "{\"holderName\":\"<b>Evil</b>\",\"brand\":\"Visa\",\"last4\":\"4242\",\"expMonth\":12,\"expYear\":" + year + ",\"paymentToken\":\"" + TOKEN + "\"}",
                "{\"holderName\":\"" + "A".repeat(101) + "\",\"brand\":\"Visa\",\"last4\":\"4242\",\"expMonth\":12,\"expYear\":" + year + ",\"paymentToken\":\"" + TOKEN + "\"}",
                "{\"holderName\":\"\",\"brand\":\"Visa\",\"last4\":\"4242\",\"expMonth\":12,\"expYear\":" + year + ",\"paymentToken\":\"" + TOKEN + "\"}",
                "{\"holderName\":\"Holder\",\"last4\":\"4242\",\"expMonth\":12,\"expYear\":" + year + ",\"paymentToken\":\"" + TOKEN + "\"}"}) {
            add(owner, orgId, json).andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("select count(*) from payout_cards where organization_id = ?", Integer.class, orgId)).isZero();
    }

    @Test
    void aPayoutNeedsAPrimaryCard() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String period = "{\"periodStart\":\"" + today.minusDays(1) + "\",\"periodEnd\":\"" + today + "\"}";

        mockMvc.perform(post("/api/v1/organizations/{id}/payouts", orgId).header("Authorization", admin)
                        .contentType("application/json").content(period))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("no primary payout card")));

        // with a card the check passes (and there is simply nothing due)
        addOk("4242", false);
        mockMvc.perform(post("/api/v1/organizations/{id}/payouts", orgId).header("Authorization", admin)
                        .contentType("application/json").content(period))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("No orders are due")));
    }
}
