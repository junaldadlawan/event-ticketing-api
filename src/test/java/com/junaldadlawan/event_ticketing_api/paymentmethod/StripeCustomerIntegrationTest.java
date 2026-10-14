package com.junaldadlawan.event_ticketing_api.paymentmethod;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.paymentmethod.gateway.StripeGateway;
import com.junaldadlawan.event_ticketing_api.paymentmethod.service.StripeCustomerService;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Stripe side with the Stripe gateway mocked (no network): a user gets one Stripe Customer, stored on the user and
 * reused, even when two requests arrive at the same time.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StripeCustomerIntegrationTest {

    @MockitoBean
    private StripeGateway stripeGateway;

    @Autowired
    private StripeCustomerService stripeCustomerService;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> userIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        userIds.forEach(userRepository::deleteById);
    }

    private User user() {
        User saved = userRepository.save(User.builder().firstName("Stripe").lastName("Tester")
                .email("stripe-" + UUID.randomUUID() + "@test.local").passwordHash("x").role(Role.CUSTOMER).build());
        userIds.add(saved.getId());
        return saved;
    }

    private void slowCustomerCreation(AtomicInteger created) {
        when(stripeGateway.createCustomer(anyString(), anyString(), any(UUID.class), anyString())).thenAnswer(inv -> {
            created.incrementAndGet();
            Thread.sleep(300);   // long enough for the second request to arrive while the first is still creating
            return "cus_test_" + UUID.randomUUID().toString().replace("-", "");
        });
    }

    private <T> List<T> runTogether(int threads, Callable<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();
        List<T> results = new ArrayList<>();
        for (Future<T> future : futures) {
            results.add(future.get());
        }
        pool.shutdown();
        return results;
    }

    @Test
    void twoSimultaneousFirstRequests_createOneStripeCustomer() throws Exception {
        User user = user();
        AtomicInteger created = new AtomicInteger();
        slowCustomerCreation(created);

        List<String> ids = runTogether(2, () -> stripeCustomerService.getOrCreate(user.getId()));

        assertThat(created.get()).isEqualTo(1);
        assertThat(ids.get(0)).startsWith("cus_test_").isEqualTo(ids.get(1));
        assertThat(jdbc.queryForObject("select stripe_customer_id from users where id = ?", String.class, user.getId())).isEqualTo(ids.get(0));
        verify(stripeGateway, times(1)).createCustomer(anyString(), anyString(), any(UUID.class), anyString());
    }

    @Test
    void theCustomerIsReusedAfterwards_andCreatedWithTheServersOwnUserIdAndEmail() throws Exception {
        User user = user();
        when(stripeGateway.createCustomer(anyString(), anyString(), any(UUID.class), anyString())).thenReturn("cus_once");

        assertThat(stripeCustomerService.getOrCreate(user.getId())).isEqualTo("cus_once");
        assertThat(stripeCustomerService.getOrCreate(user.getId())).isEqualTo("cus_once");

        verify(stripeGateway, times(1)).createCustomer(user.getEmail(), "Stripe Tester", user.getId(), "app-user-" + user.getId());
    }

    @Test
    void twoSimultaneousSetupIntentRequests_overHttp_createOneCustomer() throws Exception {
        User user = user();
        String token = "Bearer " + jwtService.generateAccessToken(user);
        AtomicInteger created = new AtomicInteger();
        slowCustomerCreation(created);
        when(stripeGateway.live()).thenReturn(true);
        when(stripeGateway.createSetupIntent(anyString())).thenReturn("seti_real_secret");

        List<Integer> statuses = runTogether(2, () -> mockMvc.perform(post("/api/v1/users/me/payment-methods/setup-intent")
                .header("Authorization", token)).andReturn().getResponse().getStatus());

        assertThat(statuses).containsOnly(200);
        assertThat(created.get()).isEqualTo(1);
    }
}
