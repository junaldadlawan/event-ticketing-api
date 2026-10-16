package com.junaldadlawan.event_ticketing_api.gatewayfee.gateway;

import com.junaldadlawan.event_ticketing_api.common.exception.ApiException;
import com.junaldadlawan.event_ticketing_api.gatewayfee.gateway.PaymentFeeSampler.PaymentSample;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class PayMongoPaymentFeeSamplerTest {

    private static final String BASIC = "Basic " + Base64.getEncoder().encodeToString("sk_test_not_a_real_key:".getBytes(StandardCharsets.UTF_8));

    private MockRestServiceServer server;
    private PayMongoPaymentFeeSampler sampler;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://paymongo.test/v1").defaultHeader(HttpHeaders.AUTHORIZATION, BASIC);
        server = MockRestServiceServer.bindTo(builder).build();
        sampler = new PayMongoPaymentFeeSampler(builder.build());
    }

    @Test
    void readsThePaidPayments_withTheFeeTheForeignFeeAndTheNet_andSkipsTheOthers() {
        server.expect(requestTo("https://paymongo.test/v1/payments?limit=3")).andExpect(method(HttpMethod.GET)).andExpect(header(HttpHeaders.AUTHORIZATION, BASIC))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body("""
                        {"has_more":false,"data":[
                          {"id":"pay_1","attributes":{"status":"paid","amount":10500,"currency":"PHP","fee":1868,"foreign_fee":105,"tax_amount":null,
                            "net_amount":8527,"paid_at":1791999766,"livemode":false,"source":{"type":"card","brand":"visa","country":"US","last4":"4343"}}},
                          {"id":"pay_2","attributes":{"status":"failed","amount":5000,"fee":0,"net_amount":0,"source":{"type":"card"}}},
                          {"id":"pay_3","attributes":{"status":"paid","amount":10000,"currency":"PHP","fee":1850,"foreign_fee":0,"net_amount":8150,"livemode":true}}]}"""));

        List<PaymentSample> samples = sampler.recentPaidPayments(3);

        assertThat(samples).hasSize(2);
        assertThat(samples.get(0)).isEqualTo(new PaymentSample("pay_1", 10_500, "PHP", 1_868, 105, 0, 8_527, "card", "visa", "US", 1_791_999_766L, false));
        assertThat(samples.get(1).paymentId()).isEqualTo("pay_3");
        assertThat(samples.get(1).paidAt()).isNull();
        assertThat(samples.get(1).cardBrand()).isNull();
        assertThat(sampler.provider()).isEqualTo("PAYMONGO");
        server.verify();
    }

    @Test
    void anErrorAnHtmlPageOrAnOutage_is502() {
        server.expect(requestTo("https://paymongo.test/v1/payments?limit=5")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> sampler.recentPaidPayments(5)).isInstanceOf(ApiException.class).hasMessageContaining("could not be read");

        server.reset();
        server.expect(requestTo("https://paymongo.test/v1/payments?limit=5")).andRespond(withStatus(HttpStatus.OK).contentType(MediaType.TEXT_HTML).body("<html/>"));
        assertThatThrownBy(() -> sampler.recentPaidPayments(5)).isInstanceOf(ApiException.class);
    }
}
