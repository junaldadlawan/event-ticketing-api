package com.junaldadlawan.event_ticketing_api.user.dto;

import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO for {@link com.junaldadlawan.event_ticketing_api.user.entity.User}
 */
public record UserResponse(
        UUID id,
        @NotNull @Size(max = 150) String name,
        @NotNull @Size(max = 255) String email,
        @NotNull Role role,
        @NotNull String createdBy,
        @NotNull Instant createdAt,
        @Size(max = 20) String updatedBy,
        Instant updatedAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedBy(),
                user.getCreatedAt(),
                user.getUpdatedBy(),
                user.getUpdatedAt());
    }
}