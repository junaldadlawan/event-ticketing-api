package com.junaldadlawan.event_ticketing_api.region.repository;

import com.junaldadlawan.event_ticketing_api.region.entity.SupportedCountry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SupportedCountryRepository extends JpaRepository<SupportedCountry, String> {

    List<SupportedCountry> findByEnabledTrueOrderByName();

    boolean existsByCurrencyCodeAndEnabledTrue(String currencyCode);

    @Query("select count(o) > 0 from Organization o where o.country = :code and o.deletedAt is null")
    boolean usedByOrganization(@Param("code") String code);

    /** The venue of an event that has not ended (and was not cancelled) is in this country. */
    @Query("select count(e) > 0 from Event e, Venue v where e.venueId = v.id and v.country = :code and e.deletedAt is null and e.endAt > :now"
            + " and e.status not in (com.junaldadlawan.event_ticketing_api.event.enums.EventStatus.CANCELLED,"
            + " com.junaldadlawan.event_ticketing_api.event.enums.EventStatus.COMPLETED)")
    boolean usedByUpcomingEventVenue(@Param("code") String code, @Param("now") Instant now);
}
