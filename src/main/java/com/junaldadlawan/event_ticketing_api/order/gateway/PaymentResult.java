package com.junaldadlawan.event_ticketing_api.order.gateway;

/**
 * Outcome of a {@link PaymentGatewayClient#charge} call. Exactly one of
 * {@code gatewayRef} (on success) or {@code failureReason} (on failure) is
 * populated.
 */
public record PaymentResult(boolean successful, String gatewayRef, String failureReason) {

    public static PaymentResult success(String gatewayRef) {
        return new PaymentResult(true, gatewayRef, null);
    }

    public static PaymentResult failure(String failureReason) {
        return new PaymentResult(false, null, failureReason);
    }
}
