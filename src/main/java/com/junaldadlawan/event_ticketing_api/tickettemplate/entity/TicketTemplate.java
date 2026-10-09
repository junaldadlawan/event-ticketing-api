package com.junaldadlawan.event_ticketing_api.tickettemplate.entity;

import com.junaldadlawan.event_ticketing_api.common.entity.Auditable;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.BackgroundFit;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.CodeType;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TicketTemplateFormat;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
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

    /**
     * Where the scannable code sits on the ticket, chosen by the organizer. {@code codeX}/
     * {@code codeY} are the unrotated box's top-left corner as % of the ticket's width/height,
     * {@code codeWidth} its width as % of the ticket's width (the height follows from the
     * type's shape), {@code codeRotation} degrees clockwise about the centre (any type).
     * All null means the renderer's default placement; type, x, y and width are set together.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "code_type", length = 10)
    private CodeType codeType;

    @Column(name = "code_x")
    private Double codeX;

    @Column(name = "code_y")
    private Double codeY;

    @Column(name = "code_width")
    private Double codeWidth;

    @Column(name = "code_rotation")
    private Integer codeRotation;

    /** The ticket's own size in pixels (100-5000); null = the default 900 x 380. */
    @Column(name = "ticket_width")
    private Integer ticketWidth;

    @Column(name = "ticket_height")
    private Integer ticketHeight;

    /** "#RRGGBB" fill under everything; null = white. */
    @Column(name = "background_color", length = 7)
    private String backgroundColor;

    /** Null = COVER. The rectangle below is only for CUSTOM. */
    @Enumerated(EnumType.STRING)
    @Column(name = "background_fit", length = 10)
    private BackgroundFit backgroundFit;

    /** CUSTOM fit only: the image's rectangle, in % of the ticket's width/height; may extend past the edges. */
    @Column(name = "background_x")
    private Double backgroundX;

    @Column(name = "background_y")
    private Double backgroundY;

    @Column(name = "background_width")
    private Double backgroundWidth;

    @Column(name = "background_height")
    private Double backgroundHeight;

    /**
     * The ticket's text fields in drawing order (later on top). Mirrors {@code Organization.documents}:
     * an element collection with an order column and no FK constraint; eager because it is tiny and
     * always needed together with the template.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "ticket_template_text_fields",
            joinColumns = @JoinColumn(name = "template_id"),
            foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    @OrderColumn(name = "sort_order")
    @Builder.Default
    private List<TicketTextField> textFields = new ArrayList<>();
}
