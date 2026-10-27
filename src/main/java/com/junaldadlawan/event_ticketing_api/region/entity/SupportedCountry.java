package com.junaldadlawan.event_ticketing_api.region.entity;

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

/** A country the platform supports (ISO 3166-1 alpha-2) and the currency its organizations get when approved. */
@Entity
@Table(name = "supported_countries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportedCountry extends Auditable {

    @Id
    @Column(name = "code", length = 2)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;
}
