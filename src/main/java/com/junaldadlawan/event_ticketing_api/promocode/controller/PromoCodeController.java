package com.junaldadlawan.event_ticketing_api.promocode.controller;

import com.junaldadlawan.event_ticketing_api.promocode.dto.PromoCodeCreateRequest;
import com.junaldadlawan.event_ticketing_api.promocode.dto.PromoCodeResponse;
import com.junaldadlawan.event_ticketing_api.promocode.dto.PromoCodeStatusRequest;
import com.junaldadlawan.event_ticketing_api.promocode.dto.PromoCodeUpdateRequest;
import com.junaldadlawan.event_ticketing_api.promocode.entity.PromoCode;
import com.junaldadlawan.event_ticketing_api.promocode.service.PromoCodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * All promo-code endpoints: create/list are addressed through the event, edit / pause-resume / delete by the
 * promo code's own id.
 */
@RestController
@RequiredArgsConstructor
public class PromoCodeController {

    private final PromoCodeService promoCodeService;

    @PostMapping("/api/v1/events/{eventId}/promo-codes")
    @ResponseStatus(HttpStatus.CREATED)
    public PromoCodeResponse create(@PathVariable UUID eventId, @Valid @RequestBody PromoCodeCreateRequest request) {
        return respond(promoCodeService.create(eventId, request));
    }

    @GetMapping("/api/v1/events/{eventId}/promo-codes")
    public List<PromoCodeResponse> list(@PathVariable UUID eventId) {
        return promoCodeService.list(eventId).stream().map(this::respond).toList();
    }

    @PatchMapping("/api/v1/promo-codes/{promoCodeId}")
    public PromoCodeResponse update(@PathVariable UUID promoCodeId, @Valid @RequestBody PromoCodeUpdateRequest request) {
        return respond(promoCodeService.update(promoCodeId, request));
    }

    /** Pause or resume a promo code: the one place that sets its on/off state. */
    @PutMapping("/api/v1/promo-codes/{promoCodeId}/status")
    public PromoCodeResponse setStatus(@PathVariable UUID promoCodeId, @Valid @RequestBody PromoCodeStatusRequest request) {
        return respond(promoCodeService.setStatus(promoCodeId, request.status()));
    }

    @DeleteMapping("/api/v1/promo-codes/{promoCodeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID promoCodeId) {
        promoCodeService.delete(promoCodeId);
    }

    private PromoCodeResponse respond(PromoCode promoCode) {
        return PromoCodeResponse.from(promoCode, promoCodeService.usedCount(promoCode));
    }
}
