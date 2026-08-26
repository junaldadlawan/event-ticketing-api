package com.junaldadlawan.event_ticketing_api.common.validation;

import jakarta.validation.Constraint;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidEndTimeValidator.class)
public @interface ValidEndTime {
    String message() default "End time cannot be earlier than start time";
    Class<?>[] groups() default {};
}
