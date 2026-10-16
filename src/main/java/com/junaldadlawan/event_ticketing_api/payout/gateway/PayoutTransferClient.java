package com.junaldadlawan.event_ticketing_api.payout.gateway;

import java.util.List;
import java.util.Map;

/**
 * Moves an organization's payout out of the platform's account. {@code payments.gateway=paymongo} sends a real transfer from the
 * PayMongo wallet; every other mode only simulates one so the rest of the payout flow can be tried without money.
 */
public interface PayoutTransferClient {

    /** True when the payout destination is a bank/e-wallet account (PayMongo); false when the old payout card is used. */
    boolean requiresAccount();

    /** The banks and e-wallets that can receive a transfer. */
    List<Bank> banks();

    /**
     * Submits one transfer. A rejection PayMongo states is returned as a FAILED result (nothing was sent, so a retry is safe); an
     * outcome that cannot be known (provider down, timeout) throws, because the money may or may not have moved.
     */
    TransferResult send(TransferRequest request);

    /** The current state of a transfer already submitted. */
    TransferResult retrieve(String transferId);

    /** The wallet payouts are sent from: its status, balance, limits and account. 404 when the provider account has no wallet yet. */
    WalletInfo wallet();

    record Bank(String name, String code) {
    }

    /** Amounts in centavos. A limit of 999999999999999999 means PayMongo sets none. */
    record WalletLimits(long balance, long inwardDaily, long inwardMonthly, long outwardDaily, long outwardMonthly) {
    }

    /** How much has moved through the wallet so far today and this month. */
    record WalletUsage(long inwardDaily, long inwardMonthly, long outwardDaily, long outwardMonthly) {
    }

    record WalletAccount(String provider, String accountName, String accountNumber, String currency) {
    }

    /**
     * {@code provider} is PAYMONGO or MOCK; {@code status} is PayMongo's own word ({@code activated} or {@code deactivated}). Balances are
     * centavos: {@code availableBalance} can be sent now, {@code pendingBalance} is still in transit. {@code matchesConfiguredAccount} says
     * whether the wallet is the one named in the server configuration (null when nothing is configured to compare, as in mock mode).
     */
    record WalletInfo(String provider, String id, String status, boolean livemode, boolean isDefault, String type,
                      String statementOfAcceptanceSignedAt, long availableBalance, long pendingBalance, WalletLimits limits,
                      WalletUsage usage, WalletAccount account, String createdAt, String updatedAt, Boolean matchesConfiguredAccount) {
    }

    /** Amounts in minor units (centavos). {@code reference} is unique per attempt. The destination fields are null when {@link #requiresAccount()} is false. */
    record TransferRequest(String reference, long amount, String currency, String bankCode, String accountName,
                           String accountNumber, String description, Map<String, String> metadata) {
    }

    enum Status { PENDING, SUCCEEDED, FAILED }

    record TransferResult(String transferId, Status status, String failureReason) {
    }
}
