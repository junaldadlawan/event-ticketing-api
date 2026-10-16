package com.junaldadlawan.event_ticketing_api.gatewayfee.repository;

import com.junaldadlawan.event_ticketing_api.gatewayfee.entity.GatewayFeeSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewayFeeSettingRepository extends JpaRepository<GatewayFeeSetting, Short> {
}
