package com.junaldadlawan.event_ticketing_api.moderation;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.cart.entity.Cart;
import com.junaldadlawan.event_ticketing_api.cart.entity.CartItem;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartItemRepository;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartRepository;
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
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.enums.TicketTypeKind;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.AccountStatus;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full {@code @SpringBootTest} proof that {@code SUSPENDED} is a REAL,
 * enforced status - not just recorded in the moderation log (BR-ADMIN-002).
 * Exercises the exact four wiring points the Phase 12 dispatch listed:
 * {@code EventServiceImpl.updateEvent}'s new suspended-event guard, the
 * already-correct exclusions (publish requires DRAFT, cart add-item's
 * allow-list, public event search's PUBLISHED-only filter), and {@code
 * VenueServiceImpl.create}'s new organization-APPROVED gate, plus {@code
 * AuthServiceImpl.login}'s new suspended-account 403 (checked only after a
 * correct password, so a wrong password on a suspended account still 401s).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SuspensionEnforcementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private TicketTypeRepository ticketTypeRepository;
    @Autowired
    private CartRepository cartRepository;
    @Autowired
    private CartItemRepository cartItemRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final List<UUID> createdOrgIds = new ArrayList<>();
    private final List<UUID> createdEventIds = new ArrayList<>();
    private final List<UUID> createdTicketTypeIds = new ArrayList<>();
    private final List<UUID> createdCartIds = new ArrayList<>();
    private final List<UUID> createdCartItemIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<OrganizationMember> createdMembers = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (OrganizationMember member : createdMembers) {
            organizationMemberRepository.findByUserIdAndOrganizationId(member.getUserId(), member.getOrganizationId())
                    .ifPresent(organizationMemberRepository::delete);
        }
        createdMembers.clear();
        for (UUID id : createdCartItemIds) {
            cartItemRepository.deleteById(id);
        }
        createdCartItemIds.clear();
        for (UUID id : createdCartIds) {
            cartRepository.deleteById(id);
        }
        createdCartIds.clear();
        for (UUID id : createdTicketTypeIds) {
            ticketTypeRepository.deleteById(id);
        }
        createdTicketTypeIds.clear();
        for (UUID id : createdEventIds) {
            eventRepository.deleteById(id);
        }
        createdEventIds.clear();
        for (UUID id : createdOrgIds) {
            organizationRepository.deleteById(id);
        }
        createdOrgIds.clear();
        for (UUID id : createdUserIds) {
            userRepository.deleteById(id);
        }
        createdUserIds.clear();
    }

    private User inMemoryUser(Role role) {
        return User.builder().id(UUID.randomUUID()).email(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local").role(role).build();
    }

    private UUID persistOrganization(OrganizationStatus status, UUID ownerId) {
        Organization organization = Organization.builder().name("Suspension Test Org " + UUID.randomUUID()).status(status).ownerId(ownerId).documents(List.of()).build();
        Organization saved = organizationRepository.save(organization);
        createdOrgIds.add(saved.getId());
        return saved.getId();
    }

    private void grantOrgRole(UUID userId, UUID organizationId, OrganizationRole role) {
        OrganizationMember member = OrganizationMember.builder().userId(userId).organizationId(organizationId).build();
        member.getRoles().add(role);
        createdMembers.add(organizationMemberRepository.save(member));
    }

    private UUID persistEvent(UUID organizationId, EventStatus status) {
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        Event event = Event.builder().organizationId(organizationId).title("Suspension Test Event").description("d")
                .category("music").status(status).ticketPrefix("S" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
                .startAt(startAt).endAt(startAt.plus(2, ChronoUnit.HOURS)).timezone("UTC").build();
        Event saved = eventRepository.save(event);
        createdEventIds.add(saved.getId());
        return saved.getId();
    }

    private UUID persistGaTicketType(UUID eventId) {
        TicketType tt = TicketType.builder().eventId(eventId).name("GA").kind(TicketTypeKind.GENERAL_ADMISSION)
                .price(Money.builder().amount(1000L).currency("USD").build()).quantityTotal(5).quantityAvailable(5)
                .saleStartAt(Instant.now().minus(1, ChronoUnit.DAYS)).saleEndAt(Instant.now().plus(5, ChronoUnit.DAYS))
                .maxPerOrder(10).build();
        TicketType saved = ticketTypeRepository.save(tt);
        createdTicketTypeIds.add(saved.getId());
        return saved.getId();
    }

    // ---- 1. EventServiceImpl.updateEvent: suspended event cannot be updated ----

    @Test
    void updateEvent_suspended_returns409() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        String token = jwtService.generateAccessToken(owner);
        UUID orgId = persistOrganization(OrganizationStatus.APPROVED, owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        // Directly persisted as SUSPENDED (ModerationActionIntegrationTest
        // already proves the real moderation endpoint produces this state).
        UUID eventId = persistEvent(orgId, EventStatus.SUSPENDED);

        mockMvc.perform(patch("/api/v1/events/{eventId}", eventId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"title\":\"New Title\"}"))
                .andExpect(status().isConflict());
    }

    // ---- 2. EventServiceImpl.publishEvent: already excluded (status != DRAFT) ----

    @Test
    void publishEvent_suspended_returns409() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        String token = jwtService.generateAccessToken(owner);
        UUID orgId = persistOrganization(OrganizationStatus.APPROVED, owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId, EventStatus.SUSPENDED);

        mockMvc.perform(post("/api/v1/events/{eventId}/publish", eventId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    // ---- 3. CartServiceImpl.addItem: already excluded by the allow-list ----

    @Test
    void addCartItem_suspendedEvent_returns409() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        String buyerToken = jwtService.generateAccessToken(buyer);
        UUID orgId = persistOrganization(OrganizationStatus.APPROVED, owner.getId());
        UUID eventId = persistEvent(orgId, EventStatus.SUSPENDED);
        UUID ticketTypeId = persistGaTicketType(eventId);

        var cartResult = mockMvc.perform(post("/api/v1/carts").header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isCreated()).andReturn();
        UUID cartId = UUID.fromString(new tools.jackson.databind.ObjectMapper().readTree(cartResult.getResponse().getContentAsString()).get("id").asText());
        createdCartIds.add(cartId);

        mockMvc.perform(post("/api/v1/carts/{cartId}/items", cartId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content("{\"ticketTypeId\":\"" + ticketTypeId + "\",\"quantity\":1}"))
                .andExpect(status().isConflict());
    }

    // ---- 4. EventServiceImpl.listEvents: already excludes non-PUBLISHED (incl. SUSPENDED) ----

    @Test
    void publicEventSearch_excludesSuspendedEvent() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(OrganizationStatus.APPROVED, owner.getId());
        UUID suspendedEventId = persistEvent(orgId, EventStatus.SUSPENDED);

        mockMvc.perform(get("/api/v1/events").param("keyword", "Suspension Test Event"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id=='" + suspendedEventId + "')]").doesNotExist());
    }

    // ---- 5. VenueServiceImpl.create: suspended organization cannot create venues ----

    @Test
    void createVenue_suspendedOrganization_returns403() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        String token = jwtService.generateAccessToken(owner);
        UUID orgId = persistOrganization(OrganizationStatus.SUSPENDED, owner.getId());

        mockMvc.perform(post("/api/v1/organizations/{orgId}/venues", orgId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"name\":\"Main Hall\"}"))
                .andExpect(status().isForbidden());
    }

    // ---- 6. AuthServiceImpl.login: suspended account, checked AFTER password verification ----

    @Test
    void login_suspendedAccount_correctPassword_returns403() throws Exception {
        String rawPassword = "correct-password-123";
        User user = User.builder().name("Suspended User").email("suspended-" + UUID.randomUUID() + "@test.local")
                .passwordHash(passwordEncoder.encode(rawPassword)).role(Role.CUSTOMER).accountStatus(AccountStatus.SUSPENDED).build();
        User saved = userRepository.save(user);
        createdUserIds.add(saved.getId());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"" + saved.getEmail() + "\",\"password\":\"" + rawPassword + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void login_suspendedAccount_wrongPassword_stillReturns401_notLeaking403() throws Exception {
        String rawPassword = "correct-password-123";
        User user = User.builder().name("Suspended User").email("suspended-" + UUID.randomUUID() + "@test.local")
                .passwordHash(passwordEncoder.encode(rawPassword)).role(Role.CUSTOMER).accountStatus(AccountStatus.SUSPENDED).build();
        User saved = userRepository.save(user);
        createdUserIds.add(saved.getId());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"" + saved.getEmail() + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_activeAccount_correctPassword_returns200() throws Exception {
        String rawPassword = "correct-password-123";
        User user = User.builder().name("Active User").email("active-" + UUID.randomUUID() + "@test.local")
                .passwordHash(passwordEncoder.encode(rawPassword)).role(Role.CUSTOMER).accountStatus(AccountStatus.ACTIVE).build();
        User saved = userRepository.save(user);
        createdUserIds.add(saved.getId());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"" + saved.getEmail() + "\",\"password\":\"" + rawPassword + "\"}"))
                .andExpect(status().isOk());
    }
}
