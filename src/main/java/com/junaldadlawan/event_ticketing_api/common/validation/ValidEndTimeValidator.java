package com.junaldadlawan.event_ticketing_api.common.validation;

import com.junaldadlawan.event_ticketing_api.event.dto.EventRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidEndTimeValidator implements ConstraintValidator<ValidEndTime, EventRequest> {

    @Override
    public boolean isValid(EventRequest request, ConstraintValidatorContext context) {
        if(request == null || request.startAt() == null || request.endAt() == null) {
            return false;
        }
        return !request.endAt().isBefore(request.startAt());
    }
}
