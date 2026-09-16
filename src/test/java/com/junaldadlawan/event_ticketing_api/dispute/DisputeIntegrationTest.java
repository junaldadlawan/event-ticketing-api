package com.junaldadlawan.event_ticketing_api.dispute;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.dispute.entity.Dispute;
import com.junaldadlawan.event_ticketing_api.dispute.enums.DisputeStatus;
import com.junaldadlawan.event_ticketing_api.dispute.repository.DisputeRepository;
import com.junaldadlawan.event_ticketing_api.notification.repository.NotificationRepository;
import com.junaldadlawan.event_ticketing_api.order.entity.Order;
import com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus;
import com.junaldadlawan.event_ticketing_api.order.enums.PayeeType;
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full {@code @SpringBootTest} coverage for {@code /api/v1/disputes/**}
 * against real Postgres + real signed JWTs + the real SecurityConfig filter
 * chain — mirrors {@code RefundIntegrationTest}. Covers the four scenarios
 * in {@code testing/dispute-test-plan.md} plus the terminal-state guard and
 * the DISPUTE_RESOLVED notification trigger.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DisputeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private DisputeRepository disputeRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private final List<UUID> createdOrderIds = new ArrayList<>();
    private final List<UUID> createdTicketIds = new ArrayList<>();
    private final List<UUID> createdDisputeIds = new ArrayList<>();
    private final List<UUID> createdNotificationIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (UUID id : createdDisputeIds) {
            disputeRepository.deleteById(id);
        }
        createdDisputeIds.clear();
        for (UUID id : createdNotificationIds) {
            notificationRepository.deleteById(id);
        }
        createdNotificationIds.clear();
        for (UUID id : createdOrderIds) {
            orderRepository.deleteById(id);
        }
        createdOrderIds.clear();
        for (UUID id : createdTicketIds) {
            ticketRepository.deleteById(id);
        }
        createdTicketIds.clear();
    }

    private User user(Role role) {
        return User.builder().id(UUID.randomUUID()).email(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local").role(role).build();
    }

    private UUID persistOrder(UUID buyerId) {
        Order order = Order.builder().buyerId(buyerId).payeeType(PayeeType.ORGANIZATION).payeeId(UUID.randomUUID())
                .status(OrderStatus.PAID)
                .total(Money.builder().amount(1000L).currency("USD").build()).createdBy("System Audit").build();
        Order saved = orderRepository.save(order);
        createdOrderIds.add(saved.getId());
        return saved.getId();
    }

    private UUID persistTicket(UUID ownerId) {
        Ticket ticket = Ticket.builder().id(UUID.randomUUID()).orderId(UUID.randomUUID()).eventId(UUID.randomUUID())
                .ticketTypeId(UUID.randomUUID()).ownerId(ownerId).ticketNumber("ABC-000001")
                .credential("cred").status(TicketStatus.VALID).build();
        Ticket saved = ticketRepository.save(ticket);
        createdTicketIds.add(saved.getId());
        return saved.getId();
    }

    private UUID trackDispute(MvcResult result) throws Exception {
        UUID id = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        createdDisputeIds.add(id);
        return id;
    }

    // ---- create() : raise a dispute against an order or ticket ----

    @Test
    void create_orderBuyer_returns201() throws Exception {
        User buyer = user(Role.CUSTOMER);
        String token = jwtService.generateAccessToken(buyer);
        UUID orderId = persistOrder(buyer.getId());

        MvcResult result = mockMvc.perform(post("/api/v1/disputes")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"orderId\":\"" + orderId + "\",\"reason\":\"never received tickets\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.raisedBy").value(buyer.getId().toString()))
                .andReturn();
        trackDispute(result);
    }

    @Test
    void create_ticketOwner_returns201() throws Exception {
        User owner = user(Role.CUSTOMER);
        String token = jwtService.generateAccessToken(owner);
        UUID ticketId = persistTicket(owner.getId());

        MvcResult result = mockMvc.perform(post("/api/v1/disputes")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"ticketId\":\"" + ticketId + "\",\"reason\":\"counterfeit ticket\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        trackDispute(result);
    }

    @Test
    void create_orderNonBuyer_returns403() throws Exception {
        User buyer = user(Role.CUSTOMER);
        User stranger = user(Role.CUSTOMER);
        String strangerToken = jwtService.generateAccessToken(stranger);
        UUID orderId = persistOrder(buyer.getId());

        mockMvc.perform(post("/api/v1/disputes")
                        .header("Authorization", "Bearer " + strangerToken)
                        .contentType("application/json")
                        .content("{\"orderId\":\"" + orderId + "\",\"reason\":\"reason\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_admin_canRaiseAgainstAnyoneElsesOrder() throws Exception {
        User buyer = user(Role.CUSTOMER);
        User admin = user(Role.ADMIN);
        String adminToken = jwtService.generateAccessToken(admin);
        UUID orderId = persistOrder(buyer.getId());

        MvcResult result = mockMvc.perform(post("/api/v1/disputes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"orderId\":\"" + orderId + "\",\"reason\":\"raised on buyer's behalf\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        trackDispute(result);
    }

    @Test
    void create_neitherOrderNorTicket_returns400() throws Exception {
        User buyer = user(Role.CUSTOMER);
        String token = jwtService.generateAccessToken(buyer);

        mockMvc.perform(post("/api/v1/disputes")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"reason\":\"reason\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/disputes")
                        .contentType("application/json")
                        .content("{\"orderId\":\"" + UUID.randomUUID() + "\",\"reason\":\"reason\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- get() : the raiser, or admin ----

    @Test
    void get_raiser_returns200() throws Exception {
        User buyer = user(Role.CUSTOMER);
        String token = jwtService.generateAccessToken(buyer);
        UUID orderId = persistOrder(buyer.getId());
        MvcResult created = mockMvc.perform(post("/api/v1/disputes")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"orderId\":\"" + orderId + "\",\"reason\":\"reason\"}"))
                .andExpect(status().isCreated()).andReturn();
        UUID disputeId = trackDispute(created);

        mockMvc.perform(get("/api/v1/disputes/{id}", disputeId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(disputeId.toString()));
    }

    @Test
    void get_stranger_returns403() throws Exception {
        User buyer = user(Role.CUSTOMER);
        User stranger = user(Role.CUSTOMER);
        String buyerToken = jwtService.generateAccessToken(buyer);
        String strangerToken = jwtService.generateAccessToken(stranger);
        UUID orderId = persistOrder(buyer.getId());
        MvcResult created = mockMvc.perform(post("/api/v1/disputes")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content("{\"orderId\":\"" + orderId + "\",\"reason\":\"reason\"}"))
                .andExpect(status().isCreated()).andReturn();
        UUID disputeId = trackDispute(created);

        mockMvc.perform(get("/api/v1/disputes/{id}", disputeId).header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
    }

    // ---- list()/update() : admin only ----

    @Test
    void list_nonAdmin_returns403() throws Exception {
        User buyer = user(Role.CUSTOMER);
        String token = jwtService.generateAccessToken(buyer);

        mockMvc.perform(get("/api/v1/disputes").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_admin_returns200() throws Exception {
        User admin = user(Role.ADMIN);
        String token = jwtService.generateAccessToken(admin);

        mockMvc.perform(get("/api/v1/disputes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").exists());
    }

    @Test
    void update_nonAdmin_returns403() throws Exception {
        User buyer = user(Role.CUSTOMER);
        String buyerToken = jwtService.generateAccessToken(buyer);
        UUID orderId = persistOrder(buyer.getId());
        MvcResult created = mockMvc.perform(post("/api/v1/disputes")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content("{\"orderId\":\"" + orderId + "\",\"reason\":\"reason\"}"))
                .andExpect(status().isCreated()).andReturn();
        UUID disputeId = trackDispute(created);

        mockMvc.perform(patch("/api/v1/disputes/{id}", disputeId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_admin_resolvesDispute_notifiesRaiser() throws Exception {
        User buyer = user(Role.CUSTOMER);
        User admin = user(Role.ADMIN);
        String buyerToken = jwtService.generateAccessToken(buyer);
        String adminToken = jwtService.generateAccessToken(admin);
        UUID orderId = persistOrder(buyer.getId());
        MvcResult created = mockMvc.perform(post("/api/v1/disputes")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content("{\"orderId\":\"" + orderId + "\",\"reason\":\"reason\"}"))
                .andExpect(status().isCreated()).andReturn();
        UUID disputeId = trackDispute(created);

        mockMvc.perform(patch("/api/v1/disputes/{id}", disputeId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"status\":\"RESOLVED\",\"resolution\":\"refund issued\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.updatedBy").value(admin.getId().toString()));

        Dispute persisted = disputeRepository.findById(disputeId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(DisputeStatus.RESOLVED);
        assertThat(persisted.getResolution()).isEqualTo("refund issued");
        assertThat(persisted.getUpdatedBy()).isEqualTo(admin.getId());

        // Best-effort DISPUTE_RESOLVED notification was recorded for the raiser.
        var notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(buyer.getId());
        assertThat(notifications).isNotEmpty();
        notifications.forEach(n -> createdNotificationIds.add(n.getId()));
    }

    @Test
    void update_alreadyResolved_returns409() throws Exception {
        User buyer = user(Role.CUSTOMER);
        User admin = user(Role.ADMIN);
        String buyerToken = jwtService.generateAccessToken(buyer);
        String adminToken = jwtService.generateAccessToken(admin);
        UUID orderId = persistOrder(buyer.getId());
        MvcResult created = mockMvc.perform(post("/api/v1/disputes")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content("{\"orderId\":\"" + orderId + "\",\"reason\":\"reason\"}"))
                .andExpect(status().isCreated()).andReturn();
        UUID disputeId = trackDispute(created);
        mockMvc.perform(patch("/api/v1/disputes/{id}", disputeId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"status\":\"DISMISSED\"}"))
                .andExpect(status().isOk());

        var notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(buyer.getId());
        notifications.forEach(n -> createdNotificationIds.add(n.getId()));

        mockMvc.perform(patch("/api/v1/disputes/{id}", disputeId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void update_noToken_returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/disputes/{id}", UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isUnauthorized());
    }
}
