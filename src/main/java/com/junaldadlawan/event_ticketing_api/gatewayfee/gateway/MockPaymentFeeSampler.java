package com.junaldadlawan.event_ticketing_api.gatewayfee.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.List;

/** Used whenever {@code payments.gateway} is not paymongo: no gateway to read, so there is nothing to compare. */
@Component
@ConditionalOnExpression("'${payments.gateway:mock}' != 'paymongo'")
public class MockPaymentFeeSampler implements PaymentFeeSampler {

    @Override
    public String provider() {
        return "MOCK";
    }

    @Override
    public List<PaymentSample> recentPaidPayments(int limit) {
        return List.of();
    }
}
