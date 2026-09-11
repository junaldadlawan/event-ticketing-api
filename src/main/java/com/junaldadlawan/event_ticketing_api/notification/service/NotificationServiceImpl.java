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
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * BR-NOTIFY-001. Only the {@code EMAIL} channel is actually wired
 * (requirements.md §4.12: "email at minimum"; SMS/push are modeled in
 * {@link NotificationChannel} for a future channel, not delivered here).
 * <p>
 * NFR 5.2 ("a notification failure must not roll back a successful
 * payment") is why {@link #notify} wraps its entire body in a catch-all and
 * never rethrows, AND why it's {@code @Transactional(propagation =
 * REQUIRES_NEW)} - code-reviewer CRITICAL: a plain {@code saveAndFlush}
 * with no propagation boundary of its own only JOINS whatever ambient
 * transaction is already open (e.g. mid-checkout). If that flush throws,
 * Spring marks the AMBIENT transaction rollback-only the instant the
 * exception is thrown - catching it here afterward is too late, since the
 * caller's own transaction can no longer commit even though it returns
 * normally (surfacing as an {@code UnexpectedRollbackException}, or a
 * silently rolled-back order/payment/tickets despite an already-charged
 * card). {@code REQUIRES_NEW} gives this method's writes their own
 * physical transaction, so a failure here can be independently rolled back
 * without poisoning whatever transaction triggered this notification -
 * same idiom as {@code WaitlistPositionAssigner#assign}. Each write still
 * uses {@code saveAndFlush} (not plain {@code save}) so any constraint
 * violation surfaces synchronously inside this method's own try/catch
 * rather than deferred past its return.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EmailSender emailSender;
    private final OrganizationAccessGuard accessGuard;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notify(UUID userId, NotificationType type, String relatedObjectType, UUID relatedObjectId) {
        try {
            Notification notification = Notification.builder()
                    .userId(userId)
                    .type(type)
                    .channel(NotificationChannel.EMAIL)
                    .relatedObjectType(relatedObjectType)
                    .relatedObjectId(relatedObjectId)
                    .status(NotificationStatus.PENDING)
                    .build();
            Notification saved = notificationRepository.saveAndFlush(notification);

            User user = userRepository.findById(userId).orElse(null);
            if (user == null || user.getEmail() == null) {
                saved.setStatus(NotificationStatus.FAILED);
                notificationRepository.saveAndFlush(saved);
                return;
            }

            boolean delivered = emailSender.send(user.getEmail(), subjectFor(type), bodyFor(type));
            saved.setStatus(delivered ? NotificationStatus.SENT : NotificationStatus.FAILED);
            if (delivered) {
                saved.setSentAt(Instant.now());
            }
            notificationRepository.saveAndFlush(saved);
        } catch (Exception e) {
            log.error("Failed to dispatch notification (userId={}, type={}, relatedObjectType={}, relatedObjectId={})",
                    userId, type, relatedObjectType, relatedObjectId, e);
        }
    }

    @Override
    public List<NotificationResponse> listMyNotifications() {
        UUID callerId = accessGuard.currentUserId();
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(callerId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    private String subjectFor(NotificationType type) {
        return switch (type) {
            case ORDER_CONFIRMATION -> "Your order is confirmed";
            case PAYMENT_RECEIPT -> "Payment received";
            case EVENT_REMINDER -> "Your event is coming up soon";
            case EVENT_CHANGE -> "Event details have changed";
            case EVENT_CANCELLATION -> "Event cancelled";
            case WAITLIST_AVAILABILITY -> "A spot opened up on your waitlist";
            case REFUND_CONFIRMATION -> "Your refund has been issued";
        };
    }

    private String bodyFor(NotificationType type) {
        return switch (type) {
            case ORDER_CONFIRMATION -> "Your order has been confirmed. Thank you for your purchase!";
            case PAYMENT_RECEIPT -> "We've received your payment. This email is your receipt.";
            case EVENT_REMINDER -> "This is a reminder that your event is coming up soon.";
            case EVENT_CHANGE -> "The event's details have changed. Please review the updated information.";
            case EVENT_CANCELLATION -> "Unfortunately, this event has been cancelled. A refund has been issued for your order.";
            case WAITLIST_AVAILABILITY -> "A ticket has become available for the waitlist you joined. "
                    + "You have a limited time to complete your purchase before the offer passes to the next person in line.";
            case REFUND_CONFIRMATION -> "Your refund has been issued and should reflect in your original payment method soon.";
        };
    }
}
