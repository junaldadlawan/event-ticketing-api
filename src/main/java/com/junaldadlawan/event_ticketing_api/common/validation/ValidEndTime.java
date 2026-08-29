package com.junaldadlawan.event_ticketing_api.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidEndTimeValidator.class)
public @interface ValidEndTime {
    String message() default "End time cannot be earlier than start time";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
