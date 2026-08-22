package com.cloudforge.core.manager.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a CloudForge Manager REST controller method as requiring the caller hold a privileged
 * role ({@code admin}/{@code manager}) or the {@code operations:run} policy — the same bar
 * destructive/high-blast-radius operations have always used, distinct from a single named
 * {@link RequiresPolicy policy}.
 *
 * <p>Enforced by {@code AuthorizationInterceptor} against
 * {@code com.cloudforgeci.manager.web.AccessGuard#requireCaller} followed by
 * {@code com.cloudforgeci.manager.auth.AuthService#requireAdminCaller(Caller)}. See
 * {@link RequiresAccess} for the "every method must carry exactly one of these four annotations"
 * contract.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresAdmin {
}
