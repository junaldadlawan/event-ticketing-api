package com.junaldadlawan.event_ticketing_api.platformsetting.repository;

import com.junaldadlawan.event_ticketing_api.platformsetting.entity.DraftEventSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DraftEventSettingsRepository extends JpaRepository<DraftEventSettings, Short> {
}
