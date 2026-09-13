package com.junaldadlawan.event_ticketing_api.user.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * DTO for {@link User} self-service partial update ({@code PATCH /users/me}).
 * Same "null means leave unchanged" convention as {@link UserUpdateRequest},
 * but deliberately has no {@code role} field at all - not just ignored, but
 * structurally absent - so there is no path for a caller to elevate their
 * own privileges through this endpoint.
 */
public record UserSelfUpdateRequest(
        @Size(max = 150) @NoHtml String name,
        @Size(max = 255) @Email String email) {
}
