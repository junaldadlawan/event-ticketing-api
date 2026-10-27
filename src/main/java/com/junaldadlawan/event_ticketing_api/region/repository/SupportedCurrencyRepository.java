package com.junaldadlawan.event_ticketing_api.region.repository;

import com.junaldadlawan.event_ticketing_api.region.entity.SupportedCurrency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SupportedCurrencyRepository extends JpaRepository<SupportedCurrency, String> {

    List<SupportedCurrency> findByEnabledTrueOrderByCode();

    @Query("select count(o) > 0 from Organization o where o.currency = :code and o.deletedAt is null")
    boolean usedByOrganization(@Param("code") String code);

    /** A ticket type priced in this currency on an event that has not ended (and was not cancelled). */
    @Query("select count(t) > 0 from TicketType t, Event e where t.eventId = e.id and t.price.currency = :code and t.deletedAt is null"
            + " and e.deletedAt is null and e.endAt > :now and e.status not in ("
            + "com.junaldadlawan.event_ticketing_api.event.enums.EventStatus.CANCELLED, com.junaldadlawan.event_ticketing_api.event.enums.EventStatus.COMPLETED)")
    boolean usedByOpenEvent(@Param("code") String code, @Param("now") Instant now);
}
