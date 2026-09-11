package com.junaldadlawan.event_ticketing_api.refundpolicy.entity;

import com.junaldadlawan.event_ticketing_api.common.entity.Auditable;
import com.junaldadlawan.event_ticketing_api.refundpolicy.enums.RefundRuleType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Entity;
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
 * Managed-resource tier per the ERD's audit-column policy: full audit set,
 * same as {@code ResalePolicy}/{@code TicketTemplate}. One per event
 * ("Event ||--o| RefundPolicy: has"), enforced by a unique index on {@code
 * event_id} (V16) rather than the ERD's literal "event_id as primary key"
 * depiction - same deviation already established for {@code ResalePolicy}.
 */
@Entity
@Table(name = "refund_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundPolicy extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 30)
    private RefundRuleType ruleType;

    /** Only meaningful when {@code ruleType == REFUNDABLE_UNTIL_N_DAYS}. */
    @Column(name = "days_before_event")
    private Integer daysBeforeEvent;

    /** Only meaningful when {@code ruleType == CUSTOM} - informational text, not machine-enforced. */
    @Column(name = "custom_terms", length = 2000)
    private String customTerms;
}
