package com.junaldadlawan.event_ticketing_api.promocode.controller;

import com.junaldadlawan.event_ticketing_api.promocode.dto.PromoCodeCreateRequest;
import com.junaldadlawan.event_ticketing_api.promocode.dto.PromoCodeResponse;
import com.junaldadlawan.event_ticketing_api.promocode.service.PromoCodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events/{eventId}/promo-codes")
@RequiredArgsConstructor
public class EventPromoCodeController {

    private final PromoCodeService promoCodeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PromoCodeResponse create(@PathVariable UUID eventId, @Valid @RequestBody PromoCodeCreateRequest request) {
        return PromoCodeResponse.from(promoCodeService.create(eventId, request));
    }

    @GetMapping
    public List<PromoCodeResponse> list(@PathVariable UUID eventId) {
        return promoCodeService.list(eventId).stream().map(PromoCodeResponse::from).toList();
    }
}
