package com.junaldadlawan.event_ticketing_api.promocode.service;

import com.junaldadlawan.event_ticketing_api.promocode.dto.PromoCodeCreateRequest;
import com.junaldadlawan.event_ticketing_api.promocode.entity.PromoCode;

import java.util.List;
import java.util.UUID;

public interface PromoCodeService {

    PromoCode create(UUID eventId, PromoCodeCreateRequest request);

    List<PromoCode> list(UUID eventId);
}
