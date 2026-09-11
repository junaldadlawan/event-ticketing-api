package com.junaldadlawan.event_ticketing_api.tickettemplate.entity;

import com.junaldadlawan.event_ticketing_api.common.entity.Auditable;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TicketTemplateFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * "Managed resource" tier per the ERD's audit-column policy — full audit
 * set, same as {@code Event}/{@code TicketType}/{@code PromoCode}.
 * <p>
 * {@code ticketTypeId} is nullable: null means the template applies to the
 * whole event (openapi.yaml's {@code TicketTemplate}/{@code
 * TicketTemplateCreate} schemas type it as {@code [string, "null"]}); a
 * non-null value scopes it to one specific ticket type. Branding fields
 * ({@code logoUrl}/{@code backgroundImageUrl}/{@code primaryColor}) are
 * plain nullable columns directly on the entity rather than a child table —
 * unlike {@code Organization.documents}/{@code
 * PromoCode.applicableTicketTypeIds}, these are simple 1:1 scalar fields,
 * not a collection, so no {@code @ElementCollection} table is warranted.
 */
@Entity
@Table(name = "ticket_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketTemplate extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "ticket_type_id")
    private UUID ticketTypeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, length = 20)
    private TicketTemplateFormat format;

    @Column(name = "logo_url", length = 2_048)
    private String logoUrl;

    @Column(name = "background_image_url", length = 2_048)
    private String backgroundImageUrl;

    @Column(name = "primary_color", length = 20)
    private String primaryColor;
}
