package com.junaldadlawan.event_ticketing_api.refund.controller;

import com.junaldadlawan.event_ticketing_api.refund.dto.RefundCreateRequest;
import com.junaldadlawan.event_ticketing_api.refund.dto.RefundResponse;
import com.junaldadlawan.event_ticketing_api.refund.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Falls through to SecurityConfig's generic {@code anyRequest().authenticated()}
 * - {@code /api/v1/orders/**} has no explicit matcher of its own (Phase 6a),
 * and this stays under that same prefix. Real visibility enforcement lives
 * in {@link RefundService}.
 */
@RestController
@RequestMapping("/api/v1/orders/{orderId}/refunds")
@RequiredArgsConstructor
public class OrderRefundController {

    private final RefundService refundService;

    @GetMapping
    public List<RefundResponse> list(@PathVariable UUID orderId) {
        return refundService.listRefunds(orderId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RefundResponse create(@PathVariable UUID orderId, @Valid @RequestBody RefundCreateRequest request) {
        return refundService.createRefund(orderId, request);
    }
}
