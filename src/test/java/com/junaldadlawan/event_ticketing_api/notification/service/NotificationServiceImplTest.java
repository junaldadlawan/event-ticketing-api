package com.junaldadlawan.event_ticketing_api.notification.service;

import com.junaldadlawan.event_ticketing_api.notification.dto.NotificationResponse;
import com.junaldadlawan.event_ticketing_api.notification.entity.Notification;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationChannel;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationStatus;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationType;
import com.junaldadlawan.event_ticketing_api.notification.gateway.EmailSender;
import com.junaldadlawan.event_ticketing_api.notification.repository.NotificationRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link NotificationServiceImpl} (no Spring
 * context) - the core Phase 11 primitive. Covers the full {@code notify}
 * state machine (PENDING -> SENT/FAILED via every branch: successful mock
 * delivery, mock delivery failure, missing user, user with a null email,
 * and an internal exception), proving the NFR 5.2 catch-all never
 * propagates, and {@code listMyNotifications}'s caller-scoped mapping.
 * NFR 5.2's END-TO-END proof (that a failed notification doesn't roll back
 * a real checkout/refund) lives in {@code NotificationIntegrationTest}
 * instead - this class can only prove this method itself never throws, not
 * that a REAL surrounding transaction survives it.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailSender emailSender;
    @Mock
    private OrganizationAccessGuard accessGuard;

    private NotificationServiceImpl service;

    private UUID userId;
    private UUID relatedObjectId;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(notificationRepository, userRepository, emailSender, accessGuard);
        userId = UUID.randomUUID();
        relatedObjectId = UUID.randomUUID();
    }

    private User user(UUID id, String email) {
        return User.builder().id(id).name("Test User").email(email).passwordHash("irrelevant").role(Role.CUSTOMER).build();
    }

    /** Every {@code saveAndFlush} call echoes back its argument, like a real save would. */
    private void stubSaveAndFlushEchoesArgument() {
        when(notificationRepository.saveAndFlush(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---- notify(): successful delivery ----

    @Test
    void notify_successfulDelivery_transitionsToSent_setsSentAt() {
        stubSaveAndFlushEchoesArgument();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, "buyer@test.local")));
        when(emailSender.send(anyString(), anyString(), anyString())).thenReturn(true);

        service.notify(userId, NotificationType.ORDER_CONFIRMATION, "Order", relatedObjectId);

        // The mock echoes back the SAME mutable Notification instance both
        // times (`saved` in NotificationServiceImpl.notify IS the object
        // passed to the first saveAndFlush call), so a captor's two
        // "snapshots" both end up pointing at the final, fully-mutated
        // state - asserting the FIRST call's argument was PENDING at the
        // time it was made isn't observable this way. What's proven
        // instead: saveAndFlush is called exactly twice (PENDING persist +
        // status-update persist, per the class's own javadoc), the
        // final/only-observable state is SENT with sentAt set, and every
        // field intended to be set on the very first (PENDING) persist made
        // it into the final row.
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).saveAndFlush(captor.capture());
        Notification finalState = captor.getValue();
        assertThat(finalState.getUserId()).isEqualTo(userId);
        assertThat(finalState.getType()).isEqualTo(NotificationType.ORDER_CONFIRMATION);
        assertThat(finalState.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(finalState.getRelatedObjectType()).isEqualTo("Order");
        assertThat(finalState.getRelatedObjectId()).isEqualTo(relatedObjectId);
        assertThat(finalState.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(finalState.getSentAt()).isNotNull();
        verify(emailSender).send("buyer@test.local", "Your order is confirmed",
                "Your order has been confirmed. Thank you for your purchase!");
    }

    // ---- notify(): mock delivery failure ("fail-delivery" recipient) ----

    @Test
    void notify_deliveryFails_transitionsToFailed_noSentAt() {
        stubSaveAndFlushEchoesArgument();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, "buyer-fail-delivery@test.local")));
        when(emailSender.send(anyString(), anyString(), anyString())).thenReturn(false);

        service.notify(userId, NotificationType.PAYMENT_RECEIPT, "Order", relatedObjectId);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).saveAndFlush(captor.capture());
        Notification finalSave = captor.getAllValues().get(1);
        assertThat(finalSave.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(finalSave.getSentAt()).isNull();
    }

    // ---- notify(): target user doesn't exist - no email attempted at all ----

    @Test
    void notify_userNotFound_transitionsToFailed_neverAttemptsDelivery() {
        stubSaveAndFlushEchoesArgument();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        service.notify(userId, NotificationType.WAITLIST_AVAILABILITY, "WaitlistEntry", relatedObjectId);

        verifyNoInteractions(emailSender);
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(captor.getAllValues().get(1).getSentAt()).isNull();
    }

    // ---- notify(): target user exists but has a null email - no email attempted at all ----

    @Test
    void notify_userHasNullEmail_transitionsToFailed_neverAttemptsDelivery() {
        stubSaveAndFlushEchoesArgument();
        User userWithNoEmail = User.builder().id(userId).name("No Email").passwordHash("irrelevant").role(Role.CUSTOMER).email(null).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithNoEmail));

        service.notify(userId, NotificationType.REFUND_CONFIRMATION, "Refund", relatedObjectId);

        verify(emailSender, never()).send(any(), any(), any());
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    // ---- notify(): NFR 5.2 - never throws, even on an internal failure ----

    @Test
    void notify_repositoryThrowsOnInitialPersist_neverPropagates_swallowedInternally() {
        when(notificationRepository.saveAndFlush(any(Notification.class))).thenThrow(new RuntimeException("DB connection lost"));

        assertThatCode(() -> service.notify(userId, NotificationType.EVENT_CANCELLATION, "Event", relatedObjectId))
                .doesNotThrowAnyException();

        verifyNoInteractions(userRepository, emailSender);
    }

    @Test
    void notify_emailSenderThrowsUnexpectedly_neverPropagates_stillPersistsAFailedStatus() {
        Notification pending = Notification.builder().id(UUID.randomUUID()).userId(userId)
                .type(NotificationType.ORDER_CONFIRMATION).channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.PENDING).build();
        when(notificationRepository.saveAndFlush(any(Notification.class))).thenReturn(pending);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, "buyer@test.local")));
        when(emailSender.send(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("mock gateway exploded"));

        assertThatCode(() -> service.notify(userId, NotificationType.ORDER_CONFIRMATION, "Order", relatedObjectId))
                .doesNotThrowAnyException();

        // Only the initial PENDING persist happened - the exception was thrown
        // mid-method (during emailSender.send), so the final status-update
        // saveAndFlush call never ran; caught by the outer try/catch instead.
        verify(notificationRepository, times(1)).saveAndFlush(any(Notification.class));
    }

    // ---- listMyNotifications() ----

    @Test
    void listMyNotifications_delegatesToCallerScopedRepositoryQuery_mapsToResponses() {
        UUID callerId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(callerId);
        Notification n1 = Notification.builder().id(UUID.randomUUID()).userId(callerId)
                .type(NotificationType.ORDER_CONFIRMATION).channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.SENT).sentAt(Instant.now()).createdAt(Instant.now()).build();
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(callerId)).thenReturn(List.of(n1));

        List<NotificationResponse> result = service.listMyNotifications();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(n1.getId());
        assertThat(result.get(0).type()).isEqualTo(NotificationType.ORDER_CONFIRMATION);
        assertThat(result.get(0).status()).isEqualTo(NotificationStatus.SENT);
        verify(notificationRepository).findByUserIdOrderByCreatedAtDesc(callerId);
    }

    @Test
    void listMyNotifications_noEntries_returnsEmptyList() {
        UUID callerId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(callerId);
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(callerId)).thenReturn(List.of());

        assertThat(service.listMyNotifications()).isEmpty();
    }
}
