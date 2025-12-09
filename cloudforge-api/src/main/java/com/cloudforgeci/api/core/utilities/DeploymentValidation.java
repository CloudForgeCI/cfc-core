package com.cloudforge.core.utilities;

import com.cloudforgeci.api.core.DeploymentContext;
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
@Constraint(validatedBy = DeploymentValidation.Validator.class)
@Target({ElementType.TYPE}) @Retention(RetentionPolicy.RUNTIME)
public @interface DeploymentValidation {
    String message() default "inconsistent CFC configuration";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    class Validator implements ConstraintValidator<DeploymentValidation, DeploymentContext> {
        @Override
        public boolean isValid(DeploymentContext c, ConstraintValidatorContext ctx) {
            if (c == null) return true;

            // Validation rules are currently disabled for CloudForge 3.0 plugin architecture
            // Domain and SSL validation is now handled by DomainFactory and CertificateFactory
            // Runtime vs compute coherence is now handled by ApplicationFactory and deployment context

            return true;
        }
        private boolean fail(ConstraintValidatorContext ctx, String msg, String prop) {
            ctx.disableDefaultConstraintViolation();
            ctx.buildConstraintViolationWithTemplate(msg).addPropertyNode(prop).addConstraintViolation();
            return false;
        }
    }
}
