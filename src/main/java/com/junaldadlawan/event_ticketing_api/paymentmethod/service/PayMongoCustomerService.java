package com.junaldadlawan.event_ticketing_api.paymentmethod.service;

import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.order.gateway.PaymentGatewayClient;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** The user's PayMongo customer, the owner of their vaulted cards. Created the first time a card is saved with a payment. */
@Service
@RequiredArgsConstructor
public class PayMongoCustomerService {

    private final UserRepository userRepository;
    private final PaymentGatewayClient paymentGatewayClient;

    /** The user row is locked while the customer is created, so a second request for the same user finds the stored id instead of creating another. */
    // its own transaction: a failure here (PayMongo refusing the customer) must not mark the checkout that asked for it rollback-only
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public String getOrCreate(UUID userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User " + userId + " not found"));
        if (user.getPaymongoCustomerId() != null && !user.getPaymongoCustomerId().isBlank()) {
            return user.getPaymongoCustomerId();
        }
        String first = user.getFirstName() == null || user.getFirstName().isBlank() ? "Customer" : user.getFirstName().trim();
        String last = user.getLastName() == null || user.getLastName().isBlank() ? first : user.getLastName().trim();
        user.setPaymongoCustomerId(paymentGatewayClient.createCustomer(user.getEmail(), first, last));
        userRepository.save(user);
        return user.getPaymongoCustomerId();
    }
}
