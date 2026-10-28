package com.junaldadlawan.event_ticketing_api.checkin;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.common.TestTicketPrefixes;
import com.junaldadlawan.event_ticketing_api.checkin.entity.CheckInRecord;
import com.junaldadlawan.event_ticketing_api.checkin.entity.FallbackScanRecord;
import com.junaldadlawan.event_ticketing_api.checkin.entity.ScannerDevice;
import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInResult;
import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInSourceType;
import com.junaldadlawan.event_ticketing_api.checkin.enums.ScannerDeviceStatus;
import com.junaldadlawan.event_ticketing_api.checkin.repository.CheckInRecordRepository;
import com.junaldadlawan.event_ticketing_api.checkin.repository.FallbackScanRecordRepository;
import com.junaldadlawan.event_ticketing_api.checkin.repository.ScannerDeviceRepository;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationMemberRepository;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.enums.TicketTypeKind;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** An event's scan history, summary and scanner devices: read only, for the organization's team and admins, with no buyer detail. */
@SpringBootTest
@AutoConfigureMockMvc
class EventCheckInHistoryIntegrationTest {

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
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private TicketTypeRepository ticketTypeRepository;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private ScannerDeviceRepository scannerDeviceRepository;
    @Autowired
    private FallbackScanRecordRepository fallbackRepository;
    @Autowired
    private CheckInRecordRepository recordRepository;

    private final List<UUID> orgIds = new ArrayList<>();
    private final List<UUID> eventIds = new ArrayList<>();
    private final List<UUID> typeIds = new ArrayList<>();
    private final List<UUID> ticketIds = new ArrayList<>();
    private final List<UUID> deviceIds = new ArrayList<>();
    private final List<UUID> fallbackIds = new ArrayList<>();
    private final List<OrganizationMember> members = new ArrayList<>();

    private String staff;
    private String owner;
    private String admin;
    private String stranger;
    private UUID eventId;
    private UUID gateA;
    private UUID gateB;

    @BeforeEach
    void setUp() {
        User staffUser = user(Role.CUSTOMER);
        User ownerUser = user(Role.CUSTOMER);
        staff = token(staffUser);
        owner = token(ownerUser);
        admin = token(user(Role.ADMIN));
        stranger = token(user(Role.CUSTOMER));
        UUID org = organizationRepository.save(Organization.builder().name("History Test Org " + UUID.randomUUID()).status(OrganizationStatus.APPROVED)
                .ownerId(ownerUser.getId()).documents(List.of()).build()).getId();
        orgIds.add(org);
        member(staffUser.getId(), org, OrganizationRole.CHECK_IN_STAFF);
        member(ownerUser.getId(), org, OrganizationRole.OWNER);
        eventId = event(org);
        UUID otherEvent = event(org);

        UUID type = ticketTypeRepository.save(TicketType.builder().eventId(eventId).name("VIP").kind(TicketTypeKind.GENERAL_ADMISSION)
                .price(Money.builder().amount(1000).currency("PHP").build()).quantityTotal(10).quantityAvailable(10)
                .saleStartAt(Instant.now().minus(1, ChronoUnit.DAYS)).saleEndAt(Instant.now().plus(5, ChronoUnit.DAYS)).maxPerOrder(5).build()).getId();
        typeIds.add(type);
        gateA = device(eventId, "Gate A", "North");
        gateB = device(eventId, "Gate B", null);
        UUID gateOther = device(otherEvent, "Other event gate", null);

        Ticket t1 = ticket(eventId, type, "HST-AAA111", TicketStatus.USED);
        Ticket t2 = ticket(eventId, type, "HST-BBB222", TicketStatus.VALID);
        Ticket t3 = ticket(eventId, type, "HST-CCC333", TicketStatus.REFUNDED);   // not admittable
        Ticket foreign = ticket(otherEvent, type, "HST-ZZZ999", TicketStatus.USED);

        record(t1, CheckInSourceType.SCANNER_DEVICE, gateA, CheckInResult.VALID, "North", 5);
        record(t1, CheckInSourceType.SCANNER_DEVICE, gateB, CheckInResult.DUPLICATE, null, 4);
        record(t2, CheckInSourceType.SCANNER_DEVICE, gateA, CheckInResult.WRONG_EVENT, "North", 3);
        UUID fallback = fallbackRepository.save(FallbackScanRecord.builder().eventId(eventId).rawCredential("raw").capturedAt(Instant.now()).build()).getId();
        fallbackIds.add(fallback);
        record(t2, CheckInSourceType.RECONCILED_FALLBACK, fallback, CheckInResult.VALID, "Side door", 2);
        record(foreign, CheckInSourceType.SCANNER_DEVICE, gateOther, CheckInResult.VALID, null, 1);   // another event: never shown
        ticketIds.size();
    }

    @AfterEach
    void tearDown() {
        ticketIds.forEach(id -> jdbc.update("delete from check_in_records where ticket_id = ?", id));
        ticketIds.forEach(ticketRepository::deleteById);
        fallbackIds.forEach(fallbackRepository::deleteById);
        deviceIds.forEach(scannerDeviceRepository::deleteById);
        typeIds.forEach(ticketTypeRepository::deleteById);
        members.forEach(m -> organizationMemberRepository.findByUserIdAndOrganizationId(m.getUserId(), m.getOrganizationId())
                .ifPresent(organizationMemberRepository::delete));
        eventIds.forEach(eventRepository::deleteById);
        orgIds.forEach(organizationRepository::deleteById);
    }

    // ---- fixtures ----

    private User user(Role role) {
        return User.builder().id(UUID.randomUUID()).email("history-" + UUID.randomUUID() + "@test.local").role(role).build();
    }

    private String token(User user) {
        return "Bearer " + jwtService.generateAccessToken(user);
    }

    private void member(UUID userId, UUID org, OrganizationRole role) {
        OrganizationMember member = OrganizationMember.builder().userId(userId).organizationId(org).build();
        member.getRoles().add(role);
        members.add(organizationMemberRepository.save(member));
    }

    private UUID event(UUID org) {
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        UUID id = eventRepository.save(Event.builder().organizationId(org).title("History Test Event").description("d").category("music")
                .status(EventStatus.PUBLISHED).ticketPrefix(TestTicketPrefixes.free(jdbc))
                .startAt(start).endAt(start.plus(2, ChronoUnit.HOURS)).timezone("UTC").build()).getId();
        eventIds.add(id);
        return id;
    }

    private UUID device(UUID event, String label, String location) {
        UUID id = UUID.randomUUID();
        scannerDeviceRepository.save(ScannerDevice.builder().id(id).eventId(event).deviceLabel(label).location(location)
                .status(ScannerDeviceStatus.ACTIVE).createdBy("test").build());
        deviceIds.add(id);
        return id;
    }

    private Ticket ticket(UUID event, UUID type, String number, TicketStatus status) {
        UUID id = UUID.randomUUID();
        Ticket saved = ticketRepository.save(Ticket.builder().id(id).orderId(UUID.randomUUID()).eventId(event).ticketTypeId(type).seatId(null)
                .ownerId(UUID.randomUUID()).ticketNumber(number).credential("cred-" + id).credentialVersion(0).status(status).build());
        ticketIds.add(saved.getId());
        return saved;
    }

    private void record(Ticket ticket, CheckInSourceType source, UUID sourceId, CheckInResult result, String location, int minutesAgo) {
        UUID id = recordRepository.save(CheckInRecord.builder().ticketId(ticket.getId()).sourceType(source).sourceId(sourceId).result(result)
                .scannerLocation(location).build()).getId();
        jdbc.update("update check_in_records set scanned_at = ? where id = ?", java.sql.Timestamp.from(Instant.now().minus(minutesAgo, ChronoUnit.MINUTES)), id);
    }

    // ---- tests ----

    @Test
    void theTeamSeesTheEventsScansNewestFirst_withoutBuyerDetail_andNothingOfOtherEvents() throws Exception {
        for (String caller : new String[] {staff, owner, admin}) {
            mockMvc.perform(get("/api/v1/events/{id}/check-in-records", eventId).header("Authorization", caller))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(4))
                    .andExpect(jsonPath("$.content[0].ticketNumber").value("HST-BBB222"))   // the newest: the reconciled fallback scan
                    .andExpect(jsonPath("$.content[0].sourceType").value("RECONCILED_FALLBACK"))
                    .andExpect(jsonPath("$.content[0].scannerLocation").value("Side door"))
                    .andExpect(jsonPath("$.content[0].scannerName").doesNotExist())
                    .andExpect(jsonPath("$.content[0].ticketTypeName").value("VIP"))
                    .andExpect(jsonPath("$.content[3].scannerName").value("Gate A"))      // the oldest
                    .andExpect(jsonPath("$.content[3].result").value("VALID"))
                    .andExpect(jsonPath("$.content[0].ownerId").doesNotExist())
                    .andExpect(jsonPath("$.content[0].buyerId").doesNotExist());
        }
        mockMvc.perform(get("/api/v1/events/{id}/check-in-records", eventId).header("Authorization", stranger)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/events/{id}/check-in-records", eventId)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/events/{id}/check-in-records", UUID.randomUUID()).header("Authorization", staff)).andExpect(status().isNotFound());
    }

    @Test
    void theFiltersAndPagingNarrowTheHistory_andTheSummaryFollowsThem() throws Exception {
        mockMvc.perform(get("/api/v1/events/{id}/check-in-records", eventId).param("result", "VALID").header("Authorization", staff))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/v1/events/{id}/check-in-records", eventId).param("sourceType", "RECONCILED_FALLBACK").header("Authorization", staff))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/events/{id}/check-in-records", eventId).param("scannerDeviceId", gateB.toString()).header("Authorization", staff))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].result").value("DUPLICATE"));
        mockMvc.perform(get("/api/v1/events/{id}/check-in-records", eventId).param("ticketNumber", "aaa1").header("Authorization", staff))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/v1/events/{id}/check-in-records", eventId).param("size", "3").param("page", "1").header("Authorization", staff))
                .andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.totalPage").value(2)).andExpect(jsonPath("$.last").value(true));
        mockMvc.perform(get("/api/v1/events/{id}/check-in-records", eventId).param("result", "NOPE").header("Authorization", staff)).andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/events/{id}/check-in-summary", eventId).header("Authorization", staff)).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4)).andExpect(jsonPath("$.valid").value(2)).andExpect(jsonPath("$.duplicate").value(1))
                .andExpect(jsonPath("$.invalid").value(0)).andExpect(jsonPath("$.wrongEvent").value(1)).andExpect(jsonPath("$.online").value(3))
                .andExpect(jsonPath("$.offline").value(1)).andExpect(jsonPath("$.uniqueTicketsAdmitted").value(2))
                .andExpect(jsonPath("$.ticketsAdmittable").value(2));   // the refunded ticket is not admittable
        mockMvc.perform(get("/api/v1/events/{id}/check-in-summary", eventId).param("sourceType", "SCANNER_DEVICE").header("Authorization", staff))
                .andExpect(jsonPath("$.total").value(3)).andExpect(jsonPath("$.offline").value(0)).andExpect(jsonPath("$.uniqueTicketsAdmitted").value(1));
        mockMvc.perform(get("/api/v1/events/{id}/check-in-summary", eventId).header("Authorization", stranger)).andExpect(status().isForbidden());
    }

    @Test
    void scannerDevicesForTheFilter_neverShowACredential_andOnlyToTheTeam() throws Exception {
        mockMvc.perform(get("/api/v1/events/{id}/scanner-devices", eventId).header("Authorization", staff)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].name").value("Gate A")).andExpect(jsonPath("$[0].location").value("North"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE")).andExpect(jsonPath("$[0].credential").doesNotExist());
        mockMvc.perform(get("/api/v1/events/{id}/scanner-devices", eventId).header("Authorization", stranger)).andExpect(status().isForbidden());
    }
}
