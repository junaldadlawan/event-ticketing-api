package com.junaldadlawan.event_ticketing_api.organization.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single verification document (e.g. business permit) submitted with an
 * {@link Organization} application. Stored as an ordered
 * {@code @ElementCollection} on {@code Organization.documents}, backed by the
 * {@code organization_documents} child table (ordered via {@code sort_order}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Embeddable
public class OrganizationDocument {

    @Column(name = "type", nullable = false, length = 100)
    private String type;

    @Column(name = "url", nullable = false, length = 500)
    private String url;
}
