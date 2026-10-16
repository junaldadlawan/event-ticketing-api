package com.junaldadlawan.event_ticketing_api.payout.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * Body of {@code PUT /api/v1/organizations/{orgId}/payout-account}: the bank or e-wallet the organization is paid out to. Pick the
 * bank from {@code GET /api/v1/payout-banks} and send its {@code code} as {@code bankCode}. {@code accountName} must match the name
 * the bank has on the account, or the transfer fails.
 */
public record PayoutAccountRequest(
        @NotBlank
        @Pattern(regexp = "[A-Z0-9]{8,11}", message = "must be the bank's code from the bank list (8 to 11 capital letters or digits)")
        String bankCode,

        @NotBlank
        @Size(max = 100)
        @NoHtml
        String bankName,

        @NotBlank
        @Size(max = 150)
        @NoHtml
        String accountName,

        @NotBlank
        @Pattern(regexp = "\\d{5,20}", message = "must be 5 to 20 digits")
        String accountNumber) implements Serializable {
}
