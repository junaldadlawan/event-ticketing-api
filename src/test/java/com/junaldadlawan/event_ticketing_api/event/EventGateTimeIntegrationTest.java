package com.junaldadlawan.event_ticketing_api.event;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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

/** BR-TICKET-GATE-001: the event's gate-open time - set on create, changed and cleared by PATCH, logged, 0 to 1440. */
@SpringBootTest
@AutoConfigureMockMvc
class EventGateTimeIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private OrganizationMemberRepository organizationMemberRepository;
    @Autowired private EventRepository eventRepository;

    private final List<UUID> eventIds = new ArrayList<>();
    private User owner;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        owner = User.builder().id(UUID.randomUUID()).email("gate-" + UUID.randomUUID() + "@test.local").role(Role.CUSTOMER).build();
        orgId = organizationRepository.save(Organization.builder().name("Gate Test Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED).ownerId(owner.getId()).documents(List.of()).build()).getId();
        OrganizationMember member = OrganizationMember.builder().userId(owner.getId()).organizationId(orgId).build();
        member.getRoles().add(OrganizationRole.OWNER);
        organizationMemberRepository.save(member);
    }

    @AfterEach
    void tearDown() {
        eventIds.forEach(id -> jdbc.update("delete from audit_log_entries where event_id = ?", id));
        eventIds.forEach(eventRepository::deleteById);
        organizationMemberRepository.findByUserIdAndOrganizationId(owner.getId(), orgId).ifPresent(organizationMemberRepository::delete);
        organizationRepository.deleteById(orgId);
    }

    @Test
    void setOnCreate_changed_keptWhenLeftOut_clearedByNull_andLogged() throws Exception {
        UUID id = create(",\"gateOpensMinutesBefore\":45").andExpect(status().isCreated())
                .andExpect(jsonPath("$.gateOpensMinutesBefore").value(45)).andReturnId();
        as(get("/api/v1/events/{id}", id)).andExpect(jsonPath("$.gateOpensMinutesBefore").value(45));

        edit(id, "{\"gateOpensMinutesBefore\":90}").andExpect(status().isOk()).andExpect(jsonPath("$.gateOpensMinutesBefore").value(90));
        edit(id, "{\"title\":\"Renamed\"}").andExpect(status().isOk()).andExpect(jsonPath("$.gateOpensMinutesBefore").value(90));
        edit(id, "{\"gateOpensMinutesBefore\":null}").andExpect(status().isOk()).andExpect(jsonPath("$.gateOpensMinutesBefore").doesNotExist());
        assertThat(eventRepository.findById(id).orElseThrow().getGateOpensMinutesBefore()).isNull();

        String logged = String.join("\n", jdbc.queryForList("select details::text from audit_log_entries where action = 'event.updated'"
                + " and event_id = ? order by created_at", String.class, id));
        assertThat(logged).contains("\"field\": \"gateOpensMinutesBefore\"").contains("\"from\": 45").contains("\"to\": 90")
                .contains("{\"to\": null, \"from\": 90, \"field\": \"gateOpensMinutesBefore\"}");
    }

    @Test
    void outsideZeroTo1440IsRefused() throws Exception {
        create(",\"gateOpensMinutesBefore\":1441").andExpect(status().isBadRequest());
        create(",\"gateOpensMinutesBefore\":-1").andExpect(status().isBadRequest());
        UUID id = create("").andExpect(status().isCreated()).andExpect(jsonPath("$.gateOpensMinutesBefore").doesNotExist()).andReturnId();
        edit(id, "{\"gateOpensMinutesBefore\":1441}").andExpect(status().isBadRequest());
        edit(id, "{\"gateOpensMinutesBefore\":0}").andExpect(status().isOk()).andExpect(jsonPath("$.gateOpensMinutesBefore").value(0));
    }

    // ---- helpers ----

    private Created create(String extra) throws Exception {
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        String body = "{\"organizationId\":\"" + orgId + "\",\"title\":\"Gate Test Event\",\"description\":\"desc\",\"category\":\"music\","
                + "\"startAt\":\"" + start + "\",\"endAt\":\"" + start.plus(2, ChronoUnit.HOURS) + "\",\"timezone\":\"UTC\"" + extra + "}";
        return new Created(as(post("/api/v1/events").contentType("application/json").content(body)));
    }

    private ResultActions edit(UUID id, String body) throws Exception {
        return as(patch("/api/v1/events/{id}", id).contentType("application/json").content(body));
    }

    private ResultActions as(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + jwtService.generateAccessToken(owner)));
    }

    /** Tracks a created event for cleanup. */
    private final class Created {
        private final ResultActions result;

        Created(ResultActions result) throws Exception {
            this.result = result;
            if (result.andReturn().getResponse().getStatus() == 201) {
                eventIds.add(andReturnId());
            }
        }

        Created andExpect(org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
            result.andExpect(matcher);
            return this;
        }

        UUID andReturnId() throws Exception {
            return UUID.fromString(objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asText());
        }
    }
}
