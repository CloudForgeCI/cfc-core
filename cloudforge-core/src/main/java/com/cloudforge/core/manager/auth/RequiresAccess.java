package com.cloudforge.core.manager.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a CloudForge Manager REST controller method as requiring only that the caller pass
 * Manager's baseline access check — no specific {@link RequiresPolicy policy} and no resolved
 * {@link RequiresCaller caller identity} beyond that. Read-only endpoints use this: the request
 * must be authenticated (session cookie, ALB/application OIDC, or a personal access token), but
 * the method body has no need for a {@code Caller} value and enforces nothing beyond that.
 *
 * <p>Enforced by {@code AuthorizationInterceptor} against
 * {@code com.cloudforgeci.manager.web.AccessGuard#requireAccess}. Every method on a Manager
 * {@code @RestController} must carry exactly one of {@link RequiresAccess}, {@link RequiresCaller},
 * {@link RequiresPolicy}, or {@link RequiresAdmin} — enforced at build time by
 * {@code ControllerAnnotationCoverageTest} so a forgotten annotation fails the build instead of
 * silently leaving an endpoint unguarded.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresAccess {
}
