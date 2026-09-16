package com.junaldadlawan.event_ticketing_api.moderation.service;

import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.event.service.EventService;
import com.junaldadlawan.event_ticketing_api.moderation.dto.ModerationActionCreateRequest;
import com.junaldadlawan.event_ticketing_api.moderation.dto.ModerationActionResponse;
import com.junaldadlawan.event_ticketing_api.moderation.entity.ModerationAction;
import com.junaldadlawan.event_ticketing_api.moderation.enums.ModerationActionType;
import com.junaldadlawan.event_ticketing_api.moderation.enums.ModerationTargetType;
import com.junaldadlawan.event_ticketing_api.moderation.repository.ModerationActionRepository;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationType;
import com.junaldadlawan.event_ticketing_api.notification.service.NotificationService;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.AccountStatus;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import com.junaldadlawan.event_ticketing_api.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link ModerationActionServiceImpl} (no Spring
 * context) - covers BR-ADMIN-002: admin-only gate, the SUSPEND/REINSTATE/
 * REMOVE matrix for all three target types, the previousStatus capture and
 * REINSTATE lookback (including the no-prior-SUSPEND-row default), the
 * already-suspended/not-suspended/already-removed conflict guards, and the
 * REMOVE-reuses-the-target-module's-own-soft-delete behavior.
 */
@ExtendWith(MockitoExtension.class)
class ModerationActionServiceImplTest {

    @Mock
    private ModerationActionRepository moderationActionRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private EventService eventService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;
    @Mock
    private OrganizationAccessGuard accessGuard;
    @Mock
    private NotificationService notificationService;

    private ModerationActionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ModerationActionServiceImpl(moderationActionRepository, organizationRepository, eventRepository,
                eventService, userRepository, userService, accessGuard, notificationService);
        lenient().when(moderationActionRepository.save(any(ModerationAction.class))).thenAnswer(inv -> {
            ModerationAction action = inv.getArgument(0);
            action.setId(UUID.randomUUID());
            return action;
        });
    }

    private Organization organization(UUID id, OrganizationStatus status, UUID ownerId) {
        return Organization.builder().id(id).name("Org").status(status).ownerId(ownerId).build();
    }

    private Event event(UUID id, EventStatus status) {
        return Event.builder().id(id).organizationId(UUID.randomUUID()).title("t").description("d").category("c")
                .startAt(java.time.Instant.now()).endAt(java.time.Instant.now().plusSeconds(3600)).timezone("UTC")
                .ticketPrefix("ABC").status(status).build();
    }

    private User user(UUID id, AccountStatus status) {
        return User.builder().id(id).name("n").email("e@test.local").passwordHash("h").role(Role.CUSTOMER).accountStatus(status).build();
    }

    // ---- admin gate ----

    @Test
    void create_nonAdmin_throwsForbidden() {
        doThrow(new ForbiddenException("Admin access required")).when(accessGuard).requireAdmin();

        assertThatThrownBy(() -> service.create(new ModerationActionCreateRequest(ModerationTargetType.USER, UUID.randomUUID(), ModerationActionType.SUSPEND, "reason")))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(organizationRepository, eventRepository, userRepository);
    }

    // ---- ORGANIZATION ----

    @Test
    void create_organizationSuspend_capturesPreviousStatus_notifiesOwner() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(adminId);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization(orgId, OrganizationStatus.APPROVED, ownerId)));

        ModerationActionResponse result = service.create(new ModerationActionCreateRequest(
                ModerationTargetType.ORGANIZATION, orgId, ModerationActionType.SUSPEND, "fraud reports"));

        assertThat(result.previousStatus()).isEqualTo("APPROVED");
        assertThat(result.performedBy()).isEqualTo(adminId);
        verify(organizationRepository).save(argThat(org -> org.getStatus() == OrganizationStatus.SUSPENDED));
        verify(notificationService).notify(eq(ownerId), eq(NotificationType.ACCOUNT_MODERATION_ACTION), any(), any());
    }

    @Test
    void create_organizationSuspend_alreadySuspended_throwsConflict() {
        UUID orgId = UUID.randomUUID();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization(orgId, OrganizationStatus.SUSPENDED, UUID.randomUUID())));

        assertThatThrownBy(() -> service.create(new ModerationActionCreateRequest(
                ModerationTargetType.ORGANIZATION, orgId, ModerationActionType.SUSPEND, "reason")))
                .isInstanceOf(ConflictException.class);
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void create_organizationReinstate_restoresFromLatestSuspendAction() {
        UUID orgId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(adminId);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization(orgId, OrganizationStatus.SUSPENDED, null)));
        ModerationAction priorSuspend = ModerationAction.builder().previousStatus("PENDING").build();
        when(moderationActionRepository.findFirstByTargetIdAndActionOrderByCreatedAtDesc(orgId, ModerationActionType.SUSPEND))
                .thenReturn(Optional.of(priorSuspend));

        service.create(new ModerationActionCreateRequest(ModerationTargetType.ORGANIZATION, orgId, ModerationActionType.REINSTATE, "resolved"));

        verify(organizationRepository).save(argThat(org -> org.getStatus() == OrganizationStatus.PENDING));
    }

    @Test
    void create_organizationReinstate_noPriorSuspendRow_defaultsApproved() {
        UUID orgId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(UUID.randomUUID());
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization(orgId, OrganizationStatus.SUSPENDED, null)));
        when(moderationActionRepository.findFirstByTargetIdAndActionOrderByCreatedAtDesc(orgId, ModerationActionType.SUSPEND))
                .thenReturn(Optional.empty());

        service.create(new ModerationActionCreateRequest(ModerationTargetType.ORGANIZATION, orgId, ModerationActionType.REINSTATE, "resolved"));

        verify(organizationRepository).save(argThat(org -> org.getStatus() == OrganizationStatus.APPROVED));
    }

    @Test
    void create_organizationReinstate_notCurrentlySuspended_throwsConflict() {
        UUID orgId = UUID.randomUUID();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization(orgId, OrganizationStatus.APPROVED, null)));

        assertThatThrownBy(() -> service.create(new ModerationActionCreateRequest(
                ModerationTargetType.ORGANIZATION, orgId, ModerationActionType.REINSTATE, "reason")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_organizationRemove_marksDeleted() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(UUID.randomUUID());
        Organization org = organization(orgId, OrganizationStatus.APPROVED, ownerId);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));

        service.create(new ModerationActionCreateRequest(ModerationTargetType.ORGANIZATION, orgId, ModerationActionType.REMOVE, "TOS violation"));

        assertThat(org.getDeletedAt()).isNotNull();
        verify(organizationRepository).save(org);
    }

    @Test
    void create_organizationRemove_alreadyRemoved_throwsConflict() {
        UUID orgId = UUID.randomUUID();
        Organization org = organization(orgId, OrganizationStatus.APPROVED, null);
        org.markDeleted();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));

        assertThatThrownBy(() -> service.create(new ModerationActionCreateRequest(
                ModerationTargetType.ORGANIZATION, orgId, ModerationActionType.REMOVE, "reason")))
                .isInstanceOf(ConflictException.class);
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void create_organizationUnknown_throwsResourceNotFound() {
        UUID orgId = UUID.randomUUID();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new ModerationActionCreateRequest(
                ModerationTargetType.ORGANIZATION, orgId, ModerationActionType.SUSPEND, "reason")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- EVENT ----

    @Test
    void create_eventSuspend_capturesPreviousStatus_noNotification() {
        UUID eventId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(UUID.randomUUID());
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event(eventId, EventStatus.PUBLISHED)));

        ModerationActionResponse result = service.create(new ModerationActionCreateRequest(
                ModerationTargetType.EVENT, eventId, ModerationActionType.SUSPEND, "reason"));

        assertThat(result.previousStatus()).isEqualTo("PUBLISHED");
        verify(eventRepository).save(argThat(e -> e.getStatus() == EventStatus.SUSPENDED));
        // No single obvious recipient for an Event - deliberately skipped.
        verifyNoInteractions(notificationService);
    }

    @Test
    void create_eventReinstate_noPriorSuspendRow_defaultsDraft() {
        UUID eventId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(UUID.randomUUID());
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event(eventId, EventStatus.SUSPENDED)));
        when(moderationActionRepository.findFirstByTargetIdAndActionOrderByCreatedAtDesc(eventId, ModerationActionType.SUSPEND))
                .thenReturn(Optional.empty());

        service.create(new ModerationActionCreateRequest(ModerationTargetType.EVENT, eventId, ModerationActionType.REINSTATE, "resolved"));

        verify(eventRepository).save(argThat(e -> e.getStatus() == EventStatus.DRAFT));
    }

    @Test
    void create_eventRemove_delegatesToEventServiceDelete() {
        UUID eventId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(UUID.randomUUID());
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event(eventId, EventStatus.PUBLISHED)));

        service.create(new ModerationActionCreateRequest(ModerationTargetType.EVENT, eventId, ModerationActionType.REMOVE, "reason"));

        verify(eventService).delete(eventId);
    }

    @Test
    void create_eventRemove_alreadyRemoved_throwsConflict() {
        UUID eventId = UUID.randomUUID();
        Event ev = event(eventId, EventStatus.PUBLISHED);
        ev.markDeleted();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(ev));

        assertThatThrownBy(() -> service.create(new ModerationActionCreateRequest(
                ModerationTargetType.EVENT, eventId, ModerationActionType.REMOVE, "reason")))
                .isInstanceOf(ConflictException.class);
        verify(eventService, never()).delete(any());
    }

    // ---- USER ----

    @Test
    void create_userSuspend_previousStatusAlwaysActive_notifiesUser() {
        UUID userId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(UUID.randomUUID());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, AccountStatus.ACTIVE)));

        ModerationActionResponse result = service.create(new ModerationActionCreateRequest(
                ModerationTargetType.USER, userId, ModerationActionType.SUSPEND, "abuse reports"));

        assertThat(result.previousStatus()).isEqualTo("ACTIVE");
        verify(userRepository).save(argThat(u -> u.getAccountStatus() == AccountStatus.SUSPENDED));
        verify(notificationService).notify(eq(userId), eq(NotificationType.ACCOUNT_MODERATION_ACTION), any(), any());
    }

    @Test
    void create_userSuspend_alreadySuspended_throwsConflict() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, AccountStatus.SUSPENDED)));

        assertThatThrownBy(() -> service.create(new ModerationActionCreateRequest(
                ModerationTargetType.USER, userId, ModerationActionType.SUSPEND, "reason")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_userReinstate_setsActive() {
        UUID userId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(UUID.randomUUID());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, AccountStatus.SUSPENDED)));

        service.create(new ModerationActionCreateRequest(ModerationTargetType.USER, userId, ModerationActionType.REINSTATE, "resolved"));

        verify(userRepository).save(argThat(u -> u.getAccountStatus() == AccountStatus.ACTIVE));
    }

    @Test
    void create_userReinstate_notSuspended_throwsConflict() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, AccountStatus.ACTIVE)));

        assertThatThrownBy(() -> service.create(new ModerationActionCreateRequest(
                ModerationTargetType.USER, userId, ModerationActionType.REINSTATE, "reason")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_userRemove_delegatesToUserServiceDelete() {
        UUID userId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(UUID.randomUUID());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, AccountStatus.ACTIVE)));

        service.create(new ModerationActionCreateRequest(ModerationTargetType.USER, userId, ModerationActionType.REMOVE, "reason"));

        verify(userService).delete(userId);
    }

    @Test
    void create_userRemove_alreadyRemoved_throwsConflict() {
        UUID userId = UUID.randomUUID();
        User u = user(userId, AccountStatus.ACTIVE);
        u.markDeleted();
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.create(new ModerationActionCreateRequest(
                ModerationTargetType.USER, userId, ModerationActionType.REMOVE, "reason")))
                .isInstanceOf(ConflictException.class);
        verify(userService, never()).delete(any());
    }

    @Test
    void create_userUnknown_throwsResourceNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new ModerationActionCreateRequest(
                ModerationTargetType.USER, userId, ModerationActionType.SUSPEND, "reason")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- list() ----

    @Test
    void list_nonAdmin_throwsForbidden() {
        doThrow(new ForbiddenException("Admin access required")).when(accessGuard).requireAdmin();
        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> service.list(null, null, pageable)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(moderationActionRepository);
    }

    @Test
    void list_bothFiltersPresent_delegatesToFindByTargetTypeAndTargetId() {
        Pageable pageable = PageRequest.of(0, 20);
        UUID targetId = UUID.randomUUID();
        when(moderationActionRepository.findByTargetTypeAndTargetId(ModerationTargetType.USER, targetId, pageable))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.list(ModerationTargetType.USER, targetId, pageable);

        verify(moderationActionRepository).findByTargetTypeAndTargetId(ModerationTargetType.USER, targetId, pageable);
        verify(moderationActionRepository, never()).findAll(pageable);
    }

    @Test
    void list_noFilters_delegatesToFindAll() {
        Pageable pageable = PageRequest.of(0, 20);
        when(moderationActionRepository.findAll(pageable)).thenReturn(org.springframework.data.domain.Page.empty());

        service.list(null, null, pageable);

        verify(moderationActionRepository).findAll(pageable);
    }

    private static <T> T argThat(java.util.function.Predicate<T> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}
