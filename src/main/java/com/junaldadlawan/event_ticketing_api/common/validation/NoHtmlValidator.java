package com.junaldadlawan.event_ticketing_api.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NoHtmlValidator implements ConstraintValidator<NoHtml, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || (value.indexOf('<') < 0 && value.indexOf('>') < 0);
    }
}
