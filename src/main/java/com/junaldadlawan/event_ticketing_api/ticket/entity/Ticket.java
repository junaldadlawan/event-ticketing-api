package com.junaldadlawan.event_ticketing_api.ticket.entity;

import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * "Transactional/status-bearing" tier per the ERD's audit-column policy —
 * {@code created_at}/{@code updated_at} only, no {@code created_by}/{@code
 * updated_by}/{@code deleted_at} (same tier as {@code Order}/{@code Payment}/
 * {@code Cart}/{@code Seat}). One row per admission unit (Phase 6a confirmed
 * decision #2): a general-admission {@code CartItem} with {@code quantity=N}
 * issues N Ticket rows at checkout; a reserved-seating {@code CartItem}
 * (quantity always 1) issues exactly 1, tied to its specific seat.
 * <p>
 * {@code eventId} is denormalized directly onto {@code Ticket} (matches the
 * ERD's own {@code Ticket} schema) purely for query convenience — {@code
 * Order} itself has no {@code eventId} column (confirmed decision #3); "the
 * order's event" is resolved by looking up any one of its tickets instead.
 * <p>
 * {@code id} is assigned by the issuing service (not {@code @GeneratedValue})
 * because the id must be known BEFORE the credential is computed — the
 * credential embeds the ticket id: {@code ticketId + "." +
 * base64url(hmac(ticketId))} (see {@code TicketCredentialService}).
 * Implements {@link Persistable}, same idiom/reasoning as {@code
 * CheckoutIdempotencyKey}: without it, Spring Data's default
 * non-null-id-means-existing heuristic would route every fresh {@code save()}
 * through {@code merge()} (an extra SELECT) instead of {@code persist()}.
 * <p>
 * {@code credential} is intentionally NEVER exposed on any response DTO
 * (openapi.yaml's {@code Ticket} schema has no such field, deliberately) —
 * see {@code TicketResponse}.
 */
@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket implements Persistable<UUID> {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "ticket_type_id", nullable = false)
    private UUID ticketTypeId;

    @Column(name = "seat_id")
    private UUID seatId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "ticket_number", nullable = false, length = 20)
    private String ticketNumber;

    @Column(name = "credential", nullable = false, length = 500)
    private String credential;

    /**
     * Folded into {@code credential}'s signed payload (Phase 7 - see
     * {@code TicketCredentialService}) so a transfer/resale can produce a
     * genuinely different credential for the SAME ticket row (BR-TRANSFER-005)
     * by bumping this and regenerating, without needing a new ticket id.
     * Starts at 0 for every ticket issued at checkout.
     */
    @Builder.Default
    @Column(name = "credential_version", nullable = false)
    private int credentialVersion = 0;

    /**
     * The ticket type's price AT THE MOMENT this ticket was issued (Phase 7,
     * code-reviewer MEDIUM) - resale's price cap (BR-TRANSFER-004) must be
     * anchored to what was actually paid, not the ticket type's current
     * price, or an organizer raising tiered/early-bird pricing later would
     * silently let early buyers resell above what BR-TRANSFER-004's cap is
     * meant to enforce. Nullable: tickets issued before this column existed
     * fall back to the ticket type's current price at resale time (see
     * {@code ResaleListingServiceImpl.requirePriceWithinCap}).
     */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "face_value_amount")),
            @AttributeOverride(name = "currency", column = @Column(name = "face_value_currency", length = 3))
    })
    private Money faceValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TicketStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Transient
    @Builder.Default
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
}
