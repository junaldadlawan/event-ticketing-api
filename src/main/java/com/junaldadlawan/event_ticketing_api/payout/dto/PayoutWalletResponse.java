package com.junaldadlawan.event_ticketing_api.payout.dto;

import com.junaldadlawan.event_ticketing_api.payout.gateway.PayoutTransferClient.WalletInfo;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;

import java.io.Serializable;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * The wallet the platform pays organizers from (admin only). {@code status} is PayMongo's word: only an {@code activated} wallet can send
 * ({@code activated} repeats that as a flag). {@code balance.available} can be sent now, {@code balance.pending} is still in transit.
 * A limit of 999999999999999999 means PayMongo sets none. {@code matchesConfiguredAccount} is true when the wallet's account number is the
 * one in {@code PAYMONGO_WALLET_ACCOUNT_NUMBER} (null when nothing is configured to compare, as in mock mode). {@code provider} is
 * PAYMONGO, or MOCK outside PayMongo mode (a simulated wallet).
 */
public record PayoutWalletResponse(
        String provider,
        String id,
        String status,
        boolean activated,
        boolean livemode,
        boolean isDefault,
        String type,
        Instant statementOfAcceptanceSignedAt,
        Balance balance,
        Limits limits,
        Usage usage,
        Account account,
        Instant createdAt,
        Instant updatedAt,
        Boolean matchesConfiguredAccount) implements Serializable {

    public record Balance(MoneyDto available, MoneyDto pending) implements Serializable {
    }

    public record Limits(MoneyDto balance, MoneyDto inwardDaily, MoneyDto inwardMonthly, MoneyDto outwardDaily,
                         MoneyDto outwardMonthly) implements Serializable {
    }

    /** What has moved through the wallet so far (today / this month). */
    public record Usage(MoneyDto inwardDaily, MoneyDto inwardMonthly, MoneyDto outwardDaily, MoneyDto outwardMonthly) implements Serializable {
    }

    public record Account(String provider, String accountName, String accountNumber, String currency) implements Serializable {
    }

    public static PayoutWalletResponse from(WalletInfo w) {
        String currency = w.account() == null || w.account().currency() == null ? "PHP" : w.account().currency();
        var limits = w.limits();
        var usage = w.usage();
        var account = w.account();
        return new PayoutWalletResponse(w.provider(), w.id(), w.status(), "activated".equalsIgnoreCase(w.status()), w.livemode(), w.isDefault(),
                w.type(), instant(w.statementOfAcceptanceSignedAt()),
                new Balance(money(w.availableBalance(), currency), money(w.pendingBalance(), currency)),
                new Limits(money(limits.balance(), currency), money(limits.inwardDaily(), currency), money(limits.inwardMonthly(), currency),
                        money(limits.outwardDaily(), currency), money(limits.outwardMonthly(), currency)),
                new Usage(money(usage.inwardDaily(), currency), money(usage.inwardMonthly(), currency), money(usage.outwardDaily(), currency),
                        money(usage.outwardMonthly(), currency)),
                account == null ? null : new Account(account.provider(), account.accountName(), account.accountNumber(), account.currency()),
                instant(w.createdAt()), instant(w.updatedAt()), w.matchesConfiguredAccount());
    }

    private static MoneyDto money(long amount, String currency) {
        return new MoneyDto(amount, currency);
    }

    private static Instant instant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
