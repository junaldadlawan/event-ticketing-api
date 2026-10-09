package com.junaldadlawan.event_ticketing_api.user.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.UserInput;
import com.junaldadlawan.event_ticketing_api.common.validation.ValidBirthDate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Body of {@code POST /auth/register}. The name comes as {@code firstName}, optional {@code middleName} and
 * {@code lastName}, plus the required {@code birthDate} (ISO yyyy-MM-dd, in the past) and an optional {@code phoneNumber} (+<digits>). The plaintext password travels in {@code passwordHash} (the name the web app already sends; it is
 * hashed before saving). There is no role: every new account is a CUSTOMER, and any extra property in the body is
 * ignored.
 */
public record RegisterRequest(
        @NotBlank @Size(max = 150) @Pattern(regexp = UserInput.NAME_REGEX, message = UserInput.NAME_MESSAGE) String firstName,
        @Size(max = 150) @Pattern(regexp = UserInput.MIDDLE_NAME_REGEX, message = UserInput.NAME_MESSAGE) String middleName,
        @NotBlank @Size(max = 150) @Pattern(regexp = UserInput.NAME_REGEX, message = UserInput.NAME_MESSAGE) String lastName,
        @NotNull @ValidBirthDate @tools.jackson.databind.annotation.JsonDeserialize(using = com.junaldadlawan.event_ticketing_api.common.validation.StrictLocalDateDeserializer.class) LocalDate birthDate,
        @Size(max = 20) @Pattern(regexp = UserInput.PHONE_OR_EMPTY_REGEX, message = UserInput.PHONE_MESSAGE) String phoneNumber,
        @NotBlank @Size(max = 255) @Pattern(regexp = UserInput.EMAIL_REGEX, message = UserInput.EMAIL_MESSAGE) String email,
        @NotBlank @Size(max = 255) String passwordHash) implements Serializable {
}
