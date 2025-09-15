package com.cloudforgeci.api.core.utilities;

import jakarta.validation.*;
import java.lang.annotation.*;
import java.util.*;

@Documented @Constraint(validatedBy = OneOf.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER}) @Retention(RetentionPolicy.RUNTIME)
public @interface OneOf {
    String message() default "must be one of: {value}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    String[] value();
    class Validator implements ConstraintValidator<OneOf, String> {
        private Set<String> allowed;
        public void initialize(OneOf a){ allowed = Set.of(a.value()); }
        public boolean isValid(String v, ConstraintValidatorContext c){
            return v == null || allowed.contains(v);
        }
    }
}
