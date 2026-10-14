package com.junaldadlawan.event_ticketing_api.paymentmethod.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * Body of {@code POST /api/v1/users/me/payment-methods}. With Stripe the token is a {@code pm_...} id and the card details
 * are read from Stripe (anything sent for them is ignored); with the mock gateway it is {@code tok_<brand>} and the
 * brand, last4 and expiry come from here. There is no field for a card number or security code, and a bare 12 to 19 digit
 * number is refused as a token. {@code primary} is ignored for a user's first card, which is always primary.
 */
public record PaymentMethodRequest(
        @NotBlank
        @Pattern(regexp = "(?!\\d{12,19}$)(pm|tok)_[A-Za-z0-9_]{2,100}", message = "must be a payment token (pm_... or tok_...), not a card number")
        String paymentToken,

        Boolean primary,

        @Size(max = 100)
        @NoHtml
        @Pattern(regexp = "[\\p{L}][\\p{L} .,'\\u2019-]*", message = "may only contain letters, spaces and . , ' -")
        String holderName,

        @Size(max = 30)
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9 ._-]*", message = "may only contain letters, digits, spaces, . _ and -")
        String brand,

        @Pattern(regexp = "\\d{4}", message = "must be exactly 4 digits")
        String last4,

        @Min(1)
        @Max(12)
        Integer expMonth,

        @Min(2000)
        @Max(2200)
        Integer expYear) implements Serializable {
}
