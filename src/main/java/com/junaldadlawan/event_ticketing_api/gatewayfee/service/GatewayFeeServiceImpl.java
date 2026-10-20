package com.junaldadlawan.event_ticketing_api.gatewayfee.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditDetails;
import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditLogService;
import com.junaldadlawan.event_ticketing_api.gatewayfee.dto.GatewayFeeSettingRequest;
import com.junaldadlawan.event_ticketing_api.gatewayfee.dto.GatewayFeeSettingResponse;
import com.junaldadlawan.event_ticketing_api.gatewayfee.entity.GatewayFeeSetting;
import com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeePayer;
import com.junaldadlawan.event_ticketing_api.gatewayfee.repository.GatewayFeeSettingRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class GatewayFeeServiceImpl implements GatewayFeeService {

    private final GatewayFeeSettingRepository settingRepository;
    private final OrganizationAccessGuard accessGuard;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public GatewayFeeQuote quote(long baseAmount, String currency) {
        if (baseAmount <= 0) {
            return GatewayFeeQuote.NONE;
        }
        GatewayFeeSetting setting = settingRepository.findById(GatewayFeeSetting.ROW_ID).orElse(null);
        if (setting == null) {
            return GatewayFeeQuote.NONE;
        }
        // A flat amount in another currency than the order is skipped, the same rule as the platform fee.
        boolean flatApplies = setting.getFlatCurrency().equalsIgnoreCase(currency);
        long fee = fee(baseAmount, setting.getPercentage(), setting.getFlatAmount(), flatApplies);
        long foreignFee = fee;
        if (setting.getForeignPercentage() != null || setting.getForeignFlatAmount() != null) {
            BigDecimal foreignPercentage = setting.getForeignPercentage() == null ? BigDecimal.ZERO : setting.getForeignPercentage();
            foreignFee = fee(baseAmount, foreignPercentage, setting.getForeignFlatAmount() == null ? 0 : setting.getForeignFlatAmount(), flatApplies);
        }
        if (fee <= 0 && foreignFee <= 0) {
            return GatewayFeeQuote.NONE;
        }
        return new GatewayFeeQuote(fee, setting.getPayer(), setting.getPercentage(), setting.getFlatAmount(), foreignFee,
                setting.getForeignPercentage(), setting.getForeignFlatAmount());
    }

    /** {@code base x percentage}, rounded half up to the minor unit, plus the flat amount when it is in the order's currency. */
    private static long fee(long baseAmount, BigDecimal percentage, long flatAmount, boolean flatApplies) {
        long fee = BigDecimal.valueOf(baseAmount).multiply(percentage).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValueExact();
        return flatApplies && flatAmount > 0 ? fee + flatAmount : fee;
    }

    @Override
    @Transactional(readOnly = true)
    public GatewayFeeSettingResponse get() {
        accessGuard.requireAdmin();
        return GatewayFeeSettingResponse.from(settingRepository.findById(GatewayFeeSetting.ROW_ID).orElseGet(GatewayFeeSetting::new));
    }

    /** The foreign rate shares the domestic flat amount's currency; sending neither number means a foreign card costs the domestic rate. */
    private static void setForeignRate(GatewayFeeSetting setting, GatewayFeeSettingRequest request) {
        if (request.foreignPercentage() == null && request.foreignFlatAmount() == null) {
            setting.setForeignPercentage(null);
            setting.setForeignFlatAmount(null);
            return;
        }
        if (request.foreignFlatAmount() != null) {
            if (request.flatAmount() != null && !request.flatAmount().currency().equals(request.foreignFlatAmount().currency())) {
                throw new BadRequestException("foreignFlatAmount must be in the same currency as flatAmount");
            }
            if (request.flatAmount() == null) {
                setting.setFlatCurrency(request.foreignFlatAmount().currency());
            }
        }
        setting.setForeignPercentage(request.foreignPercentage() == null ? BigDecimal.ZERO : request.foreignPercentage());
        setting.setForeignFlatAmount(request.foreignFlatAmount() == null ? 0L : request.foreignFlatAmount().amount());
    }

    @Override
    @Transactional
    public GatewayFeeSettingResponse update(GatewayFeeSettingRequest request) {
        accessGuard.requireAdmin();
        if (request.payer() == GatewayFeePayer.PLATFORM) {
            throw new BadRequestException("payer must be CUSTOMER or ORGANIZATION: the platform cannot be the payer of the gateway fee");
        }
        GatewayFeeSetting setting = settingRepository.findById(GatewayFeeSetting.ROW_ID).orElseGet(GatewayFeeSetting::new);
        Map<String, Object> before = AuditDetails.snapshot(GatewayFeeSettingResponse.from(setting), GatewayFeeSettingRequest.class);
        setting.setPercentage(request.percentage());
        setting.setFlatAmount(request.flatAmount() == null ? 0 : request.flatAmount().amount());
        if (request.flatAmount() != null) {
            setting.setFlatCurrency(request.flatAmount().currency());
        }
        setting.setPayer(request.payer());
        setForeignRate(setting, request);
        GatewayFeeSetting saved = settingRepository.save(setting);
        GatewayFeeSettingResponse response = GatewayFeeSettingResponse.from(saved);
        Map<String, Object> changes = AuditDetails.changes(before, AuditDetails.snapshot(response, GatewayFeeSettingRequest.class));
        if (changes != null) {
            auditLogService.recordByCurrentUser("gateway_fee.set", "GatewayFeeSetting", null, null, null, changes);   // a single settings row: no target id
        }
        return response;
    }
}
