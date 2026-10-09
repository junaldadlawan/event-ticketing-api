package com.junaldadlawan.event_ticketing_api.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A birth date that is in the past and at most {@link ValidBirthDateValidator#MAX_AGE_YEARS} years ago. Null passes (use @NotNull). */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidBirthDateValidator.class)
public @interface ValidBirthDate {
    String message() default "must be a date in the past, no more than 120 years ago";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
