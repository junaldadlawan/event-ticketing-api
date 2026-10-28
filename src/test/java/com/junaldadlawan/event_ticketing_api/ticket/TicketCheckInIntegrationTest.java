package com.junaldadlawan.event_ticketing_api.ticket;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.common.TestTicketPrefixes;
import com.junaldadlawan.event_ticketing_api.checkin.entity.ScannerDevice;
import com.junaldadlawan.event_ticketing_api.checkin.enums.ScannerDeviceStatus;
import com.junaldadlawan.event_ticketing_api.checkin.repository.CheckInRecordRepository;
import com.junaldadlawan.event_ticketing_api.checkin.repository.ScannerDeviceRepository;
import com.junaldadlawan.event_ticketing_api.checkin.security.ScannerDeviceCredentialService;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.order.entity.Order;
import com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus;
import com.junaldadlawan.event_ticketing_api.order.enums.PayeeType;
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import com.junaldadlawan.event_ticketing_api.ticket.service.TicketCredentialService;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A ticket holder sees when and where their ticket was checked in ({@code Ticket.checkIn}), and only the valid scan. */
@SpringBootTest
@AutoConfigureMockMvc
class TicketCheckInIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;
    @Autowired private ScannerDeviceCredentialService deviceCredentials;
    @Autowired private TicketCredentialService ticketCredentials;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private ScannerDeviceRepository scannerDeviceRepository;
    @Autowired private CheckInRecordRepository checkInRecordRepository;

    private UUID orgId;
    private UUID eventId;
    private UUID orderId;
    private UUID deviceId;
    private User buyer;
    private Ticket used;
    private Ticket unused;

    @BeforeEach
    void setUp() {
        buyer = User.builder().id(UUID.randomUUID()).email("holder-" + UUID.randomUUID() + "@test.local").role(Role.CUSTOMER).build();
        orgId = organizationRepository.save(Organization.builder().name("CheckIn View Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED).ownerId(UUID.randomUUID()).documents(List.of()).build()).getId();
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        eventId = eventRepository.save(Event.builder().organizationId(orgId).title("CheckIn View Event").description("desc")
                .category("music").status(EventStatus.PUBLISHED).ticketPrefix(TestTicketPrefixes.free(jdbc))
                .startAt(startAt).endAt(startAt.plus(2, ChronoUnit.HOURS)).timezone("UTC").build()).getId();
        orderId = orderRepository.save(Order.builder().buyerId(buyer.getId()).payeeType(PayeeType.ORGANIZATION).payeeId(orgId)
                .status(OrderStatus.PAID).total(Money.builder().amount(1000L).currency("USD").build())
                .createdBy(buyer.getId().toString()).build()).getId();
        deviceId = UUID.randomUUID();
        scannerDeviceRepository.save(ScannerDevice.builder().id(deviceId).eventId(eventId).deviceLabel("North Scanner")
                .location("Gate A").status(ScannerDeviceStatus.ACTIVE).createdBy("test").build());
        used = ticket();
        unused = ticket();
    }

    @AfterEach
    void tearDown() {
        for (Ticket t : List.of(used, unused)) {
            jdbc.update("delete from check_in_records where ticket_id = ?", t.getId());
            ticketRepository.deleteById(t.getId());
        }
        scannerDeviceRepository.deleteById(deviceId);
        orderRepository.deleteById(orderId);
        jdbc.update("delete from audit_log_entries where event_id = ?", eventId);
        eventRepository.deleteById(eventId);
        organizationRepository.deleteById(orgId);
    }

    @Test
    void theHolderSeesTheValidScan_onTheTicketAndTheOrder_andALaterDuplicateDoesNotChangeIt() throws Exception {
        scan(used).andExpect(jsonPath("$.result").value("VALID"));
        String scannedAt = checkInRecordRepository.findByTicketIdOrderByScannedAtAsc(used.getId()).get(0).getScannedAt().toString();
        scan(used).andExpect(jsonPath("$.result").value("DUPLICATE"));

        asBuyer(get("/api/v1/tickets/{ticketId}", used.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("USED"))
                .andExpect(jsonPath("$.checkIn.scannedAt").value(scannedAt))
                .andExpect(jsonPath("$.checkIn.scannerLocation").value("Gate A"))
                .andExpect(jsonPath("$.checkIn.scannerId").value(deviceId.toString()))
                .andExpect(jsonPath("$.checkIn.scannerName").value("North Scanner"));
        asBuyer(get("/api/v1/tickets/{ticketId}", unused.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkIn").doesNotExist());

        asBuyer(get("/api/v1/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickets[?(@.id == '" + used.getId() + "')].checkIn.scannedAt").value(scannedAt))
                .andExpect(jsonPath("$.tickets[?(@.id == '" + used.getId() + "')].checkIn.scannerName").value("North Scanner"))
                .andExpect(jsonPath("$.tickets[?(@.id == '" + unused.getId() + "')].checkIn").value(contains(nullValue())));
    }

    private ResultActions scan(Ticket ticket) throws Exception {
        return mockMvc.perform(post("/api/v1/check-in/validate").header("Authorization", "Bearer " + deviceCredentials.generate(deviceId))
                .contentType("application/json")
                .content("{\"credential\":\"" + ticket.getCredential() + "\",\"deviceId\":\"" + deviceId + "\"}"))
                .andExpect(status().isOk());
    }

    private ResultActions asBuyer(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + jwtService.generateAccessToken(buyer)));
    }

    private Ticket ticket() {
        UUID id = UUID.randomUUID();
        return ticketRepository.save(Ticket.builder().id(id).orderId(orderId).eventId(eventId).ticketTypeId(UUID.randomUUID())
                .ownerId(buyer.getId()).ticketNumber("VIEW-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .credential(ticketCredentials.generate(id, 0)).credentialVersion(0).status(TicketStatus.VALID).build());
    }
}
