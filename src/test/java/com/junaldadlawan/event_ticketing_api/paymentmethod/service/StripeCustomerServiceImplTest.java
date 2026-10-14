package com.junaldadlawan.event_ticketing_api.paymentmethod.service;

import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.paymentmethod.gateway.StripeGateway;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeCustomerServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private StripeGateway stripeGateway;

    @Test
    void getOrCreate_createsTheCustomerOnce_withAnIdempotencyKeyFromTheUserId_andStoresIt() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).firstName("Jamie").lastName("Cruz").email("jamie@test.local").build();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(stripeGateway.createCustomer("jamie@test.local", "Jamie Cruz", userId, "app-user-" + userId)).thenReturn("cus_new");
        StripeCustomerServiceImpl service = new StripeCustomerServiceImpl(userRepository, stripeGateway);

        assertThat(service.getOrCreate(userId)).isEqualTo("cus_new");

        assertThat(user.getStripeCustomerId()).isEqualTo("cus_new");
        verify(userRepository).save(user);
    }

    @Test
    void getOrCreate_reusesTheStoredCustomer() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).firstName("Jamie").lastName("Cruz").email("jamie@test.local").stripeCustomerId("cus_old").build();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

        assertThat(new StripeCustomerServiceImpl(userRepository, stripeGateway).getOrCreate(userId)).isEqualTo("cus_old");

        verify(stripeGateway, never()).createCustomer(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getOrCreate_unknownUser_throwsResourceNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new StripeCustomerServiceImpl(userRepository, stripeGateway).getOrCreate(userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
