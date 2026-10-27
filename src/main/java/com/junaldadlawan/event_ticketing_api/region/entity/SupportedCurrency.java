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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A currency the platform supports (ISO 4217). USD is the reference: {@code usdRate} is how many units of this currency buy 1 USD.
 * A row is kept when the currency is disabled, so enabling it again keeps its last rate.
 */
@Entity
@Table(name = "supported_currencies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportedCurrency extends Auditable {

    public static final String REFERENCE = "USD";

    @Id
    @Column(name = "code", length = 3)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "symbol", nullable = false, length = 10)
    private String symbol;

    @Column(name = "minor_units", nullable = false)
    private short minorUnits;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /** Null until the feed has given a rate (never made up). */
    @Column(name = "usd_rate", precision = 24, scale = 10)
    private BigDecimal usdRate;

    @Column(name = "rate_updated_at")
    private Instant rateUpdatedAt;

    /** The last fetch failed or had no rate for this currency: {@code usdRate} is the last known one. */
    @Column(name = "rate_stale", nullable = false)
    private boolean rateStale;
}
