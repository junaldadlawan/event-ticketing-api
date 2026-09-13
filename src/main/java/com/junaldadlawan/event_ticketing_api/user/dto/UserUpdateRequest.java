package com.junaldadlawan.event_ticketing_api.user.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * DTO for {@link User} partial update (admin only - {@code PATCH /users/{id}}).
 * All fields optional: {@code null} on any field means leave unchanged; a
 * present-but-blank {@code name}/{@code email} is rejected. Mirrors {@code
 * EventUpdateRequest}/{@code VenueUpdateRequest}'s exact convention,
 * including {@code @NoHtml} on the free-text {@code name} field.
 */
public record UserUpdateRequest(
        @Size(max = 150) @NoHtml String name,
        @Size(max = 255) @Email String email,
        Role role) {
}
