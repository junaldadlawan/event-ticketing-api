package com.junaldadlawan.event_ticketing_api.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;

public class ValidBirthDateValidator implements ConstraintValidator<ValidBirthDate, LocalDate> {

    static final int MAX_AGE_YEARS = 120;

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        LocalDate today = LocalDate.now();
        return value.isBefore(today) && !value.isBefore(today.minusYears(MAX_AGE_YEARS));
    }
}
