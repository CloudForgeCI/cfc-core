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
@Constraint(validatedBy = Arn.Validator.class)
@Target({ElementType.FIELD}) @Retention(RetentionPolicy.RUNTIME)
public @interface Arn {
    String message() default "invalid ARN";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    boolean optional() default false;
    class Validator implements ConstraintValidator<Arn, String> {
        private boolean optional;
        // Basic arn:partition:service:region:account:resource
        private static final String RX = "^arn:aws[a-zA-Z-]*:[a-z0-9-]+:[a-z0-9-]*:\\d{12}:.+";
        public void initialize(Arn a){ optional = a.optional(); }
        public boolean isValid(String v, ConstraintValidatorContext c){
            if (v == null || v.isBlank()) return optional;
            return v.matches(RX);
        }
    }
}