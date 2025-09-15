package com.cloudforgeci.api.core.utilities;

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
        public boolean isValid(DeploymentContext c, ConstraintValidatorContext ctx) {
            if (c == null) return true;

            // 1) CFN+ACM region rule (if using CloudFront and an ARN is provided)
  /*          if ("cloudfront".equals(""){//c.frontDoor()) && c.acmArn() != null && !c.acmArn().isBlank()) {
                String[] parts = c.acmArn().split(":");
                if (parts.length < 4 || !"us-east-1".equals(parts[3])) {
                    ctx.disableDefaultConstraintViolation();
                    ctx.buildConstraintViolationWithTemplate("CloudFront certificates must be in us-east-1")
                            .addPropertyNode("acmArn").addConstraintViolation();
                    return false;
                }
            }*/

            // 2) If domain present, hostedZoneId should also be present (Route 53 path)
            if (c.domain() != null && !c.domain().isBlank()) {
  /*              if (c.hostedZoneId() == null || c.hostedZoneId().isBlank()) {
                    ctx.disableDefaultConstraintViolation();
                    ctx.buildConstraintViolationWithTemplate("hostedZoneId required when domain is set")
                            .addPropertyNode("hostedZoneId").addConstraintViolation();*/
                    return false;
 //               }
            }

            // 3) Runtime vs compute coherence
//            if ("jenkins-fargate".equals(c.runtime()) && !"fargate".equals(c.compute())) return fail(ctx,"compute must be 'fargate' for jenkins-fargate","compute");
//            if ("jenkins-ec2".equals(c.runtime())     && !"ec2".equals(c.compute()))       return fail(ctx,"compute must be 'ec2' for jenkins-ec2","compute");

            return true;
        }
        private boolean fail(ConstraintValidatorContext ctx, String msg, String prop){
            ctx.disableDefaultConstraintViolation();
            ctx.buildConstraintViolationWithTemplate(msg).addPropertyNode(prop).addConstraintViolation();
            return false;
        }
    }
}
