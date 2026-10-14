package com.junaldadlawan.event_ticketing_api.user.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.UserInput;
import com.junaldadlawan.event_ticketing_api.common.validation.ValidBirthDate;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO for {@link User} partial update (admin only - {@code PATCH /users/{id}}).
 * All fields optional: {@code null} on any field means leave unchanged; a
 * present-but-blank {@code firstName}/{@code lastName}/{@code email} is rejected ({@code middleName}: an empty value
 * removes it). Mirrors {@code EventUpdateRequest}/{@code VenueUpdateRequest}'s convention; the name rules in
 * {@code UserInput} already exclude markup characters.
 */
public record UserUpdateRequest(
        @Size(max = 150) @Pattern(regexp = UserInput.NAME_REGEX, message = UserInput.NAME_MESSAGE) String firstName,
        @Size(max = 150) @Pattern(regexp = UserInput.MIDDLE_NAME_REGEX, message = UserInput.NAME_MESSAGE) String middleName,
        @Size(max = 150) @Pattern(regexp = UserInput.NAME_REGEX, message = UserInput.NAME_MESSAGE) String lastName,
        @ValidBirthDate @tools.jackson.databind.annotation.JsonDeserialize(using = com.junaldadlawan.event_ticketing_api.common.validation.StrictLocalDateDeserializer.class) java.time.LocalDate birthDate,
        @Size(max = 20) @Pattern(regexp = UserInput.PHONE_OR_EMPTY_REGEX, message = UserInput.PHONE_MESSAGE) String phoneNumber,
        @Size(max = 255) @Pattern(regexp = UserInput.EMAIL_REGEX, message = UserInput.EMAIL_MESSAGE) String email,
        Role role) {
}
