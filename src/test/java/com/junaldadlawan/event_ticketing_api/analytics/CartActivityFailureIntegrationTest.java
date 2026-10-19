package com.junaldadlawan.event_ticketing_api.analytics;

import com.junaldadlawan.event_ticketing_api.analytics.repository.FunnelRepository;
import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.enums.TicketTypeKind;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A cart log write that fails in the database (here: a NOT NULL violation, which aborts a Postgres transaction) must not fail or undo the cart
 * change it records: the recorder writes under a savepoint and rolls back to it.
 */
@SpringBootTest(properties = "app.analytics.cart-activity.enabled=true")
@AutoConfigureMockMvc
class CartActivityFailureIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private TicketTypeRepository ticketTypeRepository;
    @MockitoBean
    private FunnelRepository funnelRepository;

    private UUID orgId;
    private UUID eventId;
    private UUID ticketTypeId;
    private UUID cartId;
    private UUID buyerId;

    @AfterEach
    void tearDown() {
        if (cartId != null) {
            jdbc.update("delete from cart_items where cart_id = ?", cartId);
            jdbc.update("delete from carts where id = ?", cartId);
        }
        ticketTypeRepository.deleteById(ticketTypeId);
        eventRepository.deleteById(eventId);
        organizationRepository.deleteById(orgId);
        jdbc.update("delete from audit_log_entries where actor_id = ?", buyerId);
    }

    @Test
    void aFailedLogWrite_leavesTheCartChangeInPlace() throws Exception {
        orgId = organizationRepository.save(Organization.builder().name("Funnel Failure Org " + UUID.randomUUID()).status(OrganizationStatus.APPROVED)
                .ownerId(UUID.randomUUID()).documents(List.of()).build()).getId();
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        eventId = eventRepository.save(Event.builder().organizationId(orgId).title("Funnel Failure Event").description("d").category("music")
                .status(EventStatus.PUBLISHED).ticketPrefix("X" + UUID.randomUUID().toString().substring(0, 2).toUpperCase()).startAt(start)
                .endAt(start.plus(2, ChronoUnit.HOURS)).timezone("UTC").build()).getId();
        ticketTypeId = ticketTypeRepository.save(TicketType.builder().eventId(eventId).name("GA").kind(TicketTypeKind.GENERAL_ADMISSION)
                .price(Money.builder().amount(1000L).currency("USD").build()).quantityTotal(10).quantityAvailable(10)
                .saleStartAt(Instant.now().minus(1, ChronoUnit.DAYS)).saleEndAt(Instant.now().plus(5, ChronoUnit.DAYS)).maxPerOrder(10).build()).getId();
        // the log write runs a statement Postgres rejects inside the cart's transaction
        doAnswer(invocation -> jdbc.update("insert into cart_activity (id) values (?)", UUID.randomUUID()))
                .when(funnelRepository).insertCartActivity(any(), any(), any(), anyInt(), any(), any());

        buyerId = UUID.randomUUID();
        String buyer = "Bearer " + jwtService.generateAccessToken(User.builder().id(buyerId).email("funnel-fail-" + UUID.randomUUID() + "@test.local")
                .role(Role.CUSTOMER).build());
        cartId = UUID.fromString(objectMapper.readTree(mockMvc.perform(post("/api/v1/carts").header("Authorization", buyer))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText());
        mockMvc.perform(post("/api/v1/carts/{id}/items", cartId).header("Authorization", buyer).contentType("application/json")
                .content("{\"ticketTypeId\":\"" + ticketTypeId + "\",\"quantity\":2}")).andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("select coalesce(sum(quantity), 0) from cart_items where cart_id = ?", Integer.class, cartId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select quantity_available from ticket_types where id = ?", Integer.class, ticketTypeId)).isEqualTo(8);
        assertThat(jdbc.queryForObject("select count(*) from cart_activity where event_id = ?", Integer.class, eventId)).isZero();
    }
}
