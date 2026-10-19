package com.junaldadlawan.event_ticketing_api.analytics;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The audience and sales-pattern aggregates on the real database. The event is in Asia/Manila (UTC+8) and ends 2001-01-09 23:00 there. Buyers:
 * five aged 30 (one order each on Fri 5 Jan 10:00 Manila, one live ticket each; the first also has a refunded ticket), two aged 20 (Mon 8 Jan
 * 04:00 Manila, which is still 7 Jan in UTC; two tickets each, one order PARTIALLY_REFUNDED), one aged 17 when ordering on 5 Jan (43 today),
 * one with no account row (no birth date; a promo-code order on 8 Jan) and one whose order was CANCELLED (never counted).
 */
@SpringBootTest
@AutoConfigureMockMvc
class EventInsightsIntegrationTest {

    private static final String FRI_10AM = "2001-01-05T02:00:00Z";
    private static final String MON_4AM = "2001-01-07T20:00:00Z";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    private final List<UUID> orgIds = new ArrayList<>();
    private final List<OrganizationMember> members = new ArrayList<>();
    private final List<UUID> userIds = new ArrayList<>();
    private final List<UUID> orderIds = new ArrayList<>();
    private UUID eventId;
    private String owner;
    private String otherOwner;

    @BeforeEach
    void setUp() {
        UUID org = organization();
        owner = ownerOf(org);
        otherOwner = ownerOf(organization());
        String prefix;
        do {
            prefix = ThreadLocalRandom.current().ints(3, 'A', 'Z' + 1).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
        } while (jdbc.queryForObject("select count(*) from events where ticket_prefix = ?", Long.class, prefix) > 0);
        eventId = UUID.randomUUID();
        jdbc.update("""
                insert into events (id, organization_id, title, description, category, start_time, end_time, status, created_by, created_at, ticket_prefix, timezone)
                values (?, ?, 'Insights Test Event', 'd', 'Music', '2001-01-09T12:00:00Z', '2001-01-09T15:00:00Z', 'DRAFT', 'insights-it', now(), ?, 'Asia/Manila')""",
                eventId, org, prefix);

        for (int i = 0; i < 5; i++) {
            UUID order = order(buyer("1971-01-01"), "PAID", FRI_10AM, null);
            ticket(order, "VALID");
            if (i == 0) {
                ticket(order, "REFUNDED");
            }
        }
        for (String status : List.of("PAID", "PARTIALLY_REFUNDED")) {
            UUID order = order(buyer("1980-06-01"), status, MON_4AM, null);
            ticket(order, "VALID");
            ticket(order, "USED");
        }
        ticket(order(buyer("1983-01-07"), "PAID", FRI_10AM, null), "VALID");   // 17 on 5 Jan 2001
        ticket(order(UUID.randomUUID(), "PAID", MON_4AM, "SAVE10"), "VALID");   // no account row: unknown age
        ticket(order(buyer("1971-01-01"), "CANCELLED", FRI_10AM, null), "CANCELLED");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from tickets where event_id = ?", eventId);
        orderIds.forEach(id -> jdbc.update("delete from orders where id = ?", id));
        jdbc.update("delete from events where id = ?", eventId);
        members.forEach(organizationMemberRepository::delete);
        orgIds.forEach(organizationRepository::deleteById);
        userIds.forEach(id -> {
            jdbc.update("delete from users where id = ?", id);
            jdbc.update("delete from audit_log_entries where actor_id = ?", id);
        });
    }

    private UUID organization() {
        UUID id = organizationRepository.save(Organization.builder().name("Insights Test Org " + UUID.randomUUID()).status(OrganizationStatus.APPROVED)
                .ownerId(UUID.randomUUID()).documents(List.of()).build()).getId();
        orgIds.add(id);
        return id;
    }

    private String ownerOf(UUID org) {
        UUID userId = UUID.randomUUID();
        OrganizationMember member = OrganizationMember.builder().userId(userId).organizationId(org).build();
        member.getRoles().add(OrganizationRole.OWNER);
        members.add(organizationMemberRepository.save(member));
        userIds.add(userId);
        return "Bearer " + jwtService.generateAccessToken(User.builder().id(userId).email("insights-it-" + UUID.randomUUID() + "@test.local").role(Role.CUSTOMER).build());
    }

    private UUID buyer(String birthDate) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into users (id, email, password_hash, role, created_at, created_by, first_name, last_name, birth_date)
                values (?, ?, 'x', 'CUSTOMER', now(), 'insights-it', 'Test', 'Buyer', ?::date)""", id, "insights-it-" + id + "@test.local", birthDate);
        userIds.add(id);
        return id;
    }

    private UUID order(UUID buyerId, String status, String at, String promoCode) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into orders (id, buyer_id, payee_type, payee_id, status, total_amount, total_currency, created_by, created_at, promo_code)
                values (?, ?, 'ORGANIZATION', ?, ?, 1000, 'PHP', 'insights-it', ?::timestamptz, ?)""", id, buyerId, UUID.randomUUID(), status, at, promoCode);
        orderIds.add(id);
        return id;
    }

    private void ticket(UUID orderId, String status) {
        jdbc.update("""
                insert into tickets (id, order_id, event_id, ticket_type_id, owner_id, ticket_number, credential, status, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, now())""", UUID.randomUUID(), orderId, eventId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID().toString().substring(0, 20), "insights-it-" + UUID.randomUUID(), status);
    }

    private ResultActions call(String path, String caller) throws Exception {
        var request = get("/api/v1/events/{id}/analytics/" + path, eventId);
        return mockMvc.perform(caller == null ? request : request.header("Authorization", caller));
    }

    @Test
    void audience_groupsBuyersByTheirAgeWhenTheyOrdered_andHidesGroupsOfFewerThanFive() throws Exception {
        call("audience", owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.buyers").value(9)).andExpect(jsonPath("$.unknownAge").value(1))
                .andExpect(jsonPath("$.ageGroups[*].label").value(contains("Under 18", "18-24", "25-34", "35-44", "45-54", "55+")))
                .andExpect(jsonPath("$.ageGroups[0].buyers").value(nullValue())).andExpect(jsonPath("$.ageGroups[0].tickets").value(nullValue()))
                .andExpect(jsonPath("$.ageGroups[1].buyers").value(nullValue())).andExpect(jsonPath("$.ageGroups[1].tickets").value(nullValue()))
                .andExpect(jsonPath("$.ageGroups[2].buyers").value(5)).andExpect(jsonPath("$.ageGroups[2].tickets").value(5))   // the refunded one is not live
                .andExpect(jsonPath("$.ageGroups[3].buyers").value(0))   // the 17-year-old is 43 today, but counted at their order
                .andExpect(jsonPath("$.ageGroups[5].buyers").value(0)).andExpect(jsonPath("$.ageGroups[5].tickets").value(0))
                .andExpect(jsonPath("$..birthDate").doesNotExist()).andExpect(jsonPath("$..buyerId").doesNotExist());
    }

    @Test
    void salesPatterns_countLiveTicketsInTheEventsTimeZone_andFillEveryDayUpToTheEnd() throws Exception {
        call("sales-patterns", owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Asia/Manila"))
                .andExpect(jsonPath("$.tickets").value(11)).andExpect(jsonPath("$.orders").value(9)).andExpect(jsonPath("$.promoOrders").value(1))
                .andExpect(jsonPath("$.daily[*].day").value(contains("2001-01-05", "2001-01-06", "2001-01-07", "2001-01-08", "2001-01-09")))
                .andExpect(jsonPath("$.daily[*].tickets").value(contains(6, 0, 0, 5, 0)))
                .andExpect(jsonPath("$.byWeekday").value(contains(5, 0, 0, 0, 6, 0, 0)))   // Monday first: Mon 5, Fri 6
                .andExpect(jsonPath("$.byHour.length()").value(24))
                .andExpect(jsonPath("$.byHour[4]").value(5)).andExpect(jsonPath("$.byHour[10]").value(6)).andExpect(jsonPath("$.byHour[2]").value(0));
    }

    @Test
    void onlyTheEventsOwnerOrganizerOrAnAdminMayReadThem() throws Exception {
        String admin = "Bearer " + jwtService.generateAccessToken(User.builder().id(UUID.randomUUID()).email("insights-it-" + UUID.randomUUID() + "@test.local")
                .role(Role.ADMIN).build());
        call("audience", admin).andExpect(status().isOk());
        for (String path : List.of("audience", "sales-patterns")) {
            call(path, otherOwner).andExpect(status().isForbidden());
            call(path, null).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/v1/events/{id}/analytics/" + path, UUID.randomUUID()).header("Authorization", owner)).andExpect(status().isNotFound());
        }
    }
}
