package com.junaldadlawan.event_ticketing_api.platformsetting.service;

import com.junaldadlawan.event_ticketing_api.platformsetting.dto.TicketSaleSettingsRequest;
import com.junaldadlawan.event_ticketing_api.platformsetting.dto.TicketSaleSettingsResponse;
import com.junaldadlawan.event_ticketing_api.platformsetting.entity.TicketSaleSettings;

/** BR-ADMIN-SETTINGS-002: the ticket-sale timing rules. */
public interface TicketSaleSettingsService {

    /** The rules in force now (the seeded defaults if the row is somehow missing). */
    TicketSaleSettings current();

    /** Any signed-in user, so the organizer's form can show the rules. */
    TicketSaleSettingsResponse get();

    /** Admins only; logged as {@code ticket_sale_settings.updated} with before/after. */
    TicketSaleSettingsResponse update(TicketSaleSettingsRequest request);
}
