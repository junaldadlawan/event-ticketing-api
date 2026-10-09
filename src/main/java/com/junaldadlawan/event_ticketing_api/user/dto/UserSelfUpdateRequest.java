package com.junaldadlawan.event_ticketing_api.user.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.UserInput;
import com.junaldadlawan.event_ticketing_api.common.validation.ValidBirthDate;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO for {@link User} self-service partial update ({@code PATCH /users/me}).
 * Same "null means leave unchanged" convention as {@link UserUpdateRequest},
 * but deliberately has no {@code role} field at all - not just ignored, but
 * structurally absent - so there is no path for a caller to elevate their
 * own privileges through this endpoint.
 */
public record UserSelfUpdateRequest(
        @Size(max = 150) @Pattern(regexp = UserInput.NAME_REGEX, message = UserInput.NAME_MESSAGE) String firstName,
        @Size(max = 150) @Pattern(regexp = UserInput.MIDDLE_NAME_REGEX, message = UserInput.NAME_MESSAGE) String middleName,
        @Size(max = 150) @Pattern(regexp = UserInput.NAME_REGEX, message = UserInput.NAME_MESSAGE) String lastName,
        @ValidBirthDate @tools.jackson.databind.annotation.JsonDeserialize(using = com.junaldadlawan.event_ticketing_api.common.validation.StrictLocalDateDeserializer.class) java.time.LocalDate birthDate,
        @Size(max = 20) @Pattern(regexp = UserInput.PHONE_OR_EMPTY_REGEX, message = UserInput.PHONE_MESSAGE) String phoneNumber,
        @Size(max = 255) @Pattern(regexp = UserInput.EMAIL_REGEX, message = UserInput.EMAIL_MESSAGE) String email,
        /** Profile picture: one of our own upload URLs; null = unchanged, "" = remove. */
        @Size(max = 500) String avatarUrl) {
}
