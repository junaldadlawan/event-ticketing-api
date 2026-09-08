package com.junaldadlawan.event_ticketing_api.user.dto;

import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * DTO for {@link User}
 */
public record UserRequest(
        @NotNull @Size(max = 150) String name,
        @NotNull @Size(max = 255) String email,
        @NotNull @Size(max = 255) String passwordHash,
        @NotNull Role role,
        @NotNull OffsetDateTime createdBy,
        @NotNull OffsetDateTime createdAt,
        @Size(max = 20) String updatedBy,
        OffsetDateTime updatedAt) implements Serializable {
}