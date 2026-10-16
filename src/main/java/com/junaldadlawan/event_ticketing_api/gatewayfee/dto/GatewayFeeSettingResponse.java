package com.junaldadlawan.event_ticketing_api.gatewayfee.dto;

import com.junaldadlawan.event_ticketing_api.gatewayfee.entity.GatewayFeeSetting;
import com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeePayer;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public record GatewayFeeSettingResponse(
        BigDecimal percentage,
        MoneyDto flatAmount,
        GatewayFeePayer payer,
        BigDecimal foreignPercentage,
        MoneyDto foreignFlatAmount,
        Instant updatedAt) implements Serializable {

    public static GatewayFeeSettingResponse from(GatewayFeeSetting setting) {
        return new GatewayFeeSettingResponse(setting.getPercentage(),
                new MoneyDto(setting.getFlatAmount(), setting.getFlatCurrency()), setting.getPayer(),
                setting.getForeignPercentage(),
                setting.getForeignPercentage() == null && setting.getForeignFlatAmount() == null ? null
                        : new MoneyDto(setting.getForeignFlatAmount() == null ? 0 : setting.getForeignFlatAmount(), setting.getFlatCurrency()),
                setting.getUpdatedAt());
    }
}
