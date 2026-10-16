package com.junaldadlawan.event_ticketing_api.gatewayfee.gateway;

import java.util.List;

/** Reads what the payment gateway really charged on recent payments, so the fee setting can be compared with it. */
public interface PaymentFeeSampler {

    /** PAYMONGO, or MOCK outside PayMongo mode (nothing to read). */
    String provider();

    /** The most recent paid payments, newest first, at most {@code limit}. Throws a 502 when the gateway cannot be read. */
    List<PaymentSample> recentPaidPayments(int limit);

    /**
     * Amounts in centavos. {@code fee} is the gateway's base fee and {@code foreignFee} its surcharge on a card issued abroad; both are taken
     * out of {@code amount}, and {@code netAmount} is what is left. {@code paidAt} is epoch seconds.
     */
    record PaymentSample(String paymentId, long amount, String currency, long fee, long foreignFee, long taxAmount, long netAmount,
                         String sourceType, String cardBrand, String cardCountry, Long paidAt, boolean livemode) {
    }
}
