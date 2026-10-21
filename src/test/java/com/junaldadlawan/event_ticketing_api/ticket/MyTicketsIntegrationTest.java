package com.junaldadlawan.event_ticketing_api.ticket;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.checkin.entity.CheckInRecord;
import com.junaldadlawan.event_ticketing_api.checkin.entity.ScannerDevice;
import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInResult;
import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInSourceType;
import com.junaldadlawan.event_ticketing_api.checkin.enums.ScannerDeviceStatus;
import com.junaldadlawan.event_ticketing_api.checkin.repository.CheckInRecordRepository;
import com.junaldadlawan.event_ticketing_api.checkin.repository.ScannerDeviceRepository;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /users/me/tickets}: what the caller holds now, from any order; never anyone else's. */
@SpringBootTest
@AutoConfigureMockMvc
class MyTicketsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private ScannerDeviceRepository scannerDeviceRepository;
    @Autowired private CheckInRecordRepository checkInRecordRepository;

    private final List<UUID> ticketIds = new ArrayList<>();
    private UUID orgId;
    private UUID soonEvent;
    private UUID laterEvent;
    private UUID deviceId;
    private User me;
    private User other;

    @BeforeEach
    void setUp() {
        me = user();
        other = user();
        orgId = organizationRepository.save(Organization.builder().name("My Tickets Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED).ownerId(UUID.randomUUID()).documents(List.of()).build()).getId();
        soonEvent = event(5);
        laterEvent = event(30);
        deviceId = UUID.randomUUID();
        scannerDeviceRepository.save(ScannerDevice.builder().id(deviceId).eventId(soonEvent).deviceLabel("Door Scanner")
                .location("Gate C").status(ScannerDeviceStatus.ACTIVE).createdBy("test").build());
    }

    @AfterEach
    void tearDown() {
        ticketIds.forEach(id -> jdbc.update("delete from check_in_records where ticket_id = ?", id));
        ticketIds.forEach(ticketRepository::deleteById);
        scannerDeviceRepository.deleteById(deviceId);
        eventRepository.deleteById(soonEvent);
        eventRepository.deleteById(laterEvent);
        organizationRepository.deleteById(orgId);
    }

    @Test
    void listsWhatICurrentlyHold_fromAnyOrder_soonestEventFirst_withCheckIn() throws Exception {
        UUID myOrder = UUID.randomUUID();
        UUID theirOrder = UUID.randomUUID();
        Ticket bought = ticket(laterEvent, myOrder, me, TicketStatus.VALID);
        Ticket transferredToMe = ticket(soonEvent, theirOrder, me, TicketStatus.USED);
        ticket(laterEvent, myOrder, other, TicketStatus.VALID);        // I transferred it away
        ticket(soonEvent, theirOrder, other, TicketStatus.VALID);      // someone else's
        checkInRecordRepository.save(CheckInRecord.builder().ticketId(transferredToMe.getId()).sourceType(CheckInSourceType.SCANNER_DEVICE)
                .sourceId(deviceId).result(CheckInResult.VALID).scannerLocation("Gate C").build());

        myTickets("").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id").value(contains(transferredToMe.getId().toString(), bought.getId().toString())))
                .andExpect(jsonPath("$.content[0].checkIn.scannerLocation").value("Gate C"))
                .andExpect(jsonPath("$.content[0].checkIn.scannerName").value("Door Scanner"))
                .andExpect(jsonPath("$.content[1].checkIn").doesNotExist());
    }

    @Test
    void filtersByStatus_repeatable() throws Exception {
        Ticket valid = ticket(soonEvent, UUID.randomUUID(), me, TicketStatus.VALID);
        Ticket refunded = ticket(soonEvent, UUID.randomUUID(), me, TicketStatus.REFUNDED);
        ticket(soonEvent, UUID.randomUUID(), me, TicketStatus.CANCELLED);

        myTickets("?status=REFUNDED").andExpect(jsonPath("$.content[*].id").value(contains(refunded.getId().toString())));
        myTickets("?status=VALID&status=REFUNDED").andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(valid.getId().toString(), refunded.getId().toString())));
    }

    @Test
    void someoneHoldingNothing_getsAnEmptyPage_andSignedOutIsRefused() throws Exception {
        ticket(soonEvent, UUID.randomUUID(), other, TicketStatus.VALID);
        myTickets("").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/users/me/tickets")).andExpect(status().isUnauthorized());
    }

    @Test
    void eventIdNarrowsTheListAndTheTotal_andCombinesWithStatus() throws Exception {
        Ticket soonValid = ticket(soonEvent, UUID.randomUUID(), me, TicketStatus.VALID);
        Ticket soonUsed = ticket(soonEvent, UUID.randomUUID(), me, TicketStatus.USED);
        ticket(laterEvent, UUID.randomUUID(), me, TicketStatus.USED);

        myTickets("?eventId=" + soonEvent).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(soonValid.getId().toString(), soonUsed.getId().toString())));
        myTickets("?eventId=" + soonEvent + "&status=USED").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[*].id").value(contains(soonUsed.getId().toString())));
    }

    @Test
    void eventsList_onlyWhatIHoldNow_soonestFirst_andTransferringTheLastTicketAwayDropsIt() throws Exception {
        ticket(soonEvent, UUID.randomUUID(), me, TicketStatus.VALID);
        ticket(soonEvent, UUID.randomUUID(), me, TicketStatus.USED);
        Ticket onlyLater = ticket(laterEvent, UUID.randomUUID(), me, TicketStatus.VALID);
        UUID othersEvent = event(1);
        try {
            ticket(othersEvent, UUID.randomUUID(), other, TicketStatus.VALID);

            myTicketEvents().andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id").value(contains(soonEvent.toString(), laterEvent.toString())))
                    .andExpect(jsonPath("$[0].ticketCount").value(2))
                    .andExpect(jsonPath("$[0].title").value("My Tickets Event"))
                    .andExpect(jsonPath("$[0].timezone").value("UTC"))
                    .andExpect(jsonPath("$[0].startAt").isNotEmpty())
                    .andExpect(jsonPath("$[1].ticketCount").value(1));

            onlyLater.setOwnerId(other.getId());   // transferred away
            ticketRepository.save(onlyLater);
            myTicketEvents().andExpect(jsonPath("$[*].id").value(contains(soonEvent.toString())));
        } finally {
            ticketIds.forEach(ticketRepository::deleteById);
            ticketIds.clear();
            eventRepository.deleteById(othersEvent);
        }
    }

    @Test
    void eventsList_emptyWhenIHoldNothing() throws Exception {
        myTicketEvents().andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/users/me/tickets/events")).andExpect(status().isUnauthorized());
    }

    private ResultActions myTicketEvents() throws Exception {
        return mockMvc.perform(get("/api/v1/users/me/tickets/events").header("Authorization", "Bearer " + jwtService.generateAccessToken(me)));
    }

    private ResultActions myTickets(String query) throws Exception {
        return mockMvc.perform(get("/api/v1/users/me/tickets" + query).header("Authorization", "Bearer " + jwtService.generateAccessToken(me)));
    }

    private Ticket ticket(UUID eventId, UUID orderId, User owner, TicketStatus status) {
        Ticket saved = ticketRepository.save(Ticket.builder().id(UUID.randomUUID()).orderId(orderId).eventId(eventId)
                .ticketTypeId(UUID.randomUUID()).ownerId(owner.getId())
                .ticketNumber("MINE-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .credential("test-credential-" + UUID.randomUUID()).credentialVersion(0).status(status).build());
        ticketIds.add(saved.getId());
        return saved;
    }

    private UUID event(int daysAhead) {
        Instant startAt = Instant.now().plus(daysAhead, ChronoUnit.DAYS);
        return eventRepository.save(Event.builder().organizationId(orgId).title("My Tickets Event").description("desc")
                .category("music").status(EventStatus.PUBLISHED).ticketPrefix("M" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
                .startAt(startAt).endAt(startAt.plus(2, ChronoUnit.HOURS)).timezone("UTC").build()).getId();
    }

    private User user() {
        return User.builder().id(UUID.randomUUID()).email("mine-" + UUID.randomUUID() + "@test.local").role(Role.CUSTOMER).build();
    }
}
