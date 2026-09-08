package com.junaldadlawan.event_ticketing_api.user.dto;

import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO for {@link User}
 */
public record UserUpdateRequest(
        @NotNull @Size(max = 150) String name,
        @NotNull @Size(max = 255) String email,
        @NotNull Role role) {
}
