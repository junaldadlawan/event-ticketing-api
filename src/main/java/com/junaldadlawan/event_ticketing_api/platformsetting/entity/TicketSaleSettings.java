package com.junaldadlawan.event_ticketing_api.platformsetting.entity;

import com.junaldadlawan.event_ticketing_api.common.entity.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** BR-ADMIN-SETTINGS-002: the ticket-sale timing rules (BR-EVENT-003). Single row, seeded with 60 / 60 by V66. */
@Entity
@Table(name = "ticket_sale_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketSaleSettings extends Auditable {

    public static final short ROW_ID = 1;

    @Id
    @Column(name = "id")
    @Builder.Default
    private Short id = ROW_ID;

    /** Ticket sales must end at least this many minutes before the event starts. */
    @Column(name = "sales_close_minutes_before_event", nullable = false)
    @Builder.Default
    private int salesCloseMinutesBeforeEvent = 60;

    /** A ticket type's sale window must run for at least this many minutes. */
    @Column(name = "min_sale_duration_minutes", nullable = false)
    @Builder.Default
    private int minSaleDurationMinutes = 60;
}
