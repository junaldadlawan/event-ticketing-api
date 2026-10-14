package com.junaldadlawan.event_ticketing_api.paymentmethod.service;

import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.paymentmethod.gateway.StripeGateway;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StripeCustomerServiceImpl implements StripeCustomerService {

    private final UserRepository userRepository;
    private final StripeGateway stripeGateway;

    /**
     * The user row is locked while the Customer is created, so a second request for the same user waits and then finds the
     * stored id instead of creating another. The idempotency key repeats the guarantee on Stripe's side.
     */
    @Override
    @Transactional
    public String getOrCreate(UUID userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User " + userId + " not found"));
        if (user.getStripeCustomerId() != null && !user.getStripeCustomerId().isBlank()) {
            return user.getStripeCustomerId();
        }
        String customerId = stripeGateway.createCustomer(user.getEmail(), user.fullName(), userId, "app-user-" + userId);
        user.setStripeCustomerId(customerId);
        userRepository.save(user);
        return customerId;
    }
}
