package com.cloudforgeci.api.core.utilities;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = DnsLabel.Validator.class)
@Target({ElementType.FIELD}) @Retention(RetentionPolicy.RUNTIME)
public @interface DnsLabel {
    String message() default "invalid DNS label";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    class Validator implements ConstraintValidator<DnsLabel, String> {
        private static final String RX = "^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$";
        public boolean isValid(String v, ConstraintValidatorContext c){
            return v == null || v.isBlank() || v.matches(RX);
        }
    }
}