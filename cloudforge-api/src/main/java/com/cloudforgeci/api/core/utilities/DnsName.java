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
@Constraint(validatedBy = DnsName.Validator.class)
@Target({ElementType.FIELD}) @Retention(RetentionPolicy.RUNTIME)
public @interface DnsName {
    String message() default "invalid DNS name";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    class Validator implements ConstraintValidator<DnsName, String> {
        private static final String LABEL_RX = "^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$";
        public boolean isValid(String v, ConstraintValidatorContext c){
            if (v == null || v.isBlank()) return true;
            if (v.length() > 253) return false;
            for (String part : v.split("\\.")) {
                if (part.isEmpty()) return false;
                if (!part.matches(LABEL_RX)) return false;
            }
            return true;
        }


    }
}