package com.junaldadlawan.event_ticketing_api.promocode.repository;

import com.junaldadlawan.event_ticketing_api.promocode.entity.PromoCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromoCodeRepository extends JpaRepository<PromoCode, UUID> {

    Optional<PromoCode> findByEventIdAndCodeAndDeletedAtIsNull(UUID eventId, String code);

    List<PromoCode> findByEventIdAndDeletedAtIsNull(UUID eventId);
}
