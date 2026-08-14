package com.cloudforge.core.manager.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a CloudForge Manager REST controller method as requiring a resolved caller identity, but
 * no specific {@link RequiresPolicy policy} check beyond being authenticated — self-service
 * endpoints where the caller acts on their own account/resources use this (e.g. personal access
 * token management: a caller can always list/rotate/revoke their own tokens without needing a
 * named permission).
 *
 * <p>Enforced by {@code AuthorizationInterceptor} against
 * {@code com.cloudforgeci.manager.web.AccessGuard#requireCaller}. See {@link RequiresAccess} for
 * the "every method must carry exactly one of these four annotations" contract.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresCaller {
}
