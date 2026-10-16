package com.junaldadlawan.event_ticketing_api.analytics.service;

import com.junaldadlawan.event_ticketing_api.analytics.dto.FinanceSummaryResponse;
import com.junaldadlawan.event_ticketing_api.analytics.dto.OrganizationFinanceResponse;
import com.junaldadlawan.event_ticketing_api.analytics.repository.FinanceSummaryRepository;
import com.junaldadlawan.event_ticketing_api.analytics.repository.FinanceSummaryRepository.Figures;
import com.junaldadlawan.event_ticketing_api.analytics.repository.FinanceSummaryRepository.PayoutTotal;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.gatewayfee.service.GatewayFeeActualSyncService;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceSummaryServiceImpl implements FinanceSummaryService {

    static final String DEFAULT_CURRENCY = "PHP";

    private final FinanceSummaryRepository repository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationAccessGuard accessGuard;
    private final GatewayFeeActualSyncService gatewayFeeActualSync;

    @Override
    @Transactional(readOnly = true)
    public FinanceSummaryResponse platformSummary(LocalDate from, LocalDate to, String currency) {
        accessGuard.requireAdmin();
        String cur = currency(currency);
        FinancePeriod period = FinancePeriod.resolve(from, to);
        try {
            gatewayFeeActualSync.fillMissing();   // the gateway's real fee on orders that lack it; never breaks the summary
        } catch (RuntimeException e) {
            log.warn("Could not read the gateway's real fees: {}", e.getMessage());
        }
        Figures f = repository.figures(period.fromInstant(), period.toInstant(), cur, null);
        Map<String, PayoutTotal> payouts = repository.payoutsByStatus(period.fromInstant(), period.toInstant(), cur, null);
        return new FinanceSummaryResponse(period.from(), period.to(), cur,
                repository.otherCurrencies(period.fromInstant(), period.toInstant(), cur, null),
                new FinanceSummaryResponse.Orders(f.orders(), f.refundedOrders()),
                new FinanceSummaryResponse.Sales(money(f.gross(), cur), money(f.refunds(), cur), money(f.gross() - f.refunds(), cur)),
                new FinanceSummaryResponse.Platform(money(f.platformFeeKept(), cur), money(f.gatewayBorneByPlatform(), cur),
                        money(f.platformFeeKept() - f.gatewayBorneByPlatform(), cur)),
                new FinanceSummaryResponse.GatewayFees(money(f.gatewayCharged(), cur), money(f.gatewayPaidByCustomer(), cur),
                        money(f.gatewayBorneByOrganization(), cur), money(f.gatewayBorneByPlatform(), cur), f.foreignCardOrders(), f.unknownFeeOrders()),
                new FinanceSummaryResponse.Organizers(money(f.share(), cur), money(f.paidOutShare(), cur), money(f.inPayoutsShare(), cur),
                        money(f.owedShare(), cur)),
                payoutTotals(payouts, cur));
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationFinanceResponse organizationSummary(UUID organizationId, LocalDate from, LocalDate to, String currency) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new ResourceNotFoundException("Organization " + organizationId + " not found");
        }
        if (!accessGuard.isAdmin()
                && !accessGuard.hasRole(accessGuard.currentUserId(), organizationId, OrganizationRole.OWNER)) {
            throw new ForbiddenException("Only the organization's owner or an admin may view its income");
        }
        String cur = currency(currency);
        FinancePeriod period = FinancePeriod.resolve(from, to);
        Figures f = repository.figures(period.fromInstant(), period.toInstant(), cur, organizationId);
        Map<String, PayoutTotal> payouts = repository.payoutsByStatus(period.fromInstant(), period.toInstant(), cur, organizationId);
        return new OrganizationFinanceResponse(organizationId, period.from(), period.to(), cur,
                repository.otherCurrencies(period.fromInstant(), period.toInstant(), cur, organizationId), f.orders(),
                new OrganizationFinanceResponse.Sales(money(f.gross(), cur), money(f.refunds(), cur), money(f.gross() - f.refunds(), cur)),
                new OrganizationFinanceResponse.Deductions(money(f.platformFeeDeducted(), cur), money(f.gatewayFeeDeducted(), cur),
                        money(f.platformFeeDeducted() + f.gatewayFeeDeducted(), cur)),
                money(f.share(), cur), money(f.paidOutShare(), cur), money(f.inPayoutsShare(), cur), money(f.owedShare(), cur),
                payoutTotals(payouts, cur));
    }

    private static String currency(String currency) {
        if (currency == null || currency.isBlank()) {
            return DEFAULT_CURRENCY;
        }
        String cur = currency.trim().toUpperCase();
        if (!cur.matches("[A-Z]{3}")) {
            throw new BadRequestException("currency must be a 3-letter code such as PHP");
        }
        return cur;
    }

    private static FinanceSummaryResponse.Payouts payoutTotals(Map<String, PayoutTotal> totals, String currency) {
        return new FinanceSummaryResponse.Payouts(payoutTotal(totals.get("SCHEDULED"), currency), payoutTotal(totals.get("PROCESSING"), currency),
                payoutTotal(totals.get("PAID"), currency), payoutTotal(totals.get("FAILED"), currency));
    }

    private static FinanceSummaryResponse.PayoutTotal payoutTotal(PayoutTotal total, String currency) {
        return total == null ? new FinanceSummaryResponse.PayoutTotal(0, money(0, currency))
                : new FinanceSummaryResponse.PayoutTotal(total.count(), money(total.net(), currency));
    }

    private static MoneyDto money(long amount, String currency) {
        return new MoneyDto(amount, currency);
    }
}
