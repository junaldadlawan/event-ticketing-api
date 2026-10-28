package com.junaldadlawan.event_ticketing_api.tickettype;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.common.TestTicketPrefixes;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationMemberRepository;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
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
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A ticket type's display colour: set on create, changed / cleared by PATCH, logged, and readable by buyers. */
@SpringBootTest
@AutoConfigureMockMvc
class TicketTypeColorIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private OrganizationMemberRepository organizationMemberRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private TicketTypeRepository ticketTypeRepository;

    private final List<UUID> ticketTypeIds = new ArrayList<>();
    private User owner;
    private UUID orgId;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        owner = User.builder().id(UUID.randomUUID()).email("color-" + UUID.randomUUID() + "@test.local").role(Role.CUSTOMER).build();
        orgId = organizationRepository.save(Organization.builder().name("Colour Test Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED).ownerId(owner.getId()).documents(List.of()).build()).getId();
        OrganizationMember member = OrganizationMember.builder().userId(owner.getId()).organizationId(orgId).build();
        member.getRoles().add(OrganizationRole.OWNER);
        organizationMemberRepository.save(member);
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        eventId = eventRepository.save(Event.builder().organizationId(orgId).title("Colour Test Event").description("desc")
                .category("music").status(EventStatus.PUBLISHED).ticketPrefix(TestTicketPrefixes.free(jdbc))
                .startAt(startAt).endAt(startAt.plus(2, ChronoUnit.HOURS)).timezone("UTC").build()).getId();
    }

    @AfterEach
    void tearDown() {
        ticketTypeIds.forEach(ticketTypeRepository::deleteById);
        jdbc.update("delete from audit_log_entries where event_id = ?", eventId);
        eventRepository.deleteById(eventId);
        organizationMemberRepository.findByUserIdAndOrganizationId(owner.getId(), orgId).ifPresent(organizationMemberRepository::delete);
        organizationRepository.deleteById(orgId);
    }

    @Test
    void setOnCreate_uppercased_changed_leftAloneWhenMissing_clearedByNull_andLogged() throws Exception {
        UUID id = create(",\"color\":\"#1a2b3c\"").andExpect(status().isCreated())
                .andExpect(jsonPath("$.color").value("#1A2B3C")).andReturnId();

        edit(id, "{\"color\":\"#ff0000\"}").andExpect(status().isOk()).andExpect(jsonPath("$.color").value("#FF0000"));
        edit(id, "{\"name\":\"Renamed\"}").andExpect(status().isOk()).andExpect(jsonPath("$.color").value("#FF0000"));
        edit(id, "{\"color\":null}").andExpect(status().isOk()).andExpect(jsonPath("$.color").doesNotExist());
        assertThat(ticketTypeRepository.findById(id).orElseThrow().getColor()).isNull();

        List<String> logged = jdbc.queryForList("select details::text from audit_log_entries where action = 'ticket_type.updated'"
                + " and target_id = ? order by created_at", String.class, id);
        assertThat(logged).hasSize(3);
        assertThat(logged.get(0)).contains("\"field\": \"color\"").contains("\"from\": \"#1A2B3C\"").contains("\"to\": \"#FF0000\"");
        assertThat(logged.get(1)).doesNotContain("color");
        assertThat(logged.get(2)).contains("\"from\": \"#FF0000\"").contains("\"to\": null");
    }

    @Test
    void anythingButSixHexDigitsIsRefused_withAFieldError() throws Exception {
        create(",\"color\":\"red\"").andExpect(status().isBadRequest());
        UUID id = create("").andExpect(status().isCreated()).andExpect(jsonPath("$.color").doesNotExist()).andReturnId();
        for (String bad : List.of("#12345", "#1234567", "123456", "#GGGGGG", "")) {
            edit(id, "{\"color\":\"" + bad + "\"}").andExpect(status().isBadRequest())
                    .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("color"));
        }
        assertThat(ticketTypeRepository.findById(id).orElseThrow().getColor()).isNull();
    }

    @Test
    void aBuyerReadingTheTicketTypeGetsTheColour() throws Exception {
        UUID id = create(",\"color\":\"#00aa55\"").andExpect(status().isCreated()).andReturnId();
        String buyer = "Bearer " + jwtService.generateAccessToken(
                User.builder().id(UUID.randomUUID()).email("buyer-" + UUID.randomUUID() + "@test.local").role(Role.CUSTOMER).build());

        mockMvc.perform(get("/api/v1/ticket-types/{id}", id).header("Authorization", buyer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.color").value("#00AA55"));
        mockMvc.perform(get("/api/v1/events/{eventId}/ticket-types", eventId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].color").value("#00AA55"));
    }

    // ---- helpers ----

    private Created create(String extra) throws Exception {
        String body = """
                {"name":"General Admission","kind":"GENERAL_ADMISSION","price":{"amount":1000,"currency":"USD"},
                "quantityTotal":100,"saleStartAt":"%s","saleEndAt":"%s"%s}
                """.formatted(Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(5, ChronoUnit.DAYS), extra);
        return new Created(mockMvc.perform(post("/api/v1/events/{eventId}/ticket-types", eventId)
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(owner))
                .contentType("application/json").content(body)));
    }

    private ResultActions edit(UUID id, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/ticket-types/{id}", id)
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(owner))
                .contentType("application/json").content(body));
    }

    /** Tracks a created ticket type for cleanup. */
    private final class Created {
        private final ResultActions result;

        Created(ResultActions result) throws Exception {
            this.result = result;
            if (result.andReturn().getResponse().getStatus() == 201) {
                ticketTypeIds.add(id());
            }
        }

        Created andExpect(org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
            result.andExpect(matcher);
            return this;
        }

        UUID andReturnId() throws Exception {
            return id();
        }

        private UUID id() throws Exception {
            return UUID.fromString(objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asText());
        }
    }
}
