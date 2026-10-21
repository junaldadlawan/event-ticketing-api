package com.junaldadlawan.event_ticketing_api.platformsetting.controller;

import com.junaldadlawan.event_ticketing_api.platformsetting.dto.TicketSaleSettingsRequest;
import com.junaldadlawan.event_ticketing_api.platformsetting.dto.TicketSaleSettingsResponse;
import com.junaldadlawan.event_ticketing_api.platformsetting.service.TicketSaleSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** BR-ADMIN-SETTINGS-002. Both paths fall under SecurityConfig's anyRequest().authenticated(); the service checks admin for PUT. */
@RestController
@RequestMapping("/api/v1/platform-settings/ticket-sales")
@RequiredArgsConstructor
public class TicketSaleSettingsController {

    private final TicketSaleSettingsService ticketSaleSettingsService;

    @GetMapping
    public TicketSaleSettingsResponse get() {
        return ticketSaleSettingsService.get();
    }

    @PutMapping
    public TicketSaleSettingsResponse update(@Valid @RequestBody TicketSaleSettingsRequest request) {
        return ticketSaleSettingsService.update(request);
    }
}
