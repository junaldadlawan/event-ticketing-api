package com.junaldadlawan.event_ticketing_api.platformsetting.repository;

import com.junaldadlawan.event_ticketing_api.platformsetting.entity.TicketSaleSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketSaleSettingsRepository extends JpaRepository<TicketSaleSettings, Short> {
}
