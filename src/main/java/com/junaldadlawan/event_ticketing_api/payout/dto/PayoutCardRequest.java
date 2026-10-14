package com.junaldadlawan.event_ticketing_api.payout.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * Body of {@code POST /api/v1/organizations/{orgId}/payout-cards}. What the payment gateway returned for the card, never
 * the card itself: the token must look like a token (a bare 12 to 19 digit number is refused), and there is no field for
 * a full number or a security code. {@code primary} is ignored for an organization's first card, which is always primary.
 */
public record PayoutCardRequest(
        @NotBlank
        @Size(max = 100)
        @NoHtml
        @Pattern(regexp = "[\\p{L}][\\p{L} .,'\\u2019-]*", message = "may only contain letters, spaces and . , ' -")
        String holderName,

        @NotBlank
        @Size(max = 30)
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9 .-]*", message = "may only contain letters, digits, spaces, . and -")
        String brand,

        @NotBlank
        @Pattern(regexp = "\\d{4}", message = "must be exactly 4 digits")
        String last4,

        @NotNull
        @Min(1)
        @Max(12)
        Integer expMonth,

        @NotNull
        @Min(2000)
        @Max(2200)
        Integer expYear,

        @NotBlank
        @Pattern(regexp = "(?!\\d{12,19}$)[A-Za-z0-9_.-]{8,255}", message = "must be the payment gateway's token (letters, digits, _ . -), not a card number")
        String paymentToken,

        Boolean primary) implements Serializable {
}
